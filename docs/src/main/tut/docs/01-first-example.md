---
layout: docs
title: First Example
---

# {{ page.title }}

Let’s start with a simple, pure test suite, using the
most basic harness type provided. We'll be using `testz-core`
and `testz-stdlib`, but the actual test code will only use `testz-core`.

Obligatory imports:

```tut:silent
import testz.{Harness, PureHarness, assert}
```

And here's the meat.

```tut:silent
final class MathTests {
  def tests[T](harness: Harness[T]): T = {
    import harness._
    section("math must")(
      test("say 1 + 1 == 2") { () =>
        assert(1 + 1 == 2)
      },
      test("say 2 * 2 == 4") { () =>
        assert(2 * 2 == 4)
      }
    )
  }
}
```

To run this test suite, pass it the `Harness` it wants.
First to do that we have to construct the `Harness`, using
`PureHarness.makeFromPrinter`; `makeFromPrinter` expects
an impure function which can be used to print test results
(`(List[String], Result) => Unit`), and that's what we give it.

It will return a `T`, in this case `PureHarness.Uses[Unit]`,
which is `(Unit, List[String]) => TestOutput`.
`TestOutput` has a method `print()` which you can use to see the output.

```tut:book
val harness: Harness[PureHarness.Uses[Unit]] =
  PureHarness.makeFromPrinter((result, name) =>
    println(s"${name.reverse.mkString("[\"", "\"->\"", "\"]:")} $result")
  )
(new MathTests()).tests(harness)((), Nil).print()
```

I went through a lot there; let's dissect that.

```tut:silent
import testz.{Harness, PureHarness, assert}
```

Here I import `Harness`, the simplest type of test harnesses,
`PureHarness`, an actual test harness implementation,
and the `assert` function.

`PureHarness` is from testz-stdlib, and the others are from testz-core.

Conventionally, test suites are written with an abstract method
that takes some type of harness as a parameter and returns
a value it can only obtain from the harness.

Note: Test registration, intuitively, is not side effectful because
      of this. You can't accidentally register a test
      from inside an assertion; the only tests you register are a single
      value.

I also import `assert` from `testz`; assertions are just values in
testz. `assert` returns either `Success()` if its argument is `True`,
or `Failure.noMessage` otherwise (a failure, with no message).

```scala
final class MathTests {
```

We're making a test suite class called `MathTests`.
We'll be defining our tests inside.

Note: This is intentionally a class and not an object.
      testz encourages you not to use singleton objects as
      test suites. Using objects will prevent fields of
      test suites from being garbage collected during the run.

```scala
def tests[T](harness: Harness[T]): T = {
```

Here we define a method in which we will define our tests.

Note: The type returned by `tests` is abstract,
      and in its parameters only appears in `Harness[T]`.
      This is because the only way to create a `T` is to
      define some tests with that harness. You can write
      your own suite type and give it any signature you
      want; you don't even need to use testz's `Harness`
      test harness types.

```scala
import harness._
```

We also import all of the methods from `harness`;
we'll use `section` and `test`, the test registration
primitives in `Harness` which are included in all harness types.

```scala
section("math must")(
```

Declaring a test section. Takes varargs parameters, of type `T`.
Returns a `T`. The only way other than `section` to get a `T`
(when it's abstract) is `test`.

```scala
test("say 1 + 1 == 2") { () =>
```

And here's a test definition, using `test.apply`.
The first parameter is the name of the test. The parameter in the
second (curried) parameter list is a function `() => TestResult`.

Note: `() =>` is actually needed to avoid computing test registrations
      and tests at the same time. testz doesn't use by-names.
      If you *do* want a `Harness` with by-names or with test registrations
      adjacent to tests, this is something you can alter.

```scala
assert(1 + 1 == 2)
```

Here's the assertion inside.
It'll give you a `Result` which is `Fail()` if the two
arguments aren't equal using `===`, and otherwise `Succeed()`.

The next assertion, all together:

```scala
test("say 2 * 2 == 4") { () =>
  assert(2 * 2 == 4)
}
```


And we're done.
