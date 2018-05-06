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
