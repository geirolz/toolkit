package com.geirolz.app.toolkit.testing

import cats.effect.{Ref, Resource}
import cats.effect.kernel.MonadCancelThrow
import cats.Functor
import com.geirolz.app.toolkit.{App, SimpleAppInfo}

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

  implicit class appLoaderResOps[F[+_]: MonadCancelThrow: EventLogger, E, LOGGER_T[
    _[_]
  ], APP_INFO <: SimpleAppInfo[
    ?
  ], CONFIG](
    resource: Resource[F, App[F, E, APP_INFO, LOGGER_T, CONFIG]]
  ) {
    def traceAsAppLoader: Resource[F, App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      resource.trace(LabeledResource.appLoader)
  }

  implicit class appRuntimeResOps[F[_]: MonadCancelThrow: EventLogger](
    resource: Resource[F, Unit]
  ) {
    def traceAsAppRuntime: Resource[F, Unit] =
      resource.trace(LabeledResource.appRuntime)
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
