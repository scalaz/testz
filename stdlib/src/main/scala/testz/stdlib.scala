/*
 * Copyright (c) 2018, Edmund Noble
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package testz

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

// a `Harness` where tests are able to access and allocate
// some kind of resource `R`, and they have no effects.
abstract class PureHarness[T[_]] { self =>
  def test[R](name: String)(assertions: R => Result): T[R]
  def section[R](name: String)(test1: T[R], tests: T[R]*): T[R]
  def mapResource[R, RN](tr: T[R])(f: RN => R): T[RN]
  def allocate[R, I]
    (init: () => I)
    (tests: T[(I, R)]): T[R]
  }

object PureHarness {
  type Uses[R] = (R, List[String]) => () => Unit

  def run(
    harness: PureHarness[Uses],
    suite: ResourceSuite[PureHarness]
  ): () => Unit = {
    suite.tests[Uses](harness)((), Nil)
  }

  def make(
    output: (List[String], Result) => Unit
  ): PureHarness[Uses] =
    new PureHarness[Uses] {
      override def test[R]
        (name: String)
        (assertions: R => Result
      ): Uses[R] =
      // note that `assertions(r)` is *already computed* before we run
      // the `() => Unit`.
        { (r, scope) =>
          val result = assertions(r)
          () => output(name :: scope, result)
        }

      override def section[R]
        (name: String)
        (test1: Uses[R], tests: Uses[R]*
      ): Uses[R] = {
        (r, sc) =>
          val newScope = name :: sc
          val outFirst = test1(r, newScope)
          val ranTests = tests.map(_(r, newScope)).iterator.map(_())
          // semicolon inference fails, can't put `{` on the outside.
          () => {
            outFirst()
            while (ranTests.hasNext) {
              ranTests.next
            }
          }
      }

      override def mapResource[R, RN](test: Uses[R])(f: RN => R): Uses[RN] = {
        (rn, sc) => test(f(rn), sc)
      }

      override def allocate[R, I]
        (init: () => I)
        (tests: ((I, R), List[String]) => () => Unit
      ): Uses[R] =
        (r, sc) => tests((init(), r), sc)
    }

  // most harness types should have something like this,
  // a lot of tests can be written in terms of a `Harness` and
  // used in any other context.
  def toHarness[T[_], R](self: PureHarness[T]): Harness[T[R]] =
    new Harness[T[R]] {
      def test
        (name: String)
        (assertions: () => Result)
        : T[R] =
          self.test[R](name)(_ => assertions())

      def section
        (name: String)
        (test1: T[R], tests: T[R]*)
        : T[R] =
          self.section(name)(test1, tests: _*)
    }

}

trait ImpureHarness[T[_]] { self =>
  def test[R]
    (name: String)
    (assertions: R => Future[Result]): T[R]

  def section[R]
    (name: String)
    (test1: T[R], tests: T[R]*
  ): T[R]

  def mapResource[R, RN](test: T[R])(f: RN => R): T[RN]

  def bracket[R, I]
    (init: () => Future[I])
    (cleanup: I => Future[Unit])
    (tests: T[(I, R)]
  ): T[R]

 def toHarness[R]: Harness[T[R]] = new Harness[T[R]]{
    def test
      (name: String)
      (assertions: () => Result)
      : T[R] =
        self.test[R](name)(_ => Future.successful(assertions()))

    def section
      (name: String)
      (test1: T[R], tests: T[R]*)
      : T[R] =
        self.section(name)(test1, tests: _*)
  }

}

object ImpureHarness {

  type Uses[R] = (R, List[String]) => Future[() => Unit]

  def run(
    harness: ImpureHarness[Uses],
    suite: ResourceSuite[ImpureHarness]
  ): Future[() => Unit] = {
    suite.tests(harness)((), Nil)
  }

  def make(
    ec: ExecutionContext,
    outputTest: (List[String], Result) => Unit
  ): ImpureHarness[Uses] =
    new ImpureHarness[Uses] {
      def test[R](name: String)(assertion: R => Future[Result]): Uses[R] =
        (r, sc) => assertion(r).map { es =>
          () => outputTest(name :: sc, es)
        }(ec)

      def section[R](name: String)(test1: Uses[R], tests: Uses[R]*): Uses[R] = {
        (r, sc) =>
          val newScope = name :: sc
          test1(r, newScope).flatMap { p1 =>
            futureUtil.collectIterator(tests.iterator.map(_(r, newScope)))(ec).map { ps =>
              () => { p1(); ps();}
            }(ec)
          }(ec)
      }

      def mapResource[R, RN](test: Uses[R])(f: RN => R): Uses[RN] = {
        (rn, sc) => test(f(rn), sc)
      }

      // a version of `transform` that lets you block on whatever you get
      // from the inner `Try[A]`.
      private def saneTransform[A, B](fut: Future[A])(f: Try[A] => Future[B])(ec: ExecutionContext): Future[B] = {
        val prom = Promise[B]
        fut.onComplete {
          t => prom.completeWith(f(t))
        }(ec)
        prom.future
      }

      private def fromTry[A](t: Try[A]): Future[A] = {
        if (t.isInstanceOf[scala.util.Failure[A]]) Future.failed(t.asInstanceOf[scala.util.Failure[A]].exception)
        else Future.successful(t.asInstanceOf[scala.util.Success[A]].value)
      }

      def bracket[R, I]
        (init: () => Future[I])
        (cleanup: I => Future[Unit])
        (tests: Uses[(I, R)]
      ): Uses[R] = { (r, sc) =>
        init().flatMap {
          i => saneTransform(tests((i, r), sc))(r => cleanup(i).flatMap(_ => fromTry(r))(ec))(ec)
        }(ec)
      }
    }
}
