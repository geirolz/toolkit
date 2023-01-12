package com.geirolz.app.toolkit

import cats.effect.{MonadCancelThrow, Resource}
import cats.effect.kernel.Spawn

trait AppServiceRunner[F[_]] {
  def run(service: Resource[F, ?]): F[Any]
}
object AppServiceRunner {

  def instance[F[+_], T](f: Resource[F, ?] => F[T]): AppServiceRunner[F] =
    (service: Resource[F, ?]) => f(service)

  def run_[F[+_]: MonadCancelThrow]: AppServiceRunner[F] = instance[F, Unit](_.use_)

  def runForever[F[+_]: Spawn]: AppServiceRunner[F] = instance[F, Nothing](_.useForever)
}
