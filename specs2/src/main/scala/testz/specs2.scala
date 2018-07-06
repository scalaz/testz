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

import scala.concurrent.Future

// import org.specs2.mutable
// import org.specs2.specification.core.Fragment

object specs2 {

  abstract class SpecsSuite[T] {
    def test(test: () => Future[TestResult]): T
    def section(tests1: T, testss: T*): T
  }

  // abstract class TaskSuite() extends mutable.Specification {
  //   def tests[T[_]](test: Harness[() => ?, T]): T[Unit]

  //   private def makeHarness: Harness[() => ?, ? => Fragment] =
  //     new Harness[() => ?, ? => Fragment] {
  //       def apply[R]
  //         (name: String)
  //         (assertion: R => () => TestResult)
  //         : R => Fragment = {
  //         // todo: catch exceptions
  //         r => name in (assertion(r) must_== Success)
  //       }
  //       def section[R]
  //         (name: String)
  //         (
  //           test1: R => Fragment,
  //           tests: R => Fragment*
  //         ): R => Fragment = { r =>
  //           name should {
  //             val h = test1(r)
  //             tests.map(_(r)).lastOption.getOrElse(h)
  //           }
  //       }
  //       def bracket[R, I]
  //         (init: () => I)
  //         (cleanup: I => () => Unit)
  //         (tests: ((I, R)) => Fragment
  //       ): R => Fragment = { r =>
  //         val i = init()
  //         // this probably doesn't work, because
  //         // specs2 has some magical execution model things.
  //         val f = tests((i, r))
  //         cleanup(i)
  //         f
  //       }
  //       def mapResource[R, RN](test: R => Fragment)(f: RN => R): RN => Fragment = {
  //         rn => test(f(rn))
  //       }
  //   }

  //   test[? => Fragment](makeHarness)(())

  // }

}
