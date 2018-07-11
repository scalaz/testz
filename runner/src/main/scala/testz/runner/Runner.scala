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
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object Runner {
  /**
   * Configuration.
   * Forwards-compatible by construction.
   */
  final class Config private[Runner](private val _chunkSize: Int, _output: String => Unit) {
    def withChunkSize(newChunkSize: Int) = new Config(newChunkSize, _output)
    def withOutput(newOutput: String => Unit) = new Config(_chunkSize, newOutput)
    def chunkSize: Int = _chunkSize
    def output: String => Unit = _output
  }

  def bufferedStdOut(bufferSize: Int): (String => Unit, () => Unit) = {
    val out = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), bufferSize)
    // System.setOut(null)
    (str =>
      if (!str.isEmpty) {
        out.write(str.getBytes(StandardCharsets.UTF_8), 0, str.length)
      }, () => out.flush())
  }

  val defaultChunkSize = 100
  val defaultOutput = Console.print(_: String)

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
    new Config(_chunkSize = defaultChunkSize, _output = defaultOutput)

  @scala.annotation.tailrec
  private def printStrs(strs: List[String], output: String => Unit): Unit = strs match {
    case x :: xs => output(x); printStrs(xs, output)
    case _ =>
  }
  @scala.annotation.tailrec
  private def printStrss(strs: List[List[String]], output: String => Unit): Unit = strs match {
    case xs: ::[List[String]] =>
      if (xs.head.nonEmpty) {
        printStrs(xs.head, output)
        output("\n")
      }
      printStrss(xs.tail, output)
    case _ =>
  }

  /**
   * This is the meat of the runner.
   * It takes a list of `() => Suite` and runs all of them in
   * sequence, taking cues from a passed `Config` value.
   */
  def configured(suites: List[() => Suite], config: Config, ec: ExecutionContext): Future[Unit] = {
    val startTime = System.currentTimeMillis
    val run = traverseFutureIterator(suites.iterator) { suite =>
      val runSuite = suite().run(ec)
      runSuite.value match {
        case Some(Success(a)) =>
          printStrs(a, config.output)
          Future.unit
        case Some(Failure(e)) =>
          Future.failed(e)
        case None =>
          runSuite.map(printStrs(_, config.output))(ec)
      }
    }(ec)
    if (run.isCompleted) {
      // hot path: testing was fully synchronous,
      // so we don't need to submit to the ExecutionContext.
      val endTime = System.currentTimeMillis
      config.output("Testing took " + String.valueOf(endTime - startTime) + "ms (synchronously)\n")
      Future.unit
    } else {
      // slow path
      run.map { _ =>
        val endTime = System.currentTimeMillis
        config.output("Testing took " + String.valueOf(endTime - startTime) + "ms (asynchronously) \n")
      }(ec)
    }
  }

  def traverseFutureIterator[A, B](fa: Iterator[A])(f: A => Future[B])(ec: ExecutionContext): Future[List[B]] =
    if (!fa.hasNext) Future.successful(Nil)
    else {
      val lb = new scala.collection.mutable.ListBuffer[B]
      val mapped = fa.map(f)
      val outProm = Promise[List[B]]
      val run = sequenceFutureIterator(mapped, mapped.next, lb)(ec)
      if (run.isCompleted) {
        outProm.success(lb.result())
      } else {
        run.onComplete(_ => outProm.success(lb.result()))(ec)
      }
      outProm.future
    }

  def sequenceFutureIterator[A, B]
    (fa: Iterator[Future[A]], next: Future[A], lb: scala.collection.mutable.ListBuffer[A])(ec: ExecutionContext)
  : Future[Unit] = {
    @scala.annotation.tailrec def inner(next: Future[A], lb: scala.collection.mutable.ListBuffer[A]): Future[Unit] =
      next.value match {
        // hot path: no need to suspend to an EC.
        case Some(Success(a)) =>
          lb += a
          if (fa.hasNext) inner(fa.next, lb)
          else Future.unit
        case Some(Failure(e)) => Future.failed(e)
        // slow path: `next` wasn't synchronous, so we suspend to an EC.
        case None => next.flatMap { a =>
          lb += a
          if (fa.hasNext) sequenceFutureIterator(fa, fa.next, lb)(ec)
          else Future.unit
        }(ec)
      }
    inner(next, lb)
  }

  def apply(suites: List[() => Suite], ec: ExecutionContext): Future[Unit] =
    configured(suites, defaultConfig, ec)

}
