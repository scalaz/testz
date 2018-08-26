package testz

// a `Harness` where tests are able to access and allocate
// some kind of resource `R`, and they have no effects.
abstract class ResourceHarness[T[_]] { self =>
  def test[R](name: String)(assertions: R => Result): T[R]
  def section[R](name: String)(test1: T[R], tests: T[R]*): T[R]
  def allocate[R, I]
    (init: () => I)
    (tests: T[(I, R)]): T[R]
}

object ResourceHarness {
  // most harness types should have a `toHarness` like this,
  // a lot of tests can be written in terms of a `Harness` and
  // used in any other context.
  def toHarness[T[_], R](self: ResourceHarness[T]): Harness[T[R]] =
    new Harness[T[R]] {
      def test
        (name: String)
        (assertions: () => Result)
        : T[R] =
          self.test[R](name)(_ => assertions())

      def section
        (name: String)
        (test1: T[R], tests: T[R]*)
        : T[R] =
          self.section(name)(test1, tests: _*)
    }
}

/**
  Like `ResourceHarness`, but allowing tests to have `Result`s
  in `F[_]` (an effect)
 */
trait EffectResourceHarness[F[_], T[_]] { self =>
  def test[R]
    (name: String)
    (assertions: R => F[Result]): T[R]

  def section[R]
    (name: String)
    (test1: T[R], tests: T[R]*
  ): T[R]

  def bracket[R, I]
    (init: () => F[I])
    (cleanup: I => F[Unit])
    (tests: T[(I, R)]
  ): T[R]
}

object EffectResourceHarness {
  def toEffectHarness[T[_], F[_], R](
    self: EffectResourceHarness[F, T]
  ): EffectHarness[F, T[R]] = new EffectHarness[F, T[R]] {
    def test
      (name: String)
      (assertions: () => F[Result])
      : T[R] =
        self.test[R](name)(_ => assertions())

    def section
      (name: String)
      (test1: T[R], tests: T[R]*)
      : T[R] =
        self.section(name)(test1, tests: _*)
  }
}
