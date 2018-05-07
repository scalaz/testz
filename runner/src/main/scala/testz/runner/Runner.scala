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
package runner

import scala.concurrent.{ExecutionContext, Future}

object Runner {
  // Configuration.
  // Forwards-compatible by construction.
  final class Config private[Runner](private val _chunkSize: Int, _output: String => Unit) {
    def withChunkSize(newChunkSize: Int) = new Config(newChunkSize, _output)
    def withOutput(newOutput: String => Unit) = new Config(_chunkSize, newOutput)
    def chunkSize: Int = _chunkSize
    def output: String => Unit = _output
  }

  val defaultConfig: Config = new Config(_chunkSize = 100, _output = print(_))

  @scala.annotation.tailrec
  private def printStrs(strs: List[String], output: String => Unit): Unit = strs match {
    case x :: xs => output(x); printStrs(xs, output)
    case _ =>
  }
  @scala.annotation.tailrec
  private def printStrss(strs: List[List[String]], output: String => Unit): Unit = strs match {
    case xs: ::[List[String]] => printStrs(xs.head, output); output("\n"); printStrss(xs.tail, output)
    case _ =>
  }

  def configured(suites: List[() => Suite], config: Config, ec: ExecutionContext): Future[Unit] = Future {
    import config._
    val startTime = System.currentTimeMillis
    Future.traverse(suites.grouped(chunkSize).toList) { chunk =>
      Future.traverse(chunk)(_().run(ec))(collection.breakOut, ec).map(printStrss(_, config.output))(ec)
    }(collection.breakOut, ec).map { r =>
      val endTime = System.currentTimeMillis
      config.output(s"Testing took ${endTime - startTime} ms")
    }(ec)
  }(ec).flatten

  def apply(suites: List[() => Suite], ec: ExecutionContext): Future[Unit] =
    configured(suites, defaultConfig, ec)

}
