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

import scala.concurrent.{ExecutionContext, Future}

object runner {
  /**
  * Something you can use to represent the intermediate output
  * of a typical `Harness`; a failure state and a side effect
  * which prints all results.
  */
  final class TestOutput(
    val failed: Boolean,
    val print: () => Unit
  )

  object TestOutput {
    // The `mappend` operation for the `Monoid` of `TestOutput`s.
    // If either fails, the result fails.
    def combine(fst: TestOutput, snd: TestOutput) =
      new TestOutput(
        fst.failed || snd.failed,
        { () => fst.print(); snd.print() }
      )

    // Combines 1 or more `TestOutput`s, using logarithmic stack depth in the number of
    // tests unlike `combine` which would be linear.
    def combineAll1(output1: TestOutput, outputs: TestOutput*) = {
      val anyFailed = output1.failed || outputs.exists(_.failed)
      new TestOutput(
        anyFailed,
        { () => output1.print(); outputs.foreach(_.print()) }
      )
    }
  }

  /**
   * Returned by `runner.apply` - after all is said and done,
   * tests run and output printed, did any fail?
   * Useful for exit status; I often check `failed` and throw an exception
   * in `main` if it's `true`.
   */
  final class TestResult(val failed: Boolean)

  /**
   * The meat of the runner.
   * Takes a list of `() => Future[TestOutput]` and runs all of them in sequence.
   * Then, prints out how long the suites took to run, using the user-supplied printer.
   * Returns whether any tests failed.
   */
  def apply(suites: List[() => Future[TestOutput]], printer: String => Unit, ec: ExecutionContext): Future[TestResult] = {
    val startTime = System.currentTimeMillis
    val run: Future[Boolean] = futureUtil.orIterator(suites.iterator.map { suite =>
      futureUtil.map(suite()) { r => r.print(); r.failed }(ec)
    })(ec)
    futureUtil.map(run) { f =>
      val endTime = System.currentTimeMillis
      printer(
        "Testing took " +
        String.valueOf(endTime - startTime) +
        "ms.\n"
      )
      new TestResult(f)
    }(ec)
  }

  // Cached for performance.
  private val newlineSingleton =
    "\n" :: Nil

  /**
   * These four functions are just utility methods for users to write fast
   * test result printers.
   */
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

  def intersperseReverse(strs: ::[String], delim: String): ::[String] = {
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

  // Note that tests which succeed never have results printed
  // (if you use this function)
  def printTest(out: Result, scope: List[String]): List[String] = out match {
    case _: Succeed => Nil
    case _          => intersperseReverse(new ::("failed\n", scope), "->")
  }

}
