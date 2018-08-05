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

import runner._

import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.global

import org.openjdk.jmh.annotations.{
  Benchmark, BenchmarkMode, Level, Mode, OutputTimeUnit, Param, Scope, Setup, State
}

@State(Scope.Benchmark)
class BenchState {
  var bufferedOut: (String => Unit, () => Unit) = _

  @Setup(Level.Trial)
  def doSetup(): Unit = {
    bufferedOut = Runner.bufferedStdOut(4096)
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class Bulk {
  @Param(value = Array("true", "false"))
  var newOutput: Boolean = _

  @Param(value = Array("50"))
  var perSuite: Int = _

  @Param(value = Array("20"))
  var numSuites: Int = _

  abstract class PureSuite {
    def tests[T[_]](harness: PureHarness[T]): T[Unit]
  }

  def failedSuite = new PureSuite {
    def tests[T[_]](harness: PureHarness[T]): T[Unit] = {
      import harness._

      section("long, long section name")(
        test("test number 0")(_ => Fail.noMessage),
        List.tabulate(perSuite - 1)(n =>
          test[Unit]("test number " + (n + 1))(_ => Fail.noMessage)
        ): _*
      )
    }
  }

  def succeededSuite = new PureSuite {
    def tests[T[_]](harness: PureHarness[T]): T[Unit] = {
      import harness._

      section("long, long section name")(
        test("test number 0")(_ => Succeed),
        List.tabulate(perSuite - 1)(n =>
          test[Unit]("test number " + (n + 1))(_ => Succeed)
        ): _*
      )
    }
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  def runFailSuites(myState: BenchState): Unit = {
    val (print, flush) =
      if (newOutput)
        myState.bufferedOut
      else
        (Console.print(_), () => ())

    val config = Runner.defaultConfig.withOutputSuite(Runner.printStrs(_, print))

    val harness = PureHarness.make((ls, res) => Runner.printStrs(Runner.printTest(ls, res), print))

    val suite = failedSuite

    val suites: List[() => Future[TestOutput]] =
      List.fill(numSuites)(() => Future.successful(suite.tests(harness)((), Nil)))

    val result = Await.result(Runner.configured(suites, config, global), Duration.Inf)

    if (result.failed) throw new Exception()

    flush()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  def runSucceedSuites(myState: BenchState): Unit = {
    val (print, flush) =
      if (newOutput)
        myState.bufferedOut
      else
        (Console.print(_), () => ())

    val config = Runner.defaultConfig.withOutputSuite(Runner.printStrs(_, print))

    val harness = PureHarness.make((ls, res) => Runner.printStrs(Runner.printTest(ls, res), print))

    val suite = succeededSuite

    val suites: List[() => Future[TestOutput]] = List.fill(numSuites)(() => Future.successful(suite.tests(harness)((), Nil)))

    val result = Await.result(Runner.configured(suites, config, global), Duration.Inf)

    if (result.failed) throw new Exception()


    flush()
  }

}
