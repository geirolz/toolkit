package com.geirolz.app.toolkit.testing

import cats.effect.{Ref, Resource}
import cats.effect.kernel.MonadCancelThrow
import cats.Functor

class EventLogger[F[_]](ref: Ref[F, List[Event]]) {

  def events: F[List[Event]] = ref.get

  def append(event: Event): F[Unit] =
    ref.update(_ :+ event)
}
object EventLogger {

  import cats.syntax.all.*

  def apply[F[_]: EventLogger]: EventLogger[F] = implicitly[EventLogger[F]]

  def create[F[_]: Ref.Make: Functor]: F[EventLogger[F]] = {
    Ref.of(List.empty[Event]).map(new EventLogger(_))
  }

  implicit class appLoaderResOps[F[+_]: MonadCancelThrow: EventLogger](compiledApp: Resource[F, F[Unit]]) {

    def traceAsAppLoader: Resource[F, F[Unit]] =
      compiledApp.trace(LabeledResource.appLoader)

    def runFullTracedApp: F[Unit] =
      compiledApp.traceAsAppLoader
        .use(_.traceAsAppRuntime.pure[F])
        .flatten
  }

  implicit class appRuntimeResOps[F[_]: MonadCancelThrow: EventLogger](app: F[Unit]) {
    def traceAsAppRuntime: F[Unit] =
      Resource.eval(app).trace(LabeledResource.appRuntime).use_
  }

  implicit class genericResOps[F[_]: MonadCancelThrow: EventLogger, T](resource: Resource[F, T]) {

    def trace(labeledResource: LabeledResource): Resource[F, T] = {
      val logger = EventLogger[F]
      resource
        .preAllocate(logger.append(labeledResource.starting))
        .onFinalize(logger.append(labeledResource.finalized))
        .flatTap(_ => Resource.eval(logger.append(labeledResource.succeeded)))
        .onError(e => Resource.eval(logger.append(labeledResource.errored(e.getMessage))))
        .onCancel(Resource.eval(logger.append(labeledResource.canceled)))
    }
  }
}
