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

  def exhaustiveU[F[_]: Monad: BindRec, I]
                 (in: Unfold[F, I])
                 (testGenerator: I => F[List[TestError]]): F[List[TestError]] = {
    exhaustiveUNat[F, F, I](in)(testGenerator)(λ[F ~> F](x => x))
  }

  def exhaustiveUNat[F[_]: Monad, G[_]: Monad: BindRec, I]
                    (in: Unfold[G, I])
                    (testGenerator: I => F[List[TestError]])
                    (nat: G ~> F): F[List[TestError]] = {
    val myFold: Fold[G, I, F[List[TestError]]] =
      new Fold[G, I, F[List[TestError]]] {
        type S = List[F[List[TestError]]]
        val start = Nil
        def step(s: List[F[List[TestError]]], i: I): G[List[F[List[TestError]]]] =
          (testGenerator(i) :: s).pure[G]
        def end(s: List[F[List[TestError]]]): G[F[List[TestError]]] =
          s.reverse.traverseM(f => f).pure[G]
      }
    nat(zapFold(in, myFold)).join
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