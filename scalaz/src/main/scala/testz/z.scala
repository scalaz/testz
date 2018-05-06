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

import java.util.concurrent.atomic.AtomicReference
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

  object assertEqualNoShow {
    def apply[E: Equal](fst: E, snd: E): List[TestError] =
      if (fst === snd) Nil else Failure("not equal, when should be") :: Nil
  }

  object assertNotEqualNoShow {
    def apply[E: Equal](fst: E, snd: E): List[TestError] =
      if (fst === snd) Failure("equal, when shouldn't be") :: Nil else Nil
  }

  implicit val equalTestError: Equal[TestError] = Equal.equalA

  abstract class TaskSuite extends Suite {
    def test[T](test: Test[Task, Task[T]]): Task[T]
    def run(implicit ec: ExecutionContext): Future[List[String]] = {
      type TestEff[A] = ReaderT[Task, List[String], A]
      val buf = Task.delay(new AtomicReference[List[String]](Nil))

      def add(buf: AtomicReference[List[String]], str: String): Unit = {
        val _ = buf.updateAndGet(xs => str :: xs)
      }

      def test(buf: AtomicReference[List[String]]): Test[Task, Task[TestEff[Unit]]] =
        new Test[Task, Task[TestEff[Unit]]] {
          def apply
            (name: String)
            (assertion: Task[List[TestError]]
            ): Task[TestEff[Unit]] = {
            // todo: catch exceptions
            Task.now(ReaderT((ls: List[String]) =>
              assertion.attempt.map { r =>
                val out: List[TestError] = r match {
                  case \/-(result) =>
                    result
                  case -\/(exception) =>
                    List(ExceptionThrown(exception))
                }
                add(buf, Suite.printTest(name :: ls, out))
              }
            ))
          }
          def section
            (name: String)
            (
              test1: Task[TestEff[Unit]],
              tests: Task[TestEff[Unit]]*
            ): Task[TestEff[Unit]] = {
            NonEmptyList(test1, tests: _*).foldLeft1 {
              // execute sections,
              ^(_, _) {
                // execute tests
                ^(_, _) { (_, _) => () }
              }
            }
          }
      }
      val pr = Promise[List[String]]()
      buf.flatMap { b =>
        this.test[TestEff[Unit]](test(b)).flatMap(_.run(Nil)) >>
          Task.delay(b.get)
      }.unsafePerformAsync { f => pr.complete(f.fold(scala.util.Failure(_), scala.util.Success(_))) }
      pr.future
    }
  }
}
