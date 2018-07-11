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

import z._

import scalaz.{~>, Applicative, BindRec, Monad}
import scalaz.concurrent.Task
import scalaz.syntax.all._
import spire.random.rng._

object property {

  def runTests[F[_]: Applicative, G[_]: Applicative, I](
    testGenerator: I => F[TestResult]
  ): Fold[G, I, F[TestResult]] =
    new Fold[G, I, F[TestResult]] {
      type S = List[F[TestResult]]
      val start = Nil
      def step(s: List[F[TestResult]], i: I): G[List[F[TestResult]]] =
        (testGenerator(i) :: s).pure[G]
      def end(s: List[F[TestResult]]): G[F[TestResult]] =
        s.reverse.foldLeft(Success().point[F])(
          Applicative[F].apply2(_, _)(TestResult.combine)
        ).pure[G]
    }

  def exhaustive[F[_]: Applicative, I]
                (in: List[(() => I)])
                (testGenerator: I => F[TestResult])
                : F[TestResult] = {
    in.foldLeft(Success().point[F])((b, a) => Applicative[F].apply2(b, testGenerator(a()))(TestResult.combine))
  }

  def exhaustiveU[F[_]: BindRec, I]
                 (in: Unfold[F, I])
                 (testGenerator: I => F[TestResult])
                 (implicit F: Monad[F]): F[TestResult] = {
    F.join(exhaustiveUR[F, F, I](in)(testGenerator))
  }

  def exhaustiveUR[F[_]: Applicative, G[_]: Monad: BindRec, I]
  (in: Unfold[G, I]
  )(testGenerator: I => F[TestResult]
  ): G[F[TestResult]] = {
    zapFold(in, runTests[F, G, I](testGenerator))
  }

  def exhaustiveV[F[_]: Applicative, I]
                 (in: (() => I)*)
                 (testGenerator: I => F[TestResult]): F[TestResult] =
    exhaustive(in.toList)(testGenerator)

  def exhaustiveS[F[_]: Applicative, I]
                 (in: I*)
                 (testGenerator: I => F[TestResult]): F[TestResult] =
    exhaustiveV(in.map(i => () => i): _*)(testGenerator)

}
