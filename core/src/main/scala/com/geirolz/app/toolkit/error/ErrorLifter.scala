package com.geirolz.app.toolkit.error

import cats.{effect, Applicative}
import cats.effect.Resource
import com.geirolz.app.toolkit.|

trait ErrorLifter[F[_], E] { self =>

  def lift[A](f: F[A]): F[E | A]

  def liftResourceFunction[U, A](f: U => Resource[F, A]): U => Resource[F, E | A]

  final def liftFunction[U, A](f: U => F[A]): U => F[E | A] =
    f.andThen(lift(_))

}
object ErrorLifter {

  type Resource[F[_], E] = ErrorLifter[cats.effect.Resource[F, *], E]

  implicit def toRight[F[_]: Applicative, E]: ErrorLifter[F, E] = new ErrorLifter[F, E] {
    override def lift[A](fa: F[A]): F[E | A] = Applicative[F].map(fa)(Right(_))

    override def liftResourceFunction[U, A](
      f: U => effect.Resource[F, A]
    ): U => effect.Resource[F, E | A] =
      f.andThen(_.evalMap(a => lift(Applicative[F].pure(a))))
  }
}
