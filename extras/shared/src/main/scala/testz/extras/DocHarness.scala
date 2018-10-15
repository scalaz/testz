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
package extras

import resource._

import scala.collection.mutable.ListBuffer

object DocHarness {
  type Uses[R] = (String, ListBuffer[String]) => Unit

  def test[R]: Test[R, Uses[Unit]] =
    rTest[R].toTest[Unit]

  def rTest[R]: RTest[R, Uses] = new RTest[R, Uses] {
    def apply[Resource]
      (name: String)
      (assertions: Resource => R)
      : Uses[Resource] =
        (indent, buf) => buf += (indent + "  " + name)
  }

  val rSection: RSection[Uses] = new RSection[Uses] {
    def named[Resource](
      name: String
    )(
      test1: Uses[Resource],
      tests: Uses[Resource]*
    ): Uses[Resource] = {
      (indent, buf) =>
        val newIndent = indent + "  "
        buf += (newIndent + "[" + name + "]")
        test1(newIndent, buf)
        tests.foreach(_(newIndent, buf))
    }

    def apply[Resource](
      test1: Uses[Resource],
      tests: Uses[Resource]*
    ): Uses[Resource] = {
      (indent, buf) =>
        val newIndent = indent + "  "
        test1(newIndent, buf)
        tests.foreach(_(newIndent, buf))
    }
  }

  val section: Section[Uses[Unit]] =
    rSection.toSection[Unit]

  def bracket[F[_]]: Bracket[Uses, F] = new Bracket[Uses, F] {
    def apply[Resource, I]
      (init: () => F[I])
      (cleanup: I => F[Unit])
      (tests: Uses[(I, Resource)]
    ): Uses[Resource] = tests
  }
}
