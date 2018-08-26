---
layout: docs
title: testz-core
---

# {{ page.title }}

testz-core is the bare bones of testz.

As such, the source should be fully explainable, which I attempt to do below.

Firstly, let's start at `Harness[T]`. `Harness[T]` is the most generally
applicable type in testz; it's used to write most unit test suites, and can be
implemented in quite a few ways. A typical way to use it is by writing methods that
take `Harness[T]` for any `T`.

`Harness[T]` provides hierarchical test registration with two methods for operating
on and creating values of type `T`. Think of `T` values as trees with strings
at the nodes and test functions at the leaves.

It provides a method `test(String)(() => Result): T` which registers
a test under a name, returning a "test group value" of type `T`, where
a test is a `() => Result`; a function with no parameters that computes
a `testz.Result` (described later in this file)

It also provides a method `section(String)(T, T*)` which takes a string
and one or more "named test groups" and returns all of them wrapped in a
new group, labelled with the passed string.

```scala
abstract class Harness[T] {
  def test
    (name: String)
    (assertions: () => Result)
    : T

  def section
    (name: String)
    (test1: T, tests: T*)
    : T
}
```

By slightly extending `Harness` to allow tests to execute effects, we get
`EffectHarness`. For example, a test suite passed a `EffectHarness[Future, T]`
can register tests that have asynchronous results.

```scala
abstract class EffectHarness[F[_], T] {
  def test
    (name: String)
    (assertions: () => F[Result])
    : T

  def section
    (name: String)
    (test1: T, tests: T*)
    : T
}
```

`EffectHarness` can be made into `Harness`, given a way to translate a `Result` to an
`F[Result]`, by calling `toHarness`. Phrased differently: given a "default"
translation of `Result` to `F[Result]`, `toHarness` is a "default" translation of
`EffectHarness[F, T]` to `Harness[T]`.

```scala
  def toHarness[F[_], T](
    self: EffectHarness[F, T]
  )(
    pure: Result => F[Result]
  ): Harness[T] = new Harness[T] {
    def test
      (name: String)
      (assertions: () => Result)
      : T = self.test(name)(() => pure(assertions()))

    def section
      (name: String)
      (test1: T, tests: T*)
      : T = self.section(name)(test1, tests: _*)
  }
```

The type of test results in testz is `Result`.
`Result` is a glorified `Boolean`; either `Fail()` or `Succeed()`.

It's encoded as an ordinary ADT, with an encoding which provides slightly nicer
type inference.

```scala
sealed abstract class Result

final class Fail private() extends Result {
  override def toString(): String = "Fail()"
}

object Fail {
  private val cached = new Fail()

  def apply(): Result = cached
}

final class Succeed private() extends Result {
  override def toString(): String = "Succeed()"
}

object Succeed {
  private val cached = new Succeed()

  def apply(): Result = cached
}

object Result {
  def combine(first: Result, second: Result): Result =
    if (first eq second) first
    else Fail()
}
```

That's all there is to testz-core; the logical next step is
[testz-stdlib](./03-stdlib.md).
