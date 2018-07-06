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

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

// a `Harness` where tests are able to access and allocate
// some kind of resource `R`, and they have no effects.
abstract class PureHarness[T[_]] { self =>
  def test[R](name: String)(assertions: R => TestResult): T[R]
  def section[R](name: String)(test1: T[R], tests: T[R]*): T[R]
  def mapResource[R, RN](tr: T[R])(f: RN => R): T[RN]
  def allocate[R, I]
    (init: () => I)
    (tests: T[(I, R)]): T[R]

 def toHarness[R]: Harness[T[R]] = new Harness[T[R]]{
    def test
      (name: String)
      (assertions: () => TestResult)
      : T[R] =
        self.test[R](name)(_ => assertions())

    def section
      (name: String)
      (test1: T[R], tests: T[R]*)
      : T[R] =
        self.section(name)(test1, tests: _*)
  }

}

abstract class PureSuite extends Suite {
  import PureSuite._

  def test[T[_]](harness: PureHarness[T]): T[Unit]

  def harness(out: ListBuffer[String]): PureHarness[Uses] =
    PureSuite.harness(out)

  def run(ec: ExecutionContext): Future[List[String]] = {
    val buf = new ListBuffer[String]()
    this.test[Uses](harness(buf))((), Nil)
    Future.successful(buf.result())
  }
}

object PureSuite {
  type Uses[R] = (R, List[String]) => Unit

  def harness(buf: ListBuffer[String]): PureHarness[Uses] =
    new PureHarness[Uses] {
      override def test[R]
        (name: String)
        (assertions: R => TestResult
      ): Uses[R] =
        (r, scope) => buf += Suite.printTest(name :: scope, assertions(r))

      override def section[R]
        (name: String)
        (test1: Uses[R], tests: Uses[R]*
      ): Uses[R] = {
        (r, sc) =>
          val newScope = name :: sc
          test1(r, newScope)
          tests.foreach(_(r, newScope))
      }

      override def mapResource[R, RN](test: Uses[R])(f: RN => R): Uses[RN] = {
        (rn, sc) => test(f(rn), sc)
      }

      override def allocate[R, I]
        (init: () => I)
        (tests: ((I, R), List[String]) => Unit
      ): Uses[R] =
        (r, sc) => tests((init(), r), sc)
    }

}

trait ImpureHarness[T[_]] { self =>
  def test[R]
    (name: String)
    (assertions: R => Future[TestResult]): T[R]

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
      (assertions: () => TestResult)
      : T[R] =
        self.test[R](name)(_ => Future.successful(assertions()))

    def section
      (name: String)
      (test1: T[R], tests: T[R]*)
      : T[R] =
        self.section(name)(test1, tests: _*)
  }

}

abstract class ImpureSuite extends Suite {
  import ImpureSuite._

  def test[T[_]](harness: ImpureHarness[T]): T[Unit]

  def harness(out: ListBuffer[String], ec: ExecutionContext): ImpureHarness[Uses] =
    ImpureSuite.harness(out, ec)

  def run(ec: ExecutionContext): Future[List[String]] = {
    val buf = new ListBuffer[String]()

    test(harness(buf, ec))((), Nil)
      .map(_ => buf.result())(ec)
  }
}

object ImpureSuite {

  type Uses[R] = (R, List[String]) => Future[Unit]

  def harness(buf: ListBuffer[String], ec: ExecutionContext): ImpureHarness[Uses] =
    new ImpureHarness[Uses] {
      def test[R](name: String)(assertion: R => Future[TestResult]): Uses[R] =
        (r, sc) => assertion(r).map { es =>
          buf += Suite.printTest(name :: sc, es)
          ()
        }(ec)

      def section[R](name: String)(test1: Uses[R], tests: Uses[R]*): Uses[R] = {
        (r, sc) =>
          val newScope = name :: sc
          test1(r, newScope).flatMap(_ =>
            Future.traverse(tests)(_(r, newScope))(collection.breakOut, ec)
          )(ec).map(_ => ())(ec)
      }

      def mapResource[R, RN](test: Uses[R])(f: RN => R): Uses[RN] = {
        (rn, sc) => test(f(rn), sc)
      }

      private def saneTransform[A, B](fut: Future[A])(f: Try[A] => Future[B])(ec: ExecutionContext): Future[B] = {
        val prom = Promise[B]
        fut.onComplete {
          t => prom.completeWith(f(t))
        }(ec)
        prom.future
      }

      def bracket[R, I]
        (init: () => Future[I])
        (cleanup: I => Future[Unit])
        (tests: Uses[(I, R)]
      ): Uses[R] = { (r, sc) =>
        init().flatMap {
          i => saneTransform(tests((i, r), sc))(_ => cleanup(i))(ec)
        }(ec)
      }
    }
}
