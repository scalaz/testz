---
layout: docs
title: testz-core
---

# {{ page.title }}

testz-core is the bare bones of testz.

As such, the source should be fully explainable, which I attempt to do below.

The type of test results in testz is `Result`.
`Result` is a glorified `Boolean`; either `Fail()` or `Succeed()`.

It's encoded as an ordinary ADT, with an encoding which provides slightly nicer
type inference.

A delayed computation of type `Result` (e.g. `() => Result`) is often called
a "test function".

```tut:silent
sealed abstract class Result

// this constructor is `private` in the real code, but in the documentation
// every statement is separate in the REPL so `Fail` is not the companion of `Fail`
// and the constructor is *never* accessible.
final class Fail() extends Result {
  override def toString(): String = "Fail()"
  override def equals(other: Any): Boolean = other.asInstanceOf[AnyRef] eq this
}

object Fail {
  private val cached = new Fail()

  def apply(): Result = cached
}

// this constructor is `private` in the real code, but in the documentation
// every statement is separate in the REPL so `Succeed` is not the companion of `Succeed`
// and the constructor is *never* accessible.
final class Succeed() extends Result {
  override def toString(): String = "Succeed()"
  override def equals(other: Any): Boolean = other.asInstanceOf[AnyRef] eq this
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

Let's move on to `Harness[T]`. `Harness[T]` is used to write most unit test suites, and can be
implemented in quite a few ways for different featueres. A typical way to use it is by writing
methods that take `Harness[T]` for any `T`.

`Harness[T]` provides hierarchical test registration with methods for
operating on and creating values of type `T`. Think of `T` values as trees
with (optionally) strings at the nodes and test functions at the leaves.

It provides a method `test(String)(() => Result): T` which registers
a test under a name, returning a "test group value" of type `T`, where
a test is a `() => Result`; a function with no parameters that computes
a `testz.Result` (described later in this file)

It also provides a method `section(T, T*)` which takes one or more "test groups"
and returns all of them wrapped in a new unnamed group. To name the new test group,
use the `namedSection(String)(T, T*)` method.

```tut:silent
abstract class Harness[T] {
  def test(name: String)(assertions: () => Result): T

  def namedSection(name: String)(test1: T, tests: T*): T

  def section(test1: T, tests: T*): T
}
```

By slightly extending `Harness` to allow tests to execute effects, we get
`EffectHarness`. For example, a test suite passed a `EffectHarness[Future, T]`
can register tests that have asynchronous results.

```tut:silent
abstract class EffectHarness[F[_], T] {
  def test(name: String)(assertions: () => F[Result]): T

  def namedSection(name: String)(test1: T, tests: T*): T

  def section(test1: T, tests: T*): T
}
```

`EffectHarness` can be made into `Harness` by calling `EffectHarness.toHarness`; given a way to translate
a `Result` to an `F[Result]`, you can create an `EffectHarness[F, T]` from a `Harness[T]`.
Phrased differently: given a "default" translation of `Result` to `F[Result]`, `toHarness` is a "default"
translation of `EffectHarness[F, T]` to `Harness[T]`.

```tut:silent
object EffectHarness {
  def toHarness[F[_], T](
    self: EffectHarness[F, T]
  )(
    pure: Result => F[Result]
  ): Harness[T] = new Harness[T] {
    def test
      (name: String)
      (assertions: () => Result)
      : T = self.test(name)(() => pure(assertions()))

    def namedSection
      (name: String)
      (test1: T, tests: T*)
      : T = self.namedSection(name)(test1, tests: _*)

    def section
      (test1: T, tests: T*)
      : T = self.section(test1, tests: _*)
  }
}
```

That's all there is to testz-core; the next step in the test-writing side of testz is
[testz-resource](./03-resource.html).

However, if you're more interested in testz's implementation, you can skip to
[testz-stdlib](./04-stdlib.html).
