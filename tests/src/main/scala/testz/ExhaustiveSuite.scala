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
import z._, property._

final class ExhaustiveSuite extends PureSuite {
  val testData = Unfold[() => ?, Int](1, 2, 3, 4, 5, 6).flatMap {
    i =>
      val n = i * 10
      Unfold((1 to 5).toList.map(_ + n): _*)
  }

  def test[T](test: Test[() => ?, T]): T =
    test.section("exhaustives")(
      test("exhaustiveS int range") { () =>
        exhaustiveS(1, 2, 3, 4, 5, 6)(i =>
          () => assertEqualNoShow(i, 3)
        ).map(
          assertEqualNoShow(_, List.fill(5)(Failure("not equal, when should be")))
        )()
      },
      test("exhaustiveU int range") { () =>
        exhaustiveU(testData)(
          i => { () => assertNotEqualNoShow(i % 5, 1) }
        ).map(
          assertEqualNoShow(_, List.fill(6)(Failure("equal, when shouldn't be")))
        )()
      }
    )
}
