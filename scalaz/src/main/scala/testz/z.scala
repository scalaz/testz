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

// import scala.concurrent.{ExecutionContext, Future, Promise}

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

  implicit val equalResult: Equal[Result] =
    Equal.equal(_ eq _)

  implicit val monoidResult: Monoid[Result] = new Monoid[Result] {
    def zero: Result = Succeed()
    def append(f: Result, s: => Result): Result = Result.combine(f, s)
  }

  object TaskHarness {

    type Uses[R] = (R, List[String]) => Task[() => Unit]

    def makeFromPrinterEff(
      printer: (List[String], Result) => Unit
    ): EffectHarness[Task, Uses[Unit]] =
      EffectResourceHarness.toEffectHarness(makeFromPrinterEffR(printer))

    def makeFromPrinterEffR(
      printer: (List[String], Result) => Unit
    ): EffectResourceHarness[Task, Uses] = new EffectResourceHarness[Task, Uses] {
      def test[R](name: String)(assertion: R => Task[Result]): Uses[R] =
        (r, sc) => assertion(r).attempt.map {
          case \/-(es) =>
            () => printer(sc, es)
          case -\/(_) =>
            () => printer(sc, Fail())
        }

      def section[R](name: String)(test1: Uses[R], tests: Uses[R]*): Uses[R] = {
        (r, sc) =>
          val newScope = name :: sc
          test1(r, newScope).flatMap(_ =>
            tests.toList.traverse(_(r, newScope))
          ).map(ls => () => ls.foreach(_()))
      }

      def bracket[R, I]
        (init: () => Task[I])
        (cleanup: I => Task[Unit])
        (tests: Uses[(I, R)]
      ): Uses[R] = (r, sc) =>
        init().flatMap {
          i => tests((i, r), sc).attempt.flatMap(p => p.fold(e => cleanup(i).flatMap(_ => Task.fail(e)), a => cleanup(i).as(a)))
        }
    }
  }

  object streaming {

    def runTests[F[_]: Applicative, G[_]: Applicative, I](
      testGenerator: I => F[Result]
    ): Fold[G, I, F[Result]] =
      new Fold[G, I, F[Result]] {
        type S = List[F[Result]]
        val start = Nil
        def step(s: List[F[Result]], i: I): G[List[F[Result]]] =
          (testGenerator(i) :: s).pure[G]
        def end(s: List[F[Result]]): G[F[Result]] = {
          s.foldRight(Succeed().point[F])(
            Applicative[F].apply2(_, _)(Result.combine)
          ).pure[G]
        }
      }

    def exhaustive[F[_]: Applicative, I]
                  (in: List[(() => I)])
                  (testGenerator: I => F[Result])
                  : F[Result] =
      in.foldLeft(Succeed().point[F])((b, a) =>
        Applicative[F].apply2(b, testGenerator(a()))(Result.combine)
      )

    def exhaustiveU[F[_]: BindRec, I]
                  (in: Unfold[F, I])
                  (testGenerator: I => F[Result])
                  (implicit F: Monad[F]): F[Result] = {
      F.join(exhaustiveUR[F, F, I](in)(testGenerator))
    }

    def exhaustiveUR[F[_]: Applicative, G[_]: Monad: BindRec, I]
    (in: Unfold[G, I]
    )(testGenerator: I => F[Result]
    ): G[F[Result]] = {
      zapFold(in, runTests[F, G, I](testGenerator))
    }

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
