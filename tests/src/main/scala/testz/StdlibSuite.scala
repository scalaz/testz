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

import testz.stdlib._

final class StdlibSuite extends PureSuite {
  def test[T](test: Test[Function0, T]): T = {
    test.section("equality assertions")(
      test("assertEqual/assertEqual") { () =>
        assertEqual(assertEqual(1 + 1, 2), Nil)
      },
      test("assertEqual/assertNotEqual") { () =>
        assertEqual(assertNotEqual(1, 2), Nil)
      },
      test("assertNotEqual/assertNotEqual") { () =>
        assertNotEqual(assertNotEqual(1 + 1, 2), Nil)
      },
      test("assertNotEqual/assertEqual") { () =>
        assertNotEqual(assertEqual(1, 2), Nil)
      },
      test("assertNotEqual error") { () =>
        assertEqual(assertNotEqual(1, 1), List(Failure("1\n\nwas equal to\n\n1")))
      },
      test("assertEqual error") { () =>
        assertEqual(assertEqual(1, 2), List(Failure("1\n\nwas not equal to\n\n2")))
      }
    )
  }
}
