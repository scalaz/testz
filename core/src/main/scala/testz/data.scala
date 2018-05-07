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

import scala.util.{Left, Right}

trait Harness[F[_], T[_]] {
  def apply[R](name: String)(assertions: R => F[TestResult]): T[R]
  def bracket[R, I]
    (init: F[I])
    (cleanup: I => F[Unit])
    (tests: T[(I, R)]): T[R]
  def section[R](name: String)(test1: T[R], tests: T[R]*): T[R]
}

// an s-expression of test structure to interpret.
// suite types qualify as mini LISP interpreters.
// I build the s-expression instead of interpreting it
// in-place, to implement `bracket`.
// `Test` is parameterized over this; if you want your
// own test structure type, go ahead.
// sealed trait TestS[F[_]]

// final case class Bracket[F[_], I](acquire: F[I], release: I => F[Unit], run: TestS[(I, R)]) extends TestS[F]
// final case class Layer[F[_]](name: String, leaf1: TestS[F], leaves: List[TestS[F]]) extends TestS[F]
// final case class Term[F[_]](name: String, leaf: List[String] => F[Unit]) extends TestS[F]

// object TestS {
//   // def distribute[F[_], I1, I2, A](u: Using[Using[F, I1, ?], I2, A]): Using[Using[F, I2, ?], I1, A] = {
//   //   ((i1: I1) => (i2: I2) => u.asInstanceOf[I2 => I1 => F[A]](i2)(i1))
//   //     .asInstanceOf[Using[Using[F, I2, ?], I1, A]]
//   // }

//   trait NT[F[_], G[_]] {
//     def apply[A](fa: F[A]): G[A]
//   }

//   // trait Monad[F[_]] {
//   //   def pure[A](a: A): F[A]
//   //   def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
//   // }

//   def swap[R, I, I2](nat: NT[F, G]): NT[Using[F, I, ?], Using[G, I, ?]] =
//     new NT[Using[F, I, ?], Using[G, I, ?]] {
//       def apply[A](fa: Using[F, I, A]): Using[G, I, A] = {
//         using[I](i => nat(fa.runWith(i)))
//       }
//     }

//   def map[F[_], G[_]](ts: TestS[F])(nat: NT[F, G]): TestS[G] = ts match {
//     case Term(name, t) => Term(name, t.andThen(nat(_)))
//     case bra: Bracket[F[?], i] =>
//       Bracket(nat(bra.acquire), map(bra.run)(mapUsing(nat)), bra.release.andThen(nat(_)))
//     case Layer(name, l, ls) =>
//       Layer(name, map(l)(nat), ls.map(map(_)(nat)))
//   }

//   def run[F[_], I](ts: TestS[F], stack: List[String]
//                   )(
//                     implicit F: Monad[F]
//                   ): F[Unit] = ts match {
//     case bra: Bracket[F[?], i] =>
//       F.flatMap(bra.acquire) {
//         res =>
//           F.flatMap(run(
//             map(bra.run)(new NT[Using[F, i, ?], F] {
//               def apply[A](u: Using[F, i, A]): F[A] = u.runWith(res)
//             }),
//             stack
//           ))(r => F.flatMap(bra.release(res))(_ => F.pure(r)))
//       }
//     case Term(name, t) => t(name :: stack)
//     case Layer(name, t, ts) =>
//       ts.foldRight(run(t, name :: stack))(
//         (a, b) => F.flatMap(run(a, name :: stack))(nes => F.flatMap(b)(_ => F.pure(())))
//       )
//   }
// }

/**
  The type of test results.
  A two-branch sum, either `Success`, or `Failure(failures)`.
 `Failure` can contain 0 or more failure messages, and/or
  throwables. The empty case of `Failure` is deliberate;
  it's the user's choice whether to add failure information.
*/
sealed trait TestResult

final class Failure(val failures: List[Throwable Either String]) extends TestResult
object Failure {
  @inline def apply(failures: List[Throwable Either String]): TestResult =
    new Failure(failures)

  @inline def strings(failures: List[String]): TestResult =
    apply(failures.map[Throwable Either String, List[Throwable Either String]](Right(_)))

  @inline def string(failure: String): TestResult = new Failure(List(Right(failure)))

  @inline def errors(errs: Throwable*): TestResult =
    apply(errs.map[Throwable Either String, List[Throwable Either String]](Left(_))(collection.breakOut))

  @inline def error(err: Throwable): TestResult = new Failure(List(Left(err)))

  val noMessage: TestResult = new Failure(Nil)
}

case object Success extends TestResult {
  @inline final def apply(): TestResult = this
}
// TODO: use a pretty-printer?

object TestResult {
  def combine(first: TestResult, second: TestResult): TestResult =
   (first, second) match {
      case (fs1: Failure, fs2: Failure) => Failure(fs1.failures ++ fs2.failures)
      case (fs1: Failure, _) => fs1
      case (_, fs2: Failure) => fs2
      case _ => Success
    }
}

// final class TestSTest[F[_]](printExceptions: (List[String], F[TestResult]) => F[Unit]) extends Test[TestS, F] {

//   def apply[G[_]]
//     (name: String)
//     (assertions: G[TestResult])
//     (implicit F: MapUnder[F, G]): TestS[G] = {
//     Term(name, ls => F.mapUnder(assertions)(printExceptions(ls, _)))
//   }

//   def bracket[G[_], I]
//     (init: G[I])
//     (use: TestS[Using[G, I, ?]])
//     (cleanup: I => G[Unit])
//     (implicit F: MapUnder[F, G]): TestS[G] =
//     Bracket(init, use, cleanup)

//   def section[G[_]]
//     (name: String)
//     (test1: TestS[G], tests: TestS[G]*): TestS[G] =
//     Layer(name, test1, tests.toList)

// }
