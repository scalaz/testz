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

import testz.z._

import scalaz.{~>, Applicative, BindRec, Monad, Traverse}
import scalaz.concurrent.Task
import scalaz.std.list._
import scalaz.syntax.all._
import spire.random.rng._

object property {

  // property-based tests, with a certain seed.
  def fromSeed
    [F[_], I]
    (seed: (Array[Int], Int))
    (gen: Int => I)
    (testGenerator: I => F[List[TestError]])
    (numTestCases: Int)
    (implicit F: Applicative[F]): F[List[TestError]] = {
    val rng = Well44497a.fromSeed(seed)
    var acc: F[List[TestError]] = F.pure(Nil)
    var i = 0
    while (i < numTestCases) {
      acc = F.apply2(acc, testGenerator(gen(rng.nextInt)))(_ ++ _)
      i = i + 1
    }
    acc
  }

  def repeatable
    [F[_], I]
    (gen: Int => I)
    (testGenerator: I => F[List[TestError]])
    (numTestCases: Int)
    (implicit F: Applicative[F]): F[List[TestError]] =
    fromSeed[F, I]((Array.fill[Int]((44497 + 31) / 32)(1010101010), 2))(gen)(testGenerator)(numTestCases)

  def nonrepeatable
    [F[_], I]
    (gen: Int => I)
    (testGenerator: I => F[List[TestError]])
    (numTestCases: Int)
    (fromTask: Task ~> F)
    (implicit F: Monad[F]): F[List[TestError]] =
    for {
      seed <- fromTask(Task.delay(
        Array.fill[Array[Int]](10)(
          Array(System.currentTimeMillis.toInt, (System.currentTimeMillis >> 4).toInt))
      ))
      errs <- fromSeed[F, I]((seed.flatten, 1))(gen)(testGenerator)(numTestCases)
    } yield errs

  def exhaustive[F[_]: Applicative, I]
                (in: List[(() => I)])
                (testGenerator: I => F[List[TestError]])
                : F[List[TestError]] = {
    Traverse[List].traverseM(in)(i => testGenerator(i()))
  }

  def exhaustiveU[F[_]: BindRec, I]
                 (in: Unfold[F, I])
                 (testGenerator: I => F[List[TestError]])
                 (implicit F: Monad[F]): F[List[TestError]] = {
    F.join(exhaustiveUR[F, F, I](in)(testGenerator))
  }

  def runTests[F[_]: Applicative, G[_]: Applicative, I](
    testGenerator: I => F[List[TestError]]
  ): Fold[G, I, F[List[TestError]]] =
    new Fold[G, I, F[List[TestError]]] {
      type S = List[F[List[TestError]]]
      val start = Nil
      def step(s: List[F[List[TestError]]], i: I): G[List[F[List[TestError]]]] =
        (testGenerator(i) :: s).pure[G]
      def end(s: List[F[List[TestError]]]): G[F[List[TestError]]] =
        s.reverse.traverseM(f => f).pure[G]
    }

  def exhaustiveUR[F[_]: Applicative, G[_]: Monad: BindRec, I]
  (in: Unfold[G, I]
  )(testGenerator: I => F[List[TestError]]
  ): G[F[List[TestError]]] = {
    zapFold(in, runTests[F, G, I](testGenerator))
  }

  def exhaustiveV[F[_]: Applicative, I]
                 (in: (() => I)*)
                 (testGenerator: I => F[List[TestError]]): F[List[TestError]] =
    exhaustive(in.toList)(testGenerator)

  def exhaustiveS[F[_]: Applicative, I]
                 (in: I*)
                 (testGenerator: I => F[List[TestError]]): F[List[TestError]] =
    exhaustiveV(in.map(i => () => i): _*)(testGenerator)
}