package com.geirolz.app.toolkit.error

import cats.Applicative
import com.geirolz.app.toolkit.|

trait ErrorLifter[F[_], E] {
  def lift[A](f: F[A]): F[E | A]
  def liftFunction[U, A](f: U => F[A]): U => F[E | A] = f.andThen(lift(_))
}
object ErrorLifter {

  type Resource[F[_], E] = ErrorLifter[cats.effect.Resource[F, *], E]

//  implicit def attempt[F[_]: ApplicativeError[*, E], E]: ErrorLifter[F, E] =
//    new ErrorLifter[F, E] {
//      override def lift[A](fa: F[A]): F[E | A] = ApplicativeError[F, E].attempt(fa)
//    }

  implicit def toRight[F[_]: Applicative, E]: ErrorLifter[F, E] = new ErrorLifter[F, E] {
    override def lift[A](fa: F[A]): F[E | A] = Applicative[F].map(fa)(Right(_))
  }
}
