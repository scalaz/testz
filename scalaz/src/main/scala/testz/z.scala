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

import runner._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}

import scalaz._, Scalaz._
import scalaz.concurrent.Task

object z {

  /**
   * run an Unfold using a Fold, taking stack-safety from the `F`.
   */
  final def zapFold[F[_], A, B](unfold: Unfold[F, A], fold: Fold[F, A, B])
                              (implicit F: BindRec[F]): F[B] = {
    def go(foldS: fold.S, unfoldS: unfold.S): F[(fold.S, unfold.S) \/ B] = for {
      nextU <- unfold.step(unfoldS)
      res <- nextU.cata(
        {
          case (newUnfoldS, newA) =>
            fold.step(foldS, newA).map {
              newFoldS => (newFoldS, newUnfoldS).left[B]
            }
        },
        fold.end(foldS).map(_.right[(fold.S, unfold.S)])
      )
    } yield res

    F.tailrecM[(fold.S, unfold.S), B]({ case (fs, us) => go(fs, us) })((fold.start, unfold.start))
  }

  implicit val equalTestResult: Equal[TestResult] = Equal.equal {
    case (f, s) if f eq s => true
    case (Success, Success) => true
    case (f1: Failure, f2: Failure) => f1.failures == f2.failures
    case _ => false // I don't care about comparing Throwable for equality.
  }

  implicit val monoidTestResult: Monoid[TestResult] = new Monoid[TestResult] {
    def zero: TestResult = Success()
    def append(f: TestResult, s: => TestResult): TestResult = TestResult.combine(f, s)
  }

  abstract class TaskSuite extends Suite {
    type Uses[R] = (R, List[String]) => Task[Unit]

    val contravariantUses: Contravariant[Uses] = new Contravariant[Uses] {
      def contramap[A, B](r: Uses[A])(f: B => A): Uses[B] =
        (b, ls) => r(f(b), ls)
    }

    def test[T[_]: Contravariant](harness: Harness[Task, T]): T[Unit]

    def run(ec: ExecutionContext): Future[List[String]] = {
      val buf = new ListBuffer[String]()

      def harness(buf: ListBuffer[String]) = new Harness[Task, λ[R => (R, List[String]) => Task[Unit]]] {

        def apply[R](name: String)(assertion: R => Task[TestResult]): Uses[R] =
          (r, sc) => assertion(r).attempt.map {
            case \/-(es) =>
              buf += Suite.printTest(sc, es)
              ()
            case -\/(e) =>
              buf += Suite.printTest(sc, Failure.error(e))
              ()
          }

        def section[R](name: String)(test1: Uses[R], tests: Uses[R]*): Uses[R] = {
          (r, sc) =>
            val newScope = name :: sc
            test1(r, newScope).flatMap(_ =>
              tests.toList.traverse(_(r, newScope))
            ).void
        }

        def bracket[R, I]
          (init: Task[I])
          (cleanup: I => Task[Unit])
          (tests: Uses[(I, R)]
        ): Uses[R] = (r, sc) =>
          init.flatMap {
            i => tests((i, r), sc).attempt.flatMap(_ => cleanup(i))
          }

        def mapResource[R, RN](test: Uses[R])(f: RN => R): Uses[RN] =
          contravariantUses.contramap(test)(f)

      }

      val prom = Promise[List[String]]
      test[Uses](harness(buf))(contravariantUses)((), Nil).flatMap(_ => Task.delay(buf.result())).unsafePerformAsync {
        case -\/(e) => prom.failure(e)
        case \/-(r) => prom.success(r)
      }
      prom.future

    }
  }

}
