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
