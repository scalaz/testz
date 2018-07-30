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
  The most boring `Harness` you can think of:
  pure tests with no resources.
  Any harness type should be convertible to a `BareHarness`;
  it's the lingua franca of tests. If you write tests using
  `BareHarness`, they can be adapted to work with any suite type later.
*/
abstract class Harness[T] {
  def test
    (name: String)
    (assertions: () => Result)
    : T

  def section
    (name: String)
    (test1: T, tests: T*)
    : T
}

/**
  A type for `Suite`s with test harness `H[T]`, for some `T`.
 */
trait Suite[H[_]] {
  def tests[T](harness: H[T]): T
}

/**
 * A type for `Suite`s with test harness `H[T]`, for some `T[_]`.
 * The type parameter of `T[_]` is the type of a resource available to
 * tests. A `T[Int]` can access some `Int` which was computed for shared
 * use; harness types should guarantee that resources are properly shared.
 * The entire "test group" must require no resources to be runnable; that's
 * the meaning of `T[Unit]`.
 */
trait ResourceSuite[H[_[_]]] { self =>
  def tests[T[_]](harness: H[T]): T[Unit]
  // forgets that we use resources.
  // you can tell the resources are unused because the type parameter `X`
  // is unused.
  def toSuite: Suite[λ[T => H[λ[X => T]]]] = new Suite[λ[T => H[λ[X => T]]]] {
    def tests[T](harness: H[λ[X => T]]): T = self.tests[λ[X => T]](harness)
  }
}

/**
  The type of test results.
  A two-branch sum, either `S`, or `F(failures)`.
 `F` can contain 0 or more failure messages, and/or
  throwables. The empty case of `F` is deliberately included;
  it's the user's choice whether to add failure information.
*/
sealed abstract class Result

final class Fail(val failures: List[Either[Throwable, String]]) extends Result {
  override def toString(): String = "Failed(\n" + failures.mkString("  ", "\n  ",  "") + "\n)"
}

object Fail {
  @inline def apply(failures: List[Either[Throwable, String]]): Result =
    new Fail(failures)

  @inline def strings(failures: List[String]): Result =
    new Fail(failures.map(sa => Right(sa): Either[Throwable, String]))

  @inline def string(failure: String): Result = new Fail(List(Right(failure)))

  @inline def errors(errs: Throwable*): Result =
    new Fail(errs.map(Left(_): Either[Throwable, String])(collection.breakOut))

  @inline def error(err: Throwable): Result = new Fail(List(Left(err)))

  val noMessage: Result = new Fail(Nil)
}

case object Succeed extends Result {
  @inline final def apply(): Result = this
  override def toString(): String = "Succeed()"
}
// TODO: use a pretty-printer?

object Result {
  def combine(first: Result, second: Result): Result =
    if (first eq Succeed) second
    else if (second eq Succeed) first
    else {
      new Fail(first.asInstanceOf[Fail].failures ++ second.asInstanceOf[Fail].failures)
    }
}
