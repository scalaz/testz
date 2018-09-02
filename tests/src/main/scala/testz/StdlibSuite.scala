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

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

import runner.TestOutput

object StdlibSuite {

    def testSection[U](
      faileds: List[Boolean],
      expectingFailure: Boolean,
      toUses: ((List[Int], List[String]) => TestOutput) => U,
      underTest: (U, List[U]) => U,
      run: (U, List[Int], List[String]) => TestOutput,
      validOutput: (String, Int) => Boolean
    ): Result = {
      val arr = Array.fill(faileds.length)("")
      def setter(r: List[Int], ls: List[String], i: Int): () => Unit =
        () => arr(i) += (s"""${ls.mkString(", ")} - ${r.mkString(", ")} """ + i)
      val outputs = faileds.zipWithIndex.map {
        case (f, i) => toUses((r: List[Int], ls: List[String]) => new TestOutput(f, setter(r, ls, i)))
      }
      val combined =
        run(underTest(outputs.head, outputs.tail), List(1, 2), List("hey", "there"))
      combined.print()
      assert(
        (combined.failed == expectingFailure) &&
        arr.zipWithIndex.forall(validOutput.tupled)
      )
    }

    val sectionTestData = List(
      (List(false, false, false, false, false), false),
      (List(true, true, true, true, true), true),
      (List(true, true, true, true, false), true),
      (List(false, false, false, false, true), true),
      (List(true, false, false, false, false), true),
      (List(false, true, true, true, true), true),
    )

  def tests[T](harness: Harness[T], ec: ExecutionContext): T = {
    import harness._

    val noOpPHarness = PureHarness.makeFromPrinterR((_, _) => ())
    val noOpFHarness = FutureHarness.makeFromPrinterEffR((_, _) => ())(ec)

    section(
      namedSection("PureHarness")(
        test("section") { () =>
          sectionTestData.map {
            case (faileds, expectingFailure) =>
              testSection[PureHarness.Uses[List[Int]]](
                faileds,
                expectingFailure,
                t => t,
                noOpPHarness.section[List[Int]],
                (t, res, sc) => t(res, sc),
                (s, i) => s == s"hey, there - 1, 2 $i"
              )
          }.reduce(Result.combine)
        },
        test("namedSection") { () =>
          sectionTestData.map {
            case (faileds, expectingFailure) =>
              testSection[PureHarness.Uses[List[Int]]](
                faileds,
                expectingFailure,
                t => t,
                noOpPHarness.namedSection[List[Int]]("section name"),
                (t, res, sc) => t(res, sc),
                (s, i) => s == s"section name, hey, there - 1, 2 $i"
              )
          }.reduce(Result.combine)
        },
        test("test") { () =>
          var outerRes = ""
          var x = ""
          val harness = PureHarness.makeFromPrinterR((r, ls) => x = s"$x - $r - $ls")
          val test =
            harness.test[List[Int]]("test name") { (res: List[Int]) =>
              outerRes = res.toString
              Succeed()
            }
          val result = test(List(1, 2), List("outer"))
          val notPrintedEarly = (x == "")
          result.print()
          assert(
            notPrintedEarly &&
            (x == " - Succeed - List(test name, outer)") &&
            (outerRes == "List(1, 2)")
          )
        },
        test("allocate") { () =>
          var testOut = ""
          var printOut = ""
          val alloc: (Unit, List[String]) => TestOutput =
            PureHarness.makeFromPrinterR((_, _) => ()).allocate[Unit, List[Int]](() => List(1, 2)) {
              (res: (List[Int], Unit), ls: List[String]) =>
                testOut = res._1.toString + " " + ls
                new TestOutput(false, { () => printOut = "printed" })
            }
          val result = alloc((), List("scope"))
          val ranEarly = (testOut != "")
          val notPrintedEarly = (printOut == "")
          result.print()
          assert(
            ranEarly &&
            notPrintedEarly &&
            (printOut == "printed") &&
            (testOut == "List(1, 2) List(scope)") &&
            !result.failed
          )
        },
      ),
      namedSection("FutureHarness")(
        test("section") { () =>
          sectionTestData.map {
            case (faileds, expectingFailure) =>
              testSection[FutureHarness.Uses[List[Int]]](
                faileds,
                expectingFailure,
                t => (res, sc) => Future.successful(t(res, sc)),
                noOpFHarness.section[List[Int]],
                (t, res, sc) => Await.result(t(res, sc), Duration.Inf),
                (s, i) => s == s"hey, there - 1, 2 $i"
              )
          }.reduce(Result.combine)
        },
        test("namedSection") { () =>
          sectionTestData.map {
            case (faileds, expectingFailure) =>
              testSection[FutureHarness.Uses[List[Int]]](
                faileds,
                expectingFailure,
                t => (res, sc) => Future.successful(t(res, sc)),
                noOpFHarness.namedSection[List[Int]]("section name"),
                (t, res, sc) => Await.result(t(res, sc), Duration.Inf),
                (s, i) => s == s"section name, hey, there - 1, 2 $i"
              )
          }.reduce(Result.combine)
        },
        test("test") { () =>
          var outerRes = ""
          var x = ""
          val harness = FutureHarness.makeFromPrinterR((r, ls) => x = s"$x - $r - $ls")(ec)
          val test =
            harness.test[List[Int]]("test name") { (res: List[Int]) =>
              outerRes = res.toString
              Succeed()
            }
          val result = Await.result(test(List(1, 2), List("outer")), Duration.Inf)
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
            (u, ls) => Await.result(
              FutureHarness.makeFromPrinterR((_, _) => ())(ec).allocate[Unit, List[Int]] { () =>
                stages ::= "alloc"
                List(1, 2)
              } {
                (res: (List[Int], Unit), ls: List[String]) =>
                  stages ::= "test"
                  testOut = res._1.toString + " " + ls
                  Future.successful(new TestOutput(false, { () => stages ::= "print" }))
              }(u, ls), Duration.Inf
            )
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
            (u, ls) => Await.result(
              FutureHarness.makeFromPrinterEffR((_, _) => ())(ec).bracket[Unit, List[Int]] { () =>
                stages ::= "alloc"
                Future(List(1, 2))(ec)
              } {
                li => Future { stages ::= "cleanup" }(ec)
              } {
                (res: (List[Int], Unit), ls: List[String]) =>
                  stages ::= "test"
                  testOut = res._1.toString + " " + ls
                  Future(new TestOutput(false, { () => stages ::= "print" }))(ec)
              }(u, ls), Duration.Inf
            )
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
