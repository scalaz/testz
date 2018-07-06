---
layout: docs
title: A First Example
---

# {{ page.title }}

So, let’s start with a simple version of a pure test suite, using the
most basic suite type provided. We'll be using `testz-core`,
`testz-runner`, and `testz-stdlib`.

It's provided in `testz.stdlib.PureSuite`.

```tut:silent
import testz.{Id, Harness, PureSuite, assert}
import scala.concurrent.ExecutionContext.global

final class MathTests extends PureSuite {
  def tests[T[_]](test: PureHarness[T]): T[Unit] = {
    test.section("math must")(
      test("say 1 + 1 == 2") { _ =>
        assert(1 + 1 == 2)
      }
    )
  }
}
```

To run this type of test suite using the default testz runner, just
call `.run` with an `ExecutionContext`. The global one is fine,
usually.

```tut:book
new MathTests().run(global)
```

I went through a lot there; let's dissect that.

```tut:silent
import testz.{PureSuite, Harness, assert}
import scala.concurrent.ExecutionContext.global
```

Here I import `Harness[F[_], T[_]]`, the type of test harnesses in testz.
Conventionally, test suites are written to extend a test suite class
with an abstract method that takes a `Harness` as a parameter.

I also import `assert` from `testz`; assertions are just values in testz.
`assert` returns either `Success()` if its argument is `True`, or
`Failure(Nil)` otherwise (a failure, with no message).

`PureSuite` is the test suite class I'm using. The type of test
harness it uses is a `Harness[Id, T]` for all `T[_]`, and it returns a
`T[Unit]` at the end. The idea behind this is for the test code to be unaware
of what the type `T[A]` will be. So the only way it can return a `T[Unit]` is
to use the test harness.

```scala
final class MathTests extends PureSuite {
```

We're making a test suite class called `MathTests` extending the suite
type `PureSuite`; `MathTests` is what will show up in the output while
running the suite. This is pretty normal for a Scala test framework,
but note that this is a class and not an object. testz encourages you
not to use singleton objects as test suites. Using objects will prevent
fields of the test suites from being reachable for garbage collection
while tests run. Instead, use a class to keep your working set small
during the run.

```scala
def tests[T[_]](test: PureHarness[T]): T[Unit] = {
```

Here we define a method from `PureSuite` which we will use to define our tests.
Note that the type of the `test` method *entirely depends* on the suite
type. Any suite type could have give it any signature. You can write
your own suite type and give it any signature you want; you don't even
need to use testz's `Test` test harness type.

```scala
test.section("math must")(
```

Declaring a test section. Takes varargs parameters, of type `T`.
Returns a `T`. The only way other than `test.section` to get a `T`
(when it's abstract) is `test.apply`.

```scala
test("say 1 + 1 == 2") { () =>
```

And here's a test definition, using `test.apply`.
The first parameter is the name of the test. The parameter in the
second (curried) parameter list is a function `() => TestResult`.

```scala
assert(1 + 1 === 2)
```

Here's the only assertion we've got.
It'll give you a `TestResult` which contains an error if the two
arguments aren't equal using `===`; in this case, that'll be a `Success`.

And we're done.
