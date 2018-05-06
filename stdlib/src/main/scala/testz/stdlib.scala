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
