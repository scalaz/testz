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

import org.specs2.mutable
import org.specs2.specification.core.Fragment

object specs2 {

  abstract class TaskSuite() extends mutable.Specification {
    def test[T](test: Test[Function0, Function0[T]]): Function0[T]

    private def makeHarness: Test[() => ?, () => Fragment] =
      new Test[() => ?, () => Fragment] {
        def apply
          (name: String)
          (assertion: () => List[TestError])
          : () => Fragment = {
          // todo: catch exceptions
          () => name in (assertion() must_== Nil)
        }
        def section
          (name: String)
          (
            test1: () => Fragment,
            tests: () => Fragment*
          ): () => Fragment = {
            () =>
              name should {
                val h = test1();
                tests.map(_()).lastOption.getOrElse(h)
              }
        }
    }

    test[Fragment](makeHarness)()

  }

}
