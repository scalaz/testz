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

import java.io.{BufferedOutputStream, FileDescriptor, FileOutputStream}
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}

object Runner {
  /**
   * Configuration.
   * Forwards-compatible by construction.
   */
  final class Config private[Runner](
    private val _chunkSize: Int,
    private val _outputSuite: List[String] => Unit
  ) {
    def withChunkSize(newChunkSize: Int) =
      new Config(newChunkSize, _outputSuite)
    def withOutputSuite(newOutputSuite: List[String] => Unit) =
      new Config(_chunkSize, newOutputSuite)
    def chunkSize: Int = _chunkSize
    def outputSuite: List[String] => Unit = _outputSuite
  }

  final class TestResult(val failed: Boolean)

  def bufferedStdOut(bufferSize: Int): (String => Unit, () => Unit) = {
    val out = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), bufferSize)
    (str =>
      if (!str.isEmpty) {
        out.write(str.getBytes(StandardCharsets.UTF_8), 0, str.length)
      }, () => out.flush())
  }

  val defaultChunkSize: Int = 100
  val defaultOutputSuite: List[String] => Unit =
    _.foreach(Console.print)
  val newlineSingleton =
    "\n" :: Nil

  /**
   * It seems sub-optimal to chunk at 100 suites;
   * in fact, it seems sub-optimal to chunk at all.
   * We're optimizing for quick suites, though.
   * If you have long-running suites, the chunking will hopefully
   * not interfere much because of how large the chunks are.
   * Right now, the chunks aren't very useful. They would be
   * if we ran suites in parallel.
   */
  val defaultConfig: Config =
    new Config(
      _chunkSize = defaultChunkSize,
      _outputSuite = defaultOutputSuite
    )

  @scala.annotation.tailrec
  def printStrs(strs: List[String], output: String => Unit): Unit = strs match {
    case x :: xs => output(x); printStrs(xs, output)
    case _ =>
  }

  @scala.annotation.tailrec
  def printStrss(strs: List[List[String]], output: List[String] => Unit): Unit = strs match {
    case xs: ::[List[String]] =>
      val head = xs.head
      if (head.nonEmpty) {
        output(head)
        output(newlineSingleton)
      }
      printStrss(xs.tail, output)
    case _ =>
  }

  /**
   * This is the meat of the runner.
   * It takes a list of `() => Future[TestOutput]` and runs all of them in
   * sequence, taking cues from a passed `Config` value,
   * and returning whether any tests failed.
   */
  def configured(suites: List[() => Future[TestOutput]], config: Config, ec: ExecutionContext): Future[TestResult] = {
    val startTime = System.currentTimeMillis
    val run: Future[Boolean] = futureUtil.orIterator(suites.iterator.map { suite =>
      futureUtil.map(suite()) { r => r.print(); r.failed }(ec)
    })(ec)
    if (run.isCompleted) {
      // hot path: testing was fully synchronous,
      // so we don't need to submit to the ExecutionContext.
      val endTime = System.currentTimeMillis
      config.outputSuite(
        "Testing took " ::
        String.valueOf(endTime - startTime) ::
        "ms (synchronously)\n" ::
        Nil
      )
      Future.successful(new TestResult(run.value.get.get))
    } else {
      // slow path
      run.map { f =>
        val endTime = System.currentTimeMillis
        config.outputSuite(
          "Testing took " ::
          String.valueOf(endTime - startTime) ::
          "ms (asynchronously) \n" ::
          Nil
        )
        new TestResult(f)
      }(ec)
    }
  }

  def apply(suites: List[() => Future[TestOutput]], ec: ExecutionContext): Future[TestResult] =
    configured(suites, defaultConfig, ec)

  def printTest(scope: List[String], out: Result): List[String] = out match {
    case Succeed => Nil
    case _       => fastConcatDelim(new ::("failed\n", scope), "->")
  }

  def fastConcatDelim(strs: ::[String], delim: String): ::[String] = {
    if (strs.tail eq Nil) {
      strs
    } else {
      var newList: List[String] = Nil
      var cursor: List[String] = strs
      while (cursor ne Nil) {
        newList = cursor.head :: newList
        val tl = cursor.tail
        if (tl ne Nil) {
          newList = delim :: newList
        }
        cursor = cursor.tail
      }
      newList.asInstanceOf[::[String]]
    }
  }
}
