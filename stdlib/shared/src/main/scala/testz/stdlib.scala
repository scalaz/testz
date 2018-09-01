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

import runner.TestOutput

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

object PureHarness {
  type Uses[R] = (R, List[String]) => TestOutput

  def makeFromPrinter(
    output: (Result, List[String]) => Unit
  ): Harness[Uses[Unit]] =
    ResourceHarness.toHarness(makeFromPrinterR(output))

  def makeFromPrinterR(
    output: (Result, List[String]) => Unit
  ): ResourceHarness[Uses] =
    new ResourceHarness[Uses] {
      override def test[R]
        (name: String)
        (assertions: R => Result
      ): Uses[R] =
        // note that `assertions(r)` is *already computed* before the
        // `() => Unit` is run; this is important to separate phases between
        // printing and running tests.
        { (resource, scope) =>
          val result = assertions(resource)
          new TestOutput(
            result ne Succeed(),
            () => output(result, name :: scope)
          )
        }

      override def namedSection[R]
        (name: String)
        (test1: Uses[R], tests: Uses[R]*
      ): Uses[R] = {
        (r, sc) =>
          val newScope = name :: sc
          val outFirst = test1(r, newScope)
          val outRest = tests.map(_(r, newScope))
          TestOutput.combineAll1(outFirst, outRest: _*)
      }

      override def section[R]
        (test1: Uses[R], tests: Uses[R]*
      ): Uses[R] = {
        (r, sc) =>
          val outFirst = test1(r, sc)
          val outRest = tests.map(_(r, sc))
          TestOutput.combineAll1(outFirst, outRest: _*)
      }

      override def allocate[R, I]
        (init: () => I)
        (tests: ((I, R), List[String]) => TestOutput
      ): Uses[R] =
        (r, sc) => tests((init(), r), sc)
    }

}

object FutureHarness {

  type Uses[R] = (R, List[String]) => Future[TestOutput]

  def makeFromPrinterEff(
    output: (Result, List[String]) => Unit
  )(
    ec: ExecutionContext
  ): EffectHarness[Future, Uses[Unit]] =
    EffectResourceHarness.toEffectHarness(makeFromPrinterEffR(output)(ec))

  def makeFromPrinterEffR(
    outputTest: (Result, List[String]) => Unit,
  )(
    ec: ExecutionContext
  ): EffectResourceHarness[Future, Uses] =
    new EffectResourceHarness[Future, Uses] {
      // note that `assertions(r)` is *already computed* before we run
      // the `() => Unit`.
      def test[R](name: String)(assertions: R => Future[Result]): Uses[R] =
        (r, sc) => assertions(r).map { result =>
          new TestOutput(result ne Succeed(), () => outputTest(result, name :: sc))
        }(ec)

      def namedSection[R](name: String)(test1: Uses[R], tests: Uses[R]*): Uses[R] = {
        (r, sc) =>
          val newScope = name :: sc
          test1(r, newScope).flatMap { p1 =>
            futureUtil.collectIterator(tests.iterator.map(_(r, newScope)))(ec).map { ps =>
              TestOutput.combineAll1(p1, ps: _*)
            }(ec)
          }(ec)
      }

      def section[R](test1: Uses[R], tests: Uses[R]*): Uses[R] = {
        (r, sc) =>
          test1(r, sc).flatMap { p1 =>
            futureUtil.collectIterator(tests.iterator.map(_(r, sc)))(ec).map { ps =>
              TestOutput.combineAll1(p1, ps: _*)
            }(ec)
          }(ec)
      }

      // a more powerful version of `Future.transform` that lets you block on
      // whatever you make from the inner `Try[A]`, instead of only letting you
      // return a `Try`.
      // relative monad operation (`rflatMap :: f a -> (g a -> f b) -> f b`)
      private def blockingTransform[A, B](fut: Future[A])(f: Try[A] => Future[B])(ec: ExecutionContext): Future[B] = {
        val prom = Promise[B]
        fut.onComplete {
          t => prom.completeWith(f(t))
        }(ec)
        prom.future
      }

      private def fromTry[A](t: Try[A]): Future[A] = {
        if (t.isInstanceOf[scala.util.Failure[A]])
          Future.failed(t.asInstanceOf[scala.util.Failure[A]].exception)
        else
          Future.successful(t.asInstanceOf[scala.util.Success[A]].value)
      }

      def bracket[R, I]
        (init: () => Future[I])
        (cleanup: I => Future[Unit])
        (tests: Uses[(I, R)]
      ): Uses[R] = { (r, sc) =>
        init().flatMap { i =>
          blockingTransform(
            tests((i, r), sc)
          )(r =>
            cleanup(i).flatMap(_ =>
              fromTry(r)
            )(ec)
          )(ec)
        }(ec)
      }
    }
}
