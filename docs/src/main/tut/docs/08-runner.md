---
layout: docs
title: testz-runner
---

# {{ page.title }}

The testz suite runner takes a list of test suites and returns a
`Future[TestResult]`, printing test failures and successes to standard out.

The suites are passed as `() => Future[TestOutput]` in order to delay computation
of the `TestOutput`s.

Prerequisite imports:

```tut:book
import testz._

import scala.concurrent.{ExecutionContext, Future}
```

The output of a "runnable" test group, from a typical `Harness`;
a failure state and a side effect which prints all result info.

The action which prints the result info should *not* compute the
results; the results should have already been computed before `print()`
is executed.

Because computing the `TestOutput` and running the inner `print()` are separate,
it's easy to execute testz suites (and tests) in parallel without interleaving
the results.

```tut:book
final class TestOutput(
  val failed: Boolean,
  val print: () => Unit
)
```

There's an obvious way to combine `TestOutput`s; given `fst: TestOutput` and
`snd: TestOutput`, their combination is a new `TestOutput` which has failed
only if at least one of `fst` or `snd` has failed, and which when "printed"
first calls `fst.print()` then `snd.print()`.

Here's an issue though: every single `section(name)(test1, tests: _*)` call
will call `TestOutput.combine` once for each test in `tests`.

`TestOutput.combine` also builds up a `print()` thunk with a stack depth equal to
the maximum of `fst.print()`'s stack depth and `snd.print()`'s stack depth, plus one.

Putting this information together, we will consume `O(t + s)` stack frames in a test
suite with `t` tests and `s` `section` calls.

This is unacceptable for testz; we try to keep stack usage down to `O(h)`, where
`h` is the maximum height of the test tree.

`combineAll1` provides the necessary fix; passing `n` `TestOutput`s to be combined
to `combineAll1` produces a `TestOutput` where `print()` has a stack depth of the
maximum of each `TestOutput`'s stack depth, plus one.

If `section(name)(test1, tests: _*)` uses `combineAll1` instead of `combine`,
then, the stack usage will be constant regardless of the length of `tests`.

This gets us exactly the asymptotics we need: one stack frame per level of the
test tree.

```tut:book
object TestOutput {
  // The `mappend` operation for the `Monoid` of `TestOutput`s.
  // If either fails, the result fails.
  @inline def combine(fst: TestOutput, snd: TestOutput) =
    new TestOutput(
      fst.failed || snd.failed,
      { () => fst.print(); snd.print() }
    )

  // Combines 1 or more `TestOutput`s, using logarithmic stack depth in the number of
  // tests unlike `combine` which would be linear.
  @inline def combineAll1(output1: TestOutput, outputs: TestOutput*) = {
    val anyFailed = output1.failed || outputs.exists(_.failed)
    new TestOutput(
      anyFailed,
      { () => output1.print(); outputs.foreach(_.print()) }
    )
  }
}
```

Returned by `runner.apply` - after all is said and done,
tests run and output printed, did any fail?
Useful for exit status; I often check `failed` and throw an exception
in `main` if it's `true`.

```tut:book
final class TestResult(val failed: Boolean)
```

The meat of the runner.
Takes a list of `() => Future[TestOutput]` and runs all of them in sequence,
immediately printing out the results of each as they finish.
Then, prints out how long the suites took to run, using the user-supplied printer.
Returns whether any tests failed.

`futureUtil` is explained in the [testz-util docs](./10-util.md);
essentially it provides tools to use `Future` without submitting to an
`ExecutionContext` unless it's necessary.

`akka.util.FastFuture` is apparently similar, but I learned about it after
writing `futureUtil`.

Most of what the runner does is a) time measurement and b) this:
```scala
  val run: Future[Boolean] = futureUtil.orIterator(suites.iterator.map { suite =>
    futureUtil.map(suite()) { r => r.print(); r.failed }(ec)
  })(ec)
```

That code runs each test suite, then prints out their results, while accumulating
the failure state.

```tut:book
  def apply(suites: List[() => Future[TestOutput]], printer: String => Unit, ec: ExecutionContext): Future[TestResult] = {
    val startTime = System.currentTimeMillis
    val run: Future[Boolean] = futureUtil.orIterator(suites.iterator.map { suite =>
      futureUtil.map(suite()) { r => r.print(); r.failed }(ec)
    })(ec)
    futureUtil.map(run) { f =>
      val endTime = System.currentTimeMillis
      printer(
        "Testing took " +
        String.valueOf(endTime - startTime) +
        "ms.\n"
      )
      new TestResult(f)
    }(ec)
  }
```

Cached for performance.

```tut:book
val newlineSingleton =
  "\n" :: Nil
```

```tut:book
/**
  * These four functions are just utility methods for users to write fast
  * test result printers.
  */
@scala.annotation.tailrec
def printStrs(strs: List[String], output: String => Unit): Unit = strs match {
  case x :: xs => output(x); printStrs(xs, output)
  case _ =>
}

@scala.annotation.tailrec
def printStrss(strs: List[List[String]], output: List[String] => Unit): Unit = strs match {
  case xs: ::[List[String]] =>
    val head = xs.head
    if (head.nonEmpty) {
      output(head)
      output(newlineSingleton)
    }
    printStrss(xs.tail, output)
  case _ =>
}

def intersperse(strs: ::[String], delim: String): ::[String] = {
  if (strs.tail eq Nil) {
    strs
  } else {
    var newList: List[String] = Nil
    var cursor: List[String] = strs
    while (cursor ne Nil) {
      newList = cursor.head :: newList
      val tl = cursor.tail
      if (tl ne Nil) {
        newList = delim :: newList
      }
      cursor = cursor.tail
    }
    newList.asInstanceOf[::[String]]
  }
}

// Note that tests which succeed never have results printed
// (if you use this function)
def printTest(scope: List[String], out: Result): List[String] = out match {
  case _: Succeed => Nil
  case _          => intersperse(new ::("failed\n", scope), "->")
}

```

## Internals

The runner avoids interleaving test output because `Suite`
doesn't print to standard out itself; that's the runner's job.
It prints output given by the `Suite`.

As well, `() => Suite`s are run fully sequentially, and if a
`Suite` runs synchronously, the passed `ExecutionContext` is not
used.
