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

import extras._
import runner.TestOutput

object ExtrasSuite {
  def docHarnessTests[T](test: Test[Result, T]): T = {
    val docTest = DocHarness.test[Result]
    val docSection = DocHarness.section
    test("entire harness") { () =>
      val buf = new scala.collection.mutable.ListBuffer[String]()
      docSection.named("outer named section")(
        docSection.named("first inner named section")(
          docTest("first test inside of first inner named section")(() => ???),
          docTest("second test inside of first inner named section")(() => ???)
        ),
        docSection.named("second inner named section")(
          docTest("first test inside of second inner section")(() => ???),
          docSection(docTest("first test inside of section inside second inner named section")(() => ???)),
        )
      )("  ", buf)
      assert(buf.result() == List(
        "    [outer named section]",
        "      [first inner named section]",
        "        first test inside of first inner named section",
        "        second test inside of first inner named section",
        "      [second inner named section]",
        "        first test inside of second inner section",
        "          first test inside of section inside second inner named section"))
    }
  }

  def testOnlyTests[T](test: Test[Result, T], section: Section[T]): T = {
    section(
      section.named("apply")(
        test("yes") { () =>
          val yes = List("hey", "there")
          assert(
            TestOnly[List[String], String](
              (ls: List[String]) => ls.contains[Any]("there")
            )(_(yes))("ZE")("IN")
            ==
            "IN"
          )
        },
        test("no") { () =>
          val no = List("hey")
          assert(
            TestOnly[List[String], String](
              (ls: List[String]) => ls.contains[Any]("there")
            )(_(no))("ZE")("IN")
            ==
            "ZE"
          )
        },
      ),
      section.named("integrations")(
        test("pure") { () =>
          val test = TestOnly.pure(_.contains("correct test name"))[Unit] {
            (_: Unit, ls) => new TestOutput(failed = true, () => ())
          }
          assert(
            test((), List("correct test name")).failed &&
            !test((), List("other test name")).failed
          )
        },
        test("future") { () =>
          val test = TestOnly.future(_.contains("correct test name"))[Unit] {
            (_: Unit, ls) => Future.successful(new TestOutput(failed = true, () => ()))
          }
          // `future` preserving synchronicity is part of its contract.
          def mustSync[A](f: Future[A]): A = f.value.get.get
          assert(
            mustSync(test((), List("correct test name"))).failed &&
            !mustSync(test((), List("other test name"))).failed
          )
        },
      )
    )
  }

  def tests[T](test: Test[Result, T], section: Section[T]): T =
    section(
      section.named("Document harness")(
        docHarnessTests(test)
      ),
      section.named("TestOnly")(
        testOnlyTests(test, section)
      ),
    )

}
