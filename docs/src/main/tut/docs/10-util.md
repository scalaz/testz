---
layout: docs
title: testz-util
---

# {{ page.title }}

testz-util right now contains solely utilities for performance.
They're analogous to `akka.util.FastFuture`, in that most of what
they do is avoid extra thread pool submissions for performance.

All of the utilities are in `testz.futureUtil`.

The rationale for this is:
1. As stated in [Performance](./09-performance.md), testz is mostly not JITted.
Thread pool submissions are made especially expensive by this, though they're
already very expensive.
2. Parallelism grain size is COMPLETELY managed by the user in testz.
The default of "grain for every `map` call" (and basically every other call
on `Future`) is incorrect for testz, and probably for all code.

Obligatory imports:
```tut:silent
import scala.concurrent.{Future, ExecutionContext}
```

`map(fut)(f)(ec)` is like `fut.map(f)(ec)`, but if `fut` is already completed
doesn't submit to the thread pool.

```tut:silent
  def map[A, B](fut: Future[A])(f: A => B)(ec: ExecutionContext): Future[B] = {
    if (fut.isCompleted) Future.successful(f(fut.value.get.get))
    else fut.map(f)(ec)
  }
```

Differences between `orIterator(iterator)`
and `Future.sequence(it.toList).map(_.exists(b => b))`:

  - `orIterator` won't call `next()` until the current
    `Future[Boolean]` has already returned: any effects in the iterator
    are executed in sequence, not in parallel.
  - any runs of synchronous `Future`s are traversed without
    thread pool submissions.
  - there is no list allocated.

```tut:silent
  def orIterator[A](it: Iterator[Future[Boolean]])(ec: ExecutionContext): Future[Boolean] = {
    def outer(acc: Boolean): Future[Boolean] = {
      // synchronous inner loop has to be tail-recursive to be stack-safe.
      @scala.annotation.tailrec
      def inner(acc: Boolean): Future[Boolean] = {
        if (it.hasNext) {
          val ne = it.next
          if (ne.isCompleted) {
            inner(acc || ne.value.get.get)
          } else {
            ne.flatMap(b => outer(acc || b))(ec)
          }
        } else {
          Future.successful(acc)
        }
      }
      inner(acc)
    }
    outer(false)
  }
```

Differences between `consumeIterator(it)`
and `Future.sequence(it.toList).map(_ => ())`:

  - `consumeIterator` won't call `next()` until the current `Future[A]` has
    already returned: any effects in the iterator are executed in sequence,
    not in parallel.
  - any runs of synchronous `Future`s are traversed without thread pool
    submissions,
  - there is no list allocated.

```tut:silent
def consumeIterator[A](it: Iterator[Future[A]])(ec: ExecutionContext): Future[Unit] = {
  // synchronous inner loop has to be tail-recursive to be stack-safe.
  @scala.annotation.tailrec
  def inner(): Future[Unit] = {
    if (it.hasNext) {
      val ne = it.next
      if (ne.isCompleted) {
        inner()
      } else {
        ne.flatMap(_ => consumeIterator(it)(ec))(ec)
      }
    } else {
      Future.unit
    }
  }

    inner()
  }
```

Differences between `collectIterator(it)`
and `Future.sequence(it.toList)`:

  - `collectIterator` won't call `next()` until the current `Future[A]` has
    already returned: any effects in the iterator are executed in sequence,
    not in parallel.
  - any runs of synchronous `Future`s are traversed without thread pool
    submissions,
  - the input list is not allocated.

```tut:silent
def collectIterator[A](it: Iterator[Future[A]])(ec: ExecutionContext): Future[List[A]] = {
  def outer(acc: List[A]): Future[List[A]] = {
    // synchronous inner loop has to be tail-recursive to be stack-safe.
    @scala.annotation.tailrec
    def inner(acc: List[A]): Future[List[A]] = {
      if (it.hasNext) {
        val ne = it.next
        if (ne.isCompleted) {
          val newFun = ne.value.get.get
          inner(newFun :: acc)
        } else {
          ne.flatMap(c =>
            outer(c :: acc)
          )(ec)
        }
      } else {
        Future.successful(acc)
      }
    }
    inner(acc)
  }
  map(outer(Nil))(_.reverse)(ec)
}
```
