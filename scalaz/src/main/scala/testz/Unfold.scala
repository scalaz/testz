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

import scalaz._, Scalaz._

/**
 * inspired by on atnos-org/origami (in large part), scalaz.Reducer, tekmo/foldl
 * This data structure encapsulates a monadic unfold with:
 *  - a 'start' value: F[S]
 *  - a 'step' method to output new values and new state: S => F[Option[(S, A)]]
 */
abstract class Unfold[F[_], A] { self =>
  type S
  def start: S
  def step(s: S): F[Option[(S, A)]]

  // map from output values
  final def rmap[B](f: A => B)(implicit F: Functor[F]) = new Unfold[F, B] {
    type S = self.S
    val start = self.start
    def step(s: S) = self.step(s).map(_.map { case (newA, b) => (newA, f(b)) })
  }

  final def flatRmap[B](f: A => F[B])(implicit F: Monad[F]) = new Unfold[F, B] {
    type S = self.S
    val start = self.start
    def step(s: S) = self.step(s).flatMap {
      _.traverse {
        case (newA, b) => f(b).strengthL(newA)
      }
    }
  }

  final def flatMap[B](f: A => Unfold[F, B])(implicit F: Monad[F]): Unfold[F, B] = new Unfold[F, B] {
    type S = (self.S, Unfold[F, B], Any)

    val start = (self.start, null, null)

    def step(s: (self.S, Unfold[F, B], Any)) = {
      val bs = s._1
      val uf = s._2
      val ss = s._3
      if (uf eq null) {
        for {
          bigStep <- self.step(bs)
          res <- bigStep.traverseM {
            case (newBigS, newA) =>
              val newUf = f(newA)
              for {
                smallStep <- newUf.step(newUf.start)
              } yield smallStep.map {
                case (newSmallS, newB) =>
                  ((newBigS, newUf, newSmallS.asInstanceOf[Any]), newB)
              }
          }
        } yield res
      } else {
        uf.step(ss.asInstanceOf[uf.S]).flatMap {
          case Some((newSs, newB)) =>
            ((bs, uf, newSs.asInstanceOf[Any]), newB).some.pure[F]
          case None => step((bs, null, null))
        }
      }
    }
  }

  def translate[G[_]](nat: F ~> G) = new Unfold[G, A] {
    type S = self.S
    val start = self.start
    def step(s: S) = nat(self.step(s))
  }

}

object Unfold {
  // just `uncons`
  def apply[F[_], A](elems: A*)(implicit F: Monad[F]): Unfold[F, A] = new Unfold[F, A] {
    type S = List[A]
    val start: List[A] = elems.toList
    def step(s: List[A]): F[Option[(List[A], A)]] = F.pure(s match {
      case x :: xs => (xs, x).some
      case Nil => none
    })
  }
}
