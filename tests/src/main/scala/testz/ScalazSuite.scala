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

import runner.TestOutput

import z._
import scalaz._, Scalaz._
import scalaz.concurrent.Task

object ScalazSuite {
  def tests[T](harness: Harness[T]): T = {
    import harness._
    import StdlibSuite.{testSection, sectionTestData}

    // every possible `Result` value
    val allResults = List(Succeed(), Fail())

    val noOpTHarness = TaskHarness.makeFromPrinterR((_, _) => ())

    section(
      namedSection("instances")(
        namedSection("result monoid")(
          test("mappend equivalent to Result.combine") { () =>
            (allResults |@| allResults).tupled.map {
              case (i1, i2) =>
                assert(
                  Result.combine(i1, i2) == (i1 |+| i2)
                )
            }.reduce(Result.combine)
          },
          test("mempty is Succeed()") { () =>
            assert(Monoid[Result].zero == Succeed())
          },
        ),
        namedSection("result equal")(
          test("should agree with equals") { () =>
            (allResults |@| allResults).tupled.foldMap {
              case (i1, i2) =>
                assert((i1 == i2) == (i1 === i2))
            }
          },
        ),
      ),
      namedSection("TaskHarness")(
        test("section") { () =>
          sectionTestData.map {
            case (faileds, expectingFailure) =>
              testSection[TaskHarness.Uses[List[Int]]](
                faileds,
                expectingFailure,
                t => (res, sc) => Task.now(t(res, sc)),
                noOpTHarness.section[List[Int]],
                (t, res, sc) => t(res, sc).unsafePerformSync,
                (s, i) => s == s"hey, there - 1, 2 $i"
              )
          }.reduce(Result.combine)
        },
        test("namedSection") { () =>
          sectionTestData.map {
            case (faileds, expectingFailure) =>
              testSection[TaskHarness.Uses[List[Int]]](
                faileds,
                expectingFailure,
                t => (res, sc) => Task.now(t(res, sc)),
                noOpTHarness.namedSection[List[Int]]("section name"),
                (t, res, sc) => t(res, sc).unsafePerformSync,
                (s, i) => s == s"section name, hey, there - 1, 2 $i"
              )
          }.reduce(Result.combine)
        },
        test("test") { () =>
          var outerRes = ""
          var x = ""
          val harness = TaskHarness.makeFromPrinterR((r, ls) => x = s"$x - $r - $ls")
          val test =
            harness.test[List[Int]]("test name") { (res: List[Int]) =>
              outerRes = res.toString
              Succeed()
            }
          val result = test(List(1, 2), List("outer")).unsafePerformSync
          val notPrintedEarly = (x == "")
          result.print()
          assert(
            notPrintedEarly &&
            (x == " - Succeed - List(test name, outer)") &&
            (outerRes == "List(1, 2)")
          )
        },
        test("allocate") { () =>
          var stages: List[String] = Nil
          var testOut = ""
          val alloc: (Unit, List[String]) => TestOutput =
            (u, ls) =>
              TaskHarness.makeFromPrinterR((_, _) => ()).allocate[Unit, List[Int]] { () =>
                stages ::= "alloc"
                List(1, 2)
              } {
                (res: (List[Int], Unit), ls: List[String]) =>
                  stages ::= "test"
                  testOut = res._1.toString + " " + ls
                  Task.now(new TestOutput(false, { () => stages ::= "print" }))
              }(u, ls).unsafePerformSync
          val result = alloc((), List("scope"))
          val ranEarly = (testOut != "") || (stages != Nil)
          val notPrintedEarly = !stages.contains("print")
          result.print()
          assert(
            ranEarly &&
            notPrintedEarly &&
            (stages == List("print", "test", "alloc")) &&
            (testOut == "List(1, 2) List(scope)") &&
            !result.failed
          )
        },
        test("bracket") { () =>
          var stages: List[String] = Nil
          var testOut = ""
          val bracket: (Unit, List[String]) => TestOutput =
            (u, ls) =>
              TaskHarness.makeFromPrinterEffR((_, _) => ()).bracket[Unit, List[Int]] { () =>
                stages ::= "alloc"
                Task.now(List(1, 2))
              } {
                li => Task.delay { stages ::= "cleanup" }
              } {
                (res: (List[Int], Unit), ls: List[String]) =>
                  stages ::= "test"
                  testOut = res._1.toString + " " + ls
                  Task.now(new TestOutput(false, { () => stages ::= "print" }))
              }(u, ls).unsafePerformSync
          val result = bracket((), List("scope"))
          val ranEarly = (testOut != "") || (stages != Nil)
          val notPrintedEarly = !stages.contains("print")
          result.print()
          assert(
            ranEarly &&
            notPrintedEarly &&
            (stages == List("print", "cleanup", "test", "alloc")) &&
            (testOut == "List(1, 2) List(scope)") &&
            !result.failed
          )
        },
      )
    )
  }
}
