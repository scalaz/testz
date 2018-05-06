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

package testz.benchmarks

import scalaz.\/
import scalaz.concurrent.Task
import scalaz.std.anyVal._
import scalaz.syntax.all._

import testz._
import testz.runner._
import testz.z._

import scala.concurrent.ExecutionContext.Implicits.global

object Main {
  def testPureSuite = new PureSuite {
    def test[T](test: Test[Function0, T]): T =
      test.section("some tests")(
        test("this test fails")(() =>
          assertEqualNoShow(1, 2)
        ),
        test("this test wins")(() =>
          assertEqualNoShow(1, 1)
        )
      )
  }

  def testTaskSuite = new TaskSuite {
    def test[T](test: Test[Task, Task[T]]): Task[T] = {
      def doMoreTests(ts: Task[T], i: Int): Task[T] = {
        if (i == 0) ts
        else doMoreTests(
          test.section(s"test section $i")(
            ts,
            test(s"this is test $i-1")(
              assertEqualNoShow(1, 1).pure[Task]
            ),
            test(s"this is test $i-2")(
              assertEqualNoShow(1, 4).pure[Task]
            )
          ), i - 1
        )
      }
      doMoreTests(
        test.section("root tests")(
          test("this test wins")(
            assertEqualNoShow(1, 1).pure[Task]
          )
        ), 1
      )
    }
  }

  def run: Task[Unit] = {
    Task.async { cb =>
      Runner(testPureSuite :: List.fill(2)(testTaskSuite))
        .onComplete(t => cb(\/.fromEither(t.toEither)))
    }
  }
}