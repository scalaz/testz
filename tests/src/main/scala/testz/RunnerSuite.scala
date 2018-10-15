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

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable.ListBuffer

object RunnerSuite {
  def tests[T](test: Test[Future[Result], T], section: Section[T], ec: ExecutionContext): T = {
    section(
      section.named("timing")(
        test(
          "all tests should be run once and their results printed immediately, and in order"
        ) { () =>
          val out = new java.lang.StringBuilder()

          def tests = List(
            { () =>
              out.append("1 started\n");
              Future(
                new TestOutput(failed = false, print = { () => val _ = out.append("1 finished\n") })
              )(ec)
            },
            { () =>
              out.append("2 started\n");
              Future(
                new TestOutput(failed = false, print = { () => val _ = out.append("2 finished\n") })
              )(ec)
            },
            { () =>
              out.append("3 started\n");
              Future(
                new TestOutput(failed = false, print = { () => val _ = out.append("3 finished\n") })
              )(ec)
            },
          )

          runner(tests, _ => (), ec).map { _ =>
            assert(out.toString() ==
            """1 started
            |1 finished
            |2 started
            |2 finished
            |3 started
            |3 finished
            |""".stripMargin)
          }(ec)
        },

        test("any failed tests should make all tests show up as failed") { () =>
          def tests = List(
            () => Future.successful(new TestOutput(failed = false, print = () => ())),
            () => Future.successful(new TestOutput(failed = true, print = () => ())),
            () => Future.successful(new TestOutput(failed = false, print = () => ())),
          )
          runner(tests, _ => (), ec).map { r =>
            assert(r.failed)
          }(ec)
        },

        test("all successful tests should make all tests show up as successful") { () =>
          def tests = List(
            () => Future.successful(new TestOutput(failed = false, print = () => ())),
            () => Future.successful(new TestOutput(failed = false, print = () => ())),
            () => Future.successful(new TestOutput(failed = false, print = () => ())),
            () => Future.successful(new TestOutput(failed = false, print = () => ())),
          )
          runner(tests, _ => (), ec).map { r =>
            assert(!r.failed)
          }(ec)
        },
      ),
      section.named("TestOutput")(
        test("combine") { () =>
          def test(failed1: Boolean, failed2: Boolean, failedExpected: Boolean): Result = {
            var x = ""
            var y = ""
            val xSetter = () => x += "hey1"
            val ySetter = () => y += "hey2"
            val fst = new TestOutput(failed = failed1, xSetter)
            val snd = new TestOutput(failed = failed2, ySetter)
            val combined = TestOutput.combine(fst, snd)
            combined.print()
            assert(
              (combined.failed == failedExpected) &&
              (x == "hey1") &&
              (y == "hey2")
            )
          }
          Future.successful(
            List(
              test(true, true, true),
              test(false, true, true),
              test(true, false, true),
              test(false, false, false)
            ).reduce(Result.combine)
          )
        },
        test("combineAll1") { () =>
          def test(faileds: List[Boolean], expected: Boolean): Result = {
            val arr = Array.fill(faileds.length)("")
            def setter(i: Int): () => Unit =
              () => arr(i) += ("hey" + i)
            val outputs = faileds.zipWithIndex.map {
              case (f, i) => new TestOutput(f, setter(i))
            }
            val combined = TestOutput.combineAll1(outputs.head, outputs.tail: _*)
            combined.print()
            assert(
              (combined.failed == expected) &&
              arr.zipWithIndex.forall {
                case (s, i) => (s == ("hey" + i))
              }
            )
          }
          Future.successful(
            List(
              test(List(false, false, false, false, false), false),
              test(List(true, true, true, true, true), true),
              test(List(true, true, true, true, false), true),
              test(List(false, false, false, false, true), true),
              test(List(true, false, false, false, false), true),
              test(List(false, true, true, true, true), true)
            ).reduce(Result.combine)
          )
        },
      ),
      section.named("printing utilities")(
        test("printStrs") { () =>
          val strs = List(
            List(),
            List("a", "b", "c"),
          )
          Future.successful(strs.map { l =>
            val ctr = new ListBuffer[String]
            runner.printStrs(l, { s => val _ = ctr += s })
            assert(ctr.result() == l)
          }.reduce(Result.combine))
        },
        test("printStrss") { () =>
          val strss = List(
            (Nil, Nil),
            (List(Nil), Nil),
            (List(Nil, Nil), Nil),
            (List(List("a", "b", "c"), Nil), List(List("a", "b", "c"), List("\n"))),
            (List(Nil, List("a", "b", "c")), List(List("a", "b", "c"), List("\n"))),
            (List(List("a", "b", "c"), List("d", "e", "f")),
              List(List("a", "b", "c"), List("\n"), List("d", "e", "f"), List("\n")))
          )
          Future.successful(strss.map {
            case (i, o) =>
              val ctr = new ListBuffer[List[String]]
              runner.printStrss(i, { l => val _ = ctr += l })
              assert(ctr.result() == o)
          }.reduce(Result.combine))
        },
        test("intersperseReverse") { () =>
          val strs = List(
            (List("a"), List("a")),
            (List("a", "b"), List("b", "\n", "a")),
            (List("a", "b", "c"), List("c", "\n", "b", "\n", "a")),
          )

          Future.successful(strs.map {
            case (i, o) =>
              assert(
                runner.intersperseReverse(i.asInstanceOf[::[String]], "\n") == o
              )
          }.reduce(Result.combine))
        },
        test("printTest") { () =>
          val results = List(Succeed(), Fail())
          val data = List(
            (Nil, List("failed\n")),
            (List("first"), List("first", "->", "failed\n")),
            (List("first", "second"), List("second", "->", "first", "->", "failed\n")),
          )
          Future.successful((for {
            r <- results
            d <- data
          } yield
            assert(
              runner.printTest(Fail(), d._1) == d._2 &&
              runner.printTest(Succeed(), d._1) == Nil
            )
          ).reduce(Result.combine))
        },
      )
    )
  }
}
