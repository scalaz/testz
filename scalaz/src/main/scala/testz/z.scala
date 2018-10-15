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

import resource.{Bracket, RSection, RTest}
import runner.TestOutput

import scalaz._, Scalaz._
import scalaz.concurrent.Task

object z {
  implicit val equalResult: Equal[Result] =
    Equal.equal(_ eq _)

  implicit val monoidResult: Monoid[Result] = new Monoid[Result] {
    def zero: Result = Succeed()
    def append(f: Result, s: => Result): Result = Result.combine(f, s)
  }

  object TaskHarness {

    type Uses[R] = (R, List[String]) => Task[TestOutput]

    def rTest(
      printer: (Result, List[String]) => Unit
    ): RTest[Task[Result], Uses] = new RTest[Task[Result], Uses] {
      def apply[R](name: String)(assertion: R => Task[Result]): Uses[R] =
        (r, sc) => assertion(r).attempt.map {
          case \/-(es) =>
            new TestOutput(failed = (es == Fail()), () => printer(es, name :: sc))
          case -\/(_) =>
            new TestOutput(failed = true, () => printer(Fail(), name :: sc))
        }
    }

    def test(
      printer: (Result, List[String]) => Unit
    ): Test[Task[Result], Uses[Unit]] =
      rTest(printer).toTest[Unit]

    val rSection: RSection[Uses] = new RSection[Uses] {
      def named[R](name: String)(test1: Uses[R], tests: Uses[R]*): Uses[R] = {
        (r, sc) =>
          val newScope = name :: sc
          test1(r, newScope).flatMap(o1 =>
            tests.toList.traverse(_(r, newScope)).map(os => TestOutput.combineAll1(o1, os: _*))
          )
      }

      def apply[R](test1: Uses[R], tests: Uses[R]*): Uses[R] = {
        (r, sc) =>
          test1(r, sc).flatMap(o1 =>
            tests.toList.traverse(_(r, sc)).map(os => TestOutput.combineAll1(o1, os: _*))
          )
      }
    }

    val section: Section[Uses[Unit]] = rSection.toSection[Unit]

    val bracket: Bracket[Uses, Task] = new Bracket[Uses, Task] {
      def apply[R, I]
        (init: () => Task[I])
        (cleanup: I => Task[Unit])
        (tests: Uses[(I, R)]
      ): Uses[R] = (r, sc) =>
        init().flatMap {
          i => tests((i, r), sc).flatMap(a => cleanup(i).as(a))
        }
    }
  }

  object streaming {

    def exhaustive[F[_]: Applicative, I]
                  (in: List[(() => I)])
                  (testGenerator: I => F[Result])
                  : F[Result] =
      in.foldLeft(Succeed().point[F])((b, a) =>
        Applicative[F].apply2(b, testGenerator(a()))(Result.combine)
      )

    def exhaustiveV[F[_]: Applicative, I]
                  (in: (() => I)*)
                  (testGenerator: I => F[Result]): F[Result] =
      exhaustive(in.toList)(testGenerator)

    def exhaustiveS[F[_]: Applicative, I]
                  (in: I*)
                  (testGenerator: I => F[Result]): F[Result] =
      exhaustiveV(in.map(i => () => i): _*)(testGenerator)

  }

}
