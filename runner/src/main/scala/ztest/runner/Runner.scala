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
  final case class Config(chunkSize: Int)

  @scala.annotation.tailrec
  private def printStrs(strs: List[String]): Unit = strs match {
    case x :: xs => print(x); printStrs(xs)
    case _ =>
  }
  @scala.annotation.tailrec
  private def printStrss(strs: List[List[String]]): Unit = strs match {
    case x :: xs => printStrs(x); print("\n"); printStrss(xs)
    case _ =>
  }

  val defaultConfig: Config = Config(chunkSize = 100)

  def configured(suites: List[Suite], config: Config)(implicit ec: ExecutionContext): Future[Unit] = Future {
    import config._
    Future.traverse(suites.grouped(chunkSize)) { chunk =>
      Future.traverse(chunk)(_.run).map(printStrss)
    }.map(_ => ())
  }.flatten

  def apply(suites: List[Suite])(implicit ec: ExecutionContext): Future[Unit] =
    configured(suites, defaultConfig)

}