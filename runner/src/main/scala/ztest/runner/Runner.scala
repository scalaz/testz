/*
 * Copyright 2018 Edmund Noble
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package testz.runner

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

  val defaultConfig: Config = new Config(_chunkSize = 100, _output = println(_))

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

  def configured(suites: List[() => Suite], config: Config)(implicit ec: ExecutionContext): Future[Unit] = Future {
    import config._
    val startTime = System.currentTimeMillis
    Future.traverse(suites.grouped(chunkSize).toList) { chunk =>
      Future.traverse(chunk)(_().run).map(printStrss(_, config.output))
    }.map { r =>
      val endTime = System.currentTimeMillis
      config.output(s"Testing took ${endTime - startTime} ms")
    }
  }.flatten

  def apply(suites: List[() => Suite])(implicit ec: ExecutionContext): Future[Unit] =
    configured(suites, defaultConfig)

}