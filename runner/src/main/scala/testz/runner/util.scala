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

import java.lang.StringBuilder

private[testz] object util {
  def fastConcat(strs: List[String]): String = strs match {
    case ss: ::[String] => fastConcatDelim(ss, "")
    case _: Nil.type => ""
  }

  def fastConcatDelim(strs: ::[String], delim: String): String = {
    var totalLength = 0
    var numStrs = 0
    var cursor: List[String] = strs
    while (!cursor.isInstanceOf[Nil.type]) {
      val strss = cursor.asInstanceOf[::[String]]
      totalLength = totalLength + strss.head.length
      numStrs = numStrs + 1
      cursor = strss.tail
    }
    val sb = new StringBuilder(totalLength + (numStrs * delim.length) + 5)
    cursor = strs
    while (!cursor.isInstanceOf[Nil.type]) {
      val strss = cursor.asInstanceOf[::[String]]
      sb.append(strss.head)
      if (!strss.tail.isInstanceOf[Nil.type])
        sb.append(delim)
      cursor = strss.tail
    }

    sb.toString
  }
}