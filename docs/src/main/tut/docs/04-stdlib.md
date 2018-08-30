---
layout: docs
title: testz-stdlib
---

# {{ page.title }}

The `testz-stdlib` module provides basic testz harnesses using nothing more
than the standard library, `testz-core`, `testz-resource`, and `testz-util`.

It provides two harnesses: `PureHarness` and `FutureHarness`. Both are built to be
used with testz-runner, despite there being no dependency on testz-runner.

`PureHarness` is a harness type for tests which return `testz.Result`.

Its `Uses[R]` type alias is the implementation type of `PureHarness`;
a test group depending on a resource `R` in `PureHarness` is an
`(R, List[String]) => TestOutput`; a function which, given the resource
it needs and the current test group name (and all labels attached to it)
produces a `TestOutput`, which describes both how to print the results
of the group and whether any tests failed.

```tut:silent
import testz._
import testz.runner.TestOutput

object PureHarness {
  type Uses[R] = (R, List[String]) => TestOutput

  def makeFromPrinter(
    output: (List[String], Result) => Unit
  ): Harness[Uses[Unit]] =
    ResourceHarness.toHarness(makeFromPrinterR(output))

  def makeFromPrinterR(
    output: (List[String], Result) => Unit
  ): ResourceHarness[Uses] =
    new ResourceHarness[Uses] {
      override def test[R]
        (name: String)
        (assertions: R => Result
      ): Uses[R] =
        // note that `assertions(r)` is *already computed* before the
        // `() => Unit` is run; this is important to separate phases between
        // printing and running tests.
        { (r, scope) =>
          val result = assertions(r)
          new TestOutput(
            result ne Succeed(),
            () => output(name :: scope, result)
          )
        }

      override def section[R]
        (name: String)
        (test1: Uses[R], tests: Uses[R]*
      ): Uses[R] = {
        (r, sc) =>
          val newScope = name :: sc
          val outFirst = test1(r, newScope)
          val outRest = tests.map(_(r, newScope))
          TestOutput.combineAll1(outFirst, outRest: _*)
      }

      override def allocate[R, I]
        (init: () => I)
        (tests: ((I, R), List[String]) => TestOutput
      ): Uses[R] =
        (r, sc) => tests((init(), r), sc)
    }

}
```

`FutureHarness` is a harness type for tests which return `Future[testz.Result]`.

It's a lot more verbose than `PureHarness`, mostly because I'm careful with
`ExecutionContext` and because there are several generic utilities missing from
`Future` that are very useful in implementing the harness in a clear and concise
way.

```tut:silent
import testz._
import testz.runner.TestOutput

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

object FutureHarness {

  type Uses[R] = (R, List[String]) => Future[TestOutput]

  def makeFromPrinterEff(
    output: (List[String], Result) => Unit
  )(
    ec: ExecutionContext
  ): EffectHarness[Future, Uses[Unit]] =
    EffectResourceHarness.toEffectHarness(makeFromPrinterEffR(output)(ec))

  def makeFromPrinterEffR(
    outputTest: (List[String], Result) => Unit,
  )(
    ec: ExecutionContext
  ): EffectResourceHarness[Future, Uses] =
    new EffectResourceHarness[Future, Uses] {
      // note that `assertions(r)` is *already computed* before we run
      // the `() => Unit`.
      def test[R](name: String)(assertions: R => Future[Result]): Uses[R] =
        (r, sc) => assertions(r).map { es =>
          new TestOutput(es ne Succeed(), () => outputTest(name :: sc, es))
        }(ec)

      def section[R](name: String)(test1: Uses[R], tests: Uses[R]*): Uses[R] = {
        (r, sc) =>
          val newScope = name :: sc
          test1(r, newScope).flatMap { p1 =>
            futureUtil.collectIterator(tests.iterator.map(_(r, newScope)))(ec).map { ps =>
              TestOutput.combineAll1(p1, ps: _*)
            }(ec)
          }(ec)
      }

      // a more powerful version of `Future.transform` that lets you block on
      // whatever you make from the inner `Try[A]`, instead of only letting you
      // return a `Try`.
      // relative monad operation (`rflatMap :: f a -> (g a -> f b) -> f b`)
      private def blockingTransform[A, B](fut: Future[A])(f: Try[A] => Future[B])(ec: ExecutionContext): Future[B] = {
        val prom = Promise[B]
        fut.onComplete {
          t => prom.completeWith(f(t))
        }(ec)
        prom.future
      }

      private def fromTry[A](t: Try[A]): Future[A] = {
        if (t.isInstanceOf[scala.util.Failure[A]])
          Future.failed(t.asInstanceOf[scala.util.Failure[A]].exception)
        else
          Future.successful(t.asInstanceOf[scala.util.Success[A]].value)
      }

      def bracket[R, I]
        (init: () => Future[I])
        (cleanup: I => Future[Unit])
        (tests: Uses[(I, R)]
      ): Uses[R] = { (otherResources, sc) =>
        init().flatMap { i =>
          blockingTransform(
            tests((i, otherResources), sc)
          )(result =>
            cleanup(i).flatMap(_ =>
              fromTry(result)
            )(ec)
          )(ec)
        }(ec)
      }
    }
}
```
