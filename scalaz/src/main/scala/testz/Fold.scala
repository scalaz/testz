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
 * This data structure encapsulates a monadic left fold with:
 *  - a 'start' value: S
 *  - a 'step' method to accumulate state: (S, A) => F[S]
 *  - an 'end' method to finalize the result: S => B
 */
abstract class Fold[F[_], A, B] { self =>
  type S

  def start: S
  def step(s: S, a: A): F[S]
  def end(s: S): F[B]

  // map from output values
  def rmap[C](f: B => C)(implicit F: Functor[F]) = new Fold[F, A, C] {
    type S = self.S
    val start = self.start
    def step(s: S, a: A) = self.step(s, a)
    def end(s: S) = self.end(s).map(f)
  }

  def flatRmap[C](f: B => F[C])(implicit F: Bind[F]) = new Fold[F, A, C] {
    type S = self.S
    val start = self.start
    def step(s: S, a: A) = self.step(s, a)
    def end(s: S) = self.end(s).flatMap(f)
  }

  /** run another fold on the end result */
  def andThen[C](f: Fold[F, B, C])(implicit F: Bind[F]) = new Fold[F, A, C] {
    type S = self.S
    val start = self.start
    def step(s: S, a: A) = self.step(s, a)
    def end(s: S) = F.bind(self.end(s))(f.run1)
  }

  /** map input values */
  def lmap[C](f: C => A) = new Fold[F, C, B] {
    type S = self.S
    val start = self.start
    def step(s: S, c: C) = self.step(s, f(c))
    def end(s: S) = self.end(s)
  }

  /** contra flatmap the input values */
  def flatLmap[C](f: C => F[A])(implicit F: Bind[F]) = new Fold[F, C, B] {
    type S = self.S
    val start = self.start
    def step(s: S, c: C) = f(c).flatMap(c1 => self.step(s, c1))
    def end(s: S) = self.end(s)
  }

  /** zip 2 steps to return a pair of values. */
  def zip[C](f: Fold[F, A, C])(implicit F: Apply[F]) = new Fold[F, A, (B, C)] {
    type S = (self.S, f.S)
    val start = (self.start, f.start)
    def step(s: S, a: A) = F.tuple2(self.step(s._1, a), f.step(s._2, a))
    def end(s: S) = F.tuple2(self.end(s._1), f.end(s._2))
  }

  /**
   * run over one element
   */
  def run1(a: A)(implicit F: Bind[F]): F[B] =
    step(start, a) >>= end

  /** equivalent of the as method for functors, but lazy */
  def asL[C](c: () => C)(implicit F: Functor[F]): Fold[F, A, C] =
    rmap(_ => c())

  def translate[G[_]](nat: F ~> G) = new Fold[G, A, B] {
    type S = self.S
    val start = self.start
    def step(s: S, a: A) = nat(self.step(s, a))
    def end(s: S) = nat(self.end(s))
  }
}
