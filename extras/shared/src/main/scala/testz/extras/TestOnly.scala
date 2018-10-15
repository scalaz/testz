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
package extras

import scala.concurrent.Future

import runner.TestOutput

object TestOnly {
  def apply[X, T](pred: X => Boolean)
                 (grab: (X => T) => T)
                 (ze: T)(in: T)
                 : T = {
    grab(ls => if (pred(ls)) in else ze)
  }

  def pure(pred: List[String] => Boolean): PureHarness.Uses NT PureHarness.Uses =
    new (PureHarness.Uses NT PureHarness.Uses) {
      def apply[R](in: PureHarness.Uses[R]): PureHarness.Uses[R] =
        TestOnly[List[String], PureHarness.Uses[R]](pred)(
          f => (r, ls) => f(ls)(r, ls)
        )((_, _) => TestOutput.empty)(in)
    }

  def future(pred: List[String] => Boolean): FutureHarness.Uses NT FutureHarness.Uses =
    new (FutureHarness.Uses NT FutureHarness.Uses) {
      def apply[R](in: FutureHarness.Uses[R]): FutureHarness.Uses[R] =
        TestOnly[List[String], FutureHarness.Uses[R]](pred)(
          f => (r, ls) => f(ls)(r, ls)
        )((_, _) => Future.successful(TestOutput.empty))(in)
    }

}
