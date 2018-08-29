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

object RunnerSuite {
  @inline
  def tests[T](harness: EffectHarness[Future, T], ec: ExecutionContext): T = {
    import harness._
    section("timing")(
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
    )
  }
}
