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

import testz.runner._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

abstract class PureSuite extends Suite {
  import PureSuite._

  def test[T[_]](test: Harness[Id, T]): T[Unit]

  def run(ec: ExecutionContext): Future[List[String]] = {
    val buf = new ListBuffer[String]()
    this.test[Uses](PureSuite.makeHarness(buf))((), Nil)
    Future.successful(buf.result())
  }
}

object PureSuite {
  type Uses[R] = (R, List[String]) => Unit

  def makeHarness(buf: ListBuffer[String]): Harness[Id, Uses] =
    new Harness[Id, Uses] {
      def apply[R]
        (name: String)
        (assertions: R => TestResult
      ): Uses[R] =
        (r, scope) => buf += Suite.printTest(scope, assertions(r))

      def bracket[R, I]
        (init: I)
        (cleanup: I => Unit)
        (tests: ((I, R), List[String]) => Unit
      ): Uses[R] =
        // cleanup is never called, because all tests are pure.
        (r, sc) => tests((init, r), sc)

      def section[R]
        (name: String)
        (test1: Uses[R], tests: Uses[R]*
      ): Uses[R] = {
        (r, sc) =>
          val newScope = name :: sc
          test1(r, newScope)
          tests.foreach(_(r, newScope))
      }
    }
}

abstract class ImpureSuite extends Suite {
  import ImpureSuite._

  def test[T[_]](test: Harness[FakeTask, T]): T[Unit]

  def run(ec: ExecutionContext): Future[List[String]] = {
    val buf = new ListBuffer[String]()

    test(makeHarness(buf, ec))((), Nil)
      .map(_ => buf.result())(ec)
  }
}

object ImpureSuite {

  type Uses[R] = (R, List[String]) => Future[Unit]

  type FakeTask[A] = () => Future[A]

  def makeHarness(buf: ListBuffer[String], ec: ExecutionContext): Harness[FakeTask, Uses] =
    new Harness[FakeTask, Uses] {
      def apply[R](name: String)(assertion: R => FakeTask[TestResult]): Uses[R] =
        (r, sc) => assertion(r)().map { es =>
          buf += Suite.printTest(sc, es)
          ()
        }(ec)

      def section[R](name: String)(test1: Uses[R], tests: Uses[R]*): Uses[R] = {
        (r, sc) =>
          val newScope = name :: sc
          test1(r, newScope).flatMap(_ =>
            Future.traverse(tests)(_(r, newScope))(collection.breakOut, ec)
          )(ec).map(_ => ())(ec)
      }

      def saneTransform[A, B](fut: Future[A])(f: Try[A] => Future[B])(ec: ExecutionContext): Future[B] = {
        val prom = Promise[B]
        fut.onComplete {
          t => prom.completeWith(f(t))
        }(ec)
        prom.future
      }

      def bracket[R, I]
        (init: FakeTask[I])
        (cleanup: I => FakeTask[Unit])
        (tests: Uses[(I, R)]
      ): Uses[R] = { (r, sc) =>
        init().flatMap {
          i => saneTransform(tests((i, r), sc))(_ => cleanup(i)())(ec)
        }(ec)
      }

    }
}
