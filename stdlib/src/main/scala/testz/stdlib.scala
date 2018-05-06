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

import testz.runner._

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

abstract class PureSuite extends Suite {
  def test[T](test: Test[Function0, T]): T

  def run(implicit ec: ExecutionContext): Future[List[String]] =
    Future.successful {
      val buf = new AtomicReference[List[String]](Nil)
      this.test(PureSuite.makeHarness(buf))(Nil)
      buf.get
    }
}

object PureSuite {
  type TestEff[A] = List[String] => A

  private def add(buf: AtomicReference[List[String]], str: String): Unit = {
    val _ = buf.updateAndGet(str :: _)
  }

  def makeHarness(buf: AtomicReference[List[String]]): Test[Function0, TestEff[Unit]] =
    new Test[Function0, TestEff[Unit]] {
      def apply(name: String)(assertion: () => List[TestError]): TestEff[Unit] =
        { (ls: List[String]) =>
          val result: List[TestError] = try {
            assertion()
          } catch {
            case thrown: Exception => List(ExceptionThrown(thrown))
          }
          add(buf, Suite.printTest(name :: ls, result))
        }

      def section(name: String)(test1: TestEff[Unit], tests: TestEff[Unit]*) =
        { (ls: List[String]) =>
          val newScopes = name :: ls
          test1(newScopes)
          tests.foreach(test => test(newScopes))
        }
  }
}

object stdlib {
  object assertEqual {
    def apply[E](fst: E, snd: E): List[TestError] =
      if (fst == snd) Nil else Failure(s"$fst\n\nwas not equal to\n\n$snd") :: Nil
  }

  object assertNotEqual {
    def apply[E](fst: E, snd: E): List[TestError] =
      if (fst != snd) Nil else Failure(s"$fst\n\nwas equal to\n\n$snd") :: Nil
  }
}
