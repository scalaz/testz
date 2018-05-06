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