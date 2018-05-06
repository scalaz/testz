/*
 * Copyright 2018 Edmund Noble
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
