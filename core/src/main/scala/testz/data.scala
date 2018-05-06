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

package testz

trait Test[F[_], T] {
  def apply(name: String)(assertions: F[List[TestError]]): T
  def section(name: String)(test1: T, tests: T*): T
}

sealed trait TestError
final case class ExceptionThrown(thrown: Throwable) extends TestError
final case class Failure(failureAsString: String) extends TestError
// TODO: use pretty-printer