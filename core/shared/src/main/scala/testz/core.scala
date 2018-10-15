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

/**
 * I *hate* having to define this in testz,
 * but it's utterly basic.
*/
abstract class NT[F[_], G[_]] {
  def apply[A](fa: F[A]): G[A]
}

/**
  A type for test results.
  A two-branch sum, either `Succeed()`, or `Fail()`.
*/
sealed abstract class Result

final class Fail private() extends Result {
  override val toString: String = "Fail"
  override def equals(other: Any): Boolean = other.isInstanceOf[Fail]
}

object Fail {
  private val cached = new Fail()

  def apply(): Result = cached
}

final class Succeed private() extends Result {
  override val toString: String = "Succeed"
  override def equals(other: Any): Boolean = other.isInstanceOf[Succeed]
}

object Succeed {
  private val cached = new Succeed()

  def apply(): Result = cached
}

object Result {
  def combine(first: Result, second: Result): Result =
    if (first eq second) first
    else Fail()
}

abstract class Test[R, T] { self =>
  def apply(name: String)(assertions: () => R): T

  final def contramap[S](f: S => R): Test[S, T] =
    new Test[S, T] {
      def apply(name: String)(assertions: () => S): T =
        self(name)(() => f(assertions()))
    }

  final def map[U](f: T => U): Test[R, U] =
    new Test[R, U] {
      def apply(name: String)(assertions: () => R): U =
        f(self(name)(assertions))
    }
}

abstract class Section[T] {
  def named(name: String)(test1: T, tests: T*): T
  def apply(test1: T, tests: T*): T
}
