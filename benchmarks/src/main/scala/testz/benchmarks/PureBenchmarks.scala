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
package benchmarks

import runner.TestOutput

import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.global

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class BenchState {
  var bufferedOut: (String => Unit, () => Unit) = _

  @Setup(Level.Trial)
  def doSetup(): Unit = {
    bufferedOut = (_ => (), () => ())
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@Measurement(time = 500, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class BulkPureBenchmarks {
  @Param(value = Array("true")) //, "false"))
  var newOutput: Boolean = _

  @Param(value = Array("50"))
  var perSuite: Int = _

  @Param(value = Array("500"))
  var numSuites: Int = _

  object FailedSuite {
    def tests[T](harness: Harness[T]): T = {
      import harness._

      section("long, long section name")(
        test("test number 0")(() => Fail()),
        List.tabulate(perSuite - 1)(n =>
          test("test number " + (n + 1))(() => Fail())
        ): _*
      )
    }
  }

  object SucceededSuite {
    def tests[T](harness: Harness[T]): T = {
      import harness._

      section("long, long section name")(
        test("test number 0")(() => Succeed()),
        List.tabulate(perSuite - 1)(n =>
          test("test number " + (n + 1))(() => Succeed())
        ): _*
      )
    }
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.SampleTime))
  def runFailSuites(myState: BenchState): Unit = {
    val (print, flush) =
      if (newOutput)
        myState.bufferedOut
      else
        (Console.print(_), () => ())

    val harness = PureHarness.makeFromPrinter(
      (res, ls) => runner.printStrs(runner.printTest(res, ls), print)
    )

    val suites: List[() => Future[TestOutput]] =
      List.fill(numSuites)(() =>
        Future.successful(FailedSuite.tests(harness)((), Nil))
      )

    val result = Await.result(runner(suites, print, global), Duration.Inf)

    if (!result.failed) throw new Exception()

    flush()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.SampleTime))
  def runSucceedSuites(myState: BenchState): Unit = {
    val (print, flush) =
      if (newOutput)
        myState.bufferedOut
      else
        (Console.print(_), () => ())

    val harness = PureHarness.makeFromPrinter(
      (res, ls) => runner.printStrs(runner.printTest(res, ls), print)
    )

    val suites: List[() => Future[TestOutput]] =
      List.fill(numSuites)(() =>
        Future.successful(SucceededSuite.tests(harness)((), Nil))
      )

    val result = Await.result(runner(suites, print, global), Duration.Inf)

    if (result.failed) throw new Exception()

    flush()
  }

}
