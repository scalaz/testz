---
layout: docs
title: testz-resource
---

# {{ page.title }}

Inside testz-resource is a couple of models for allocating and cleaning up resources
that are shared between tests.

These come in the form of a couple of harness types; firstly, `ResourceHarness`.

```scala
abstract class ResourceHarness[T[_]] { self =>
  def test[R](name: String)(assertions: R => Result): T[R]
  def namedSection[R](name: String)(test1: T[R], tests: T[R]*): T[R]
  def section[R](test1: T[R], tests: T[R]*): T[R]
  def allocate[R, I]
    (init: () => I)
    (tests: T[(I, R)]): T[R]
}
```

Noting the differences between `ResourceHarness[T[_]]` and `Harness[T]`:

  - `ResourceHarness` requires a type constructor, unlike `Harness` which takes a fully-saturated type
     parameter.
  - `ResourceHarness` has a `test` method which takes an `R => Result`, as opposed to
    `Harness.test` which takes an `() => Result`. This expresses that the test *depends* on
    a resource of type `R`.
  - `ResourceHarness.test` also returns a `T[R]`, not a `T`. This is because the type
    `T[R]` represents a group of tests which *depend on* a resource of type `R`.
  - `ResourceHarness.namedSection` and `ResourceHarness.section` are both polymorphic over `R`
    and take in `T[R]`'s and return a `T[R]`, whereas `Harness.namedSection` and `Harness.section`
    take in and return `T`'s.
  - `allocate` is a new method on `ResourceHarness`; it lets you discharge one resource obligation
    by providing a way to allocate that resource. That's why it takes in a `T[(I, R)]` and `() => I`,
    and returns a `T[R]`, which is a test group.

`T[_]` is likely to be a contravariant functor, because what it represents is a *consumer*
of `R` values.

To type a group of tests with all resources accounted for -
including the case of no resources needed at all - the type `T[Unit]` suffices.

`allocate` can be used to "fill in" one of the resources required by
a group of tests, by describing how the resource is allocated.

Resources should be allocated immediately before all tests within an `allocate` block
execute. After all tests within an `allocate` block execute, the resource being
allocated should no longer be referenced.

The intent behind `allocate` is to keep test data short-lived; data which
is live for a long time is promoted to the old generation, making garbage collection
much more expensive.

Here's an example:

```tut:silent
import testz._

object TestsWithResources {
  def tests[T[_]](harness: ResourceHarness[T]): T[Unit] = {
    import harness._
    section(
      allocate(() => List(1, 2, 3, 4))(
        test("the list should be ascending") {
          case (list, _) =>
            assert(list == list.sorted)
        }
      ),
      test("doesn't see the list, it's not referenced by the time the test executes") {
        case () =>
          assert(true)
      }
    )
  }
}
```

This code makes sure to be careful about resources; though I'm only using a `List`
here, large buffers and the like are useful with `ResourceHarness`, to avoid keeping
too much data in memory and tightly control references.

You may (rightly) ask: what about resources which require cleanup?
Well, if you want to use a resource that needs cleaning up, you definitely want more
than `R => Result`; you want `R => F[Result]` for some `F[_]`. Hence,
`EffectResourceHarness`:

```scala
trait EffectResourceHarness[F[_], T[_]] { self =>
  def test[R]
    (name: String)
    (assertions: R => F[Result]): T[R]

  def namedSection[R]
    (name: String)
    (test1: T[R], tests: T[R]*
  ): T[R]

  def section[R]
    (test1: T[R], tests: T[R]*
  ): T[R]

  def bracket[R, I]
    (init: () => F[I])
    (cleanup: I => F[Unit])
    (tests: T[(I, R)]
  ): T[R]
}
```

The difference between `ResourceHarness.allocate` and `EffectResourceHarness.bracket`
is that `bracket` takes some cleanup action as well, which returns an `F[Unit]`, and
actually allocating the value can execute an effect in `F[_]` as well.

`EffectResourceHarness` has similarly tight guarantees; allocation happens before
the tests in `bracket`, cleanup happens immediately after the tests in `bracket`.

