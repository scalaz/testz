package testz
package resource

abstract class RTest[R, T[_]] { self =>
  def apply[Resource](name: String)(assertions: Resource => R): T[Resource]

  def contramap[S](f: S => R): RTest[S, T] =
    new RTest[S, T] {
      def apply[Resource](name: String)(assertions: Resource => S): T[Resource] =
        self[Resource](name)(res => f(assertions(res)))
    }

  def map[U[_]](f: T NT U): RTest[R, U] =
    new RTest[R, U] {
      def apply[Resource](name: String)(assertions: Resource => R): U[Resource] =
        f(self[Resource](name)(assertions))
    }

  def toTest[Resource]: Test[R, T[Resource]] = new Test[R, T[Resource]] {
    def apply(name: String)(assertions: () => R): T[Resource] =
      self[Resource](name)(_ => assertions())
  }
}

abstract class RSection[T[_]] { self =>
  def apply[Resource](t1: T[Resource], ts: T[Resource]*): T[Resource]
  def named[Resource](name: String)(t1: T[Resource], ts: T[Resource]*): T[Resource]
  def toSection[Resource]: Section[T[Resource]] = new Section[T[Resource]] {
    def apply(t1: T[Resource], ts: T[Resource]*): T[Resource] = self[Resource](t1, ts: _*)
    def named(name: String)(t1: T[Resource], ts: T[Resource]*): T[Resource] = self.named[Resource](name)(t1, ts: _*)
  }
}

abstract class Allocate[T[_]] {
  def apply[R, I]
    (init: () => I)
    (tests: T[(I, R)]): T[R]
}

abstract class Bracket[T[_], F[_]] { self =>
  def apply[R, I]
    (init: () => F[I])
    (cleanup: I => F[Unit])
    (tests: T[(I, R)]): T[R]

  def contramap[G[_]](tr: G NT F): Bracket[T, G] =
    new Bracket[T, G] {
      def apply[R, I]
        (init: () => G[I])
        (cleanup: I => G[Unit])
        (tests: T[(I, R)]): T[R] =
        self(
          () => tr(init())
        )(
          i => tr(cleanup(i))
        )(tests)
    }

  def toAllocate(pure: Id NT F): Allocate[T] =
    new Allocate[T] {
      def apply[R, I]
        (init: () => I)
        (tests: T[(I, R)]): T[R] =
        self(() => pure(init()))(_ => pure(()))(tests)
    }
}
