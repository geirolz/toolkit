package com.geirolz.app.toolkit

import cats.{Applicative, ApplicativeError, Parallel, Semigroup, Show}
import cats.data.NonEmptyList
import cats.effect.{Async, Fiber, Ref, Resource}
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Resource.ExitCase
import com.geirolz.app.toolkit.logger.LoggerAdapter

trait App[F[+_], E, APP_INFO <: SimpleAppInfo[?], LOGGER_T[_[_]], CONFIG] {

  import cats.syntax.all.*
  import Resourced.Syntax.*

  // ------------------- DEFINITION ------------------
  type Resources = AppResources[APP_INFO, LOGGER_T[F], CONFIG]
  class Resourced[T] private (val resources: Resources, val value: T) {
    val info: APP_INFO      = resources.info
    val config: CONFIG      = resources.config
    val logger: LOGGER_T[F] = resources.logger

    def map[U](f: T => U): Resourced[U]         = f(value).resourced
    def tupled: (Resources, T)                  = Resourced.unapply(this)
    def useTupled[U](f: (Resources, T) => U): U = f.tupled(tupled)
    def use[U](f: Resources => T => U): U       = f(resources)(value)

    def tupledAll: (APP_INFO, CONFIG, LOGGER_T[F], T) = (info, config, logger, value)
    def useTupledAll[U](f: (APP_INFO, CONFIG, LOGGER_T[F], T) => U): U = f.tupled(tupledAll)
  }
  object Resourced {

    private[App] def apply[T](t: T): Resourced[T]   = new Resourced(resources, t)
    def unapply[T](r: Resourced[T]): (Resources, T) = (r.resources, r.value)

    implicit val applicative: Applicative[Resourced] = new Applicative[Resourced] {
      override def pure[A](x: A): Resourced[A] =
        x.resourced
      override def ap[A, B](ff: Resourced[A => B])(fa: Resourced[A]): Resourced[B] =
        ff.value(fa.value).resourced
    }

    object Syntax {
      private[App] implicit class AnyOps[T](any: T) {
        def resourced: Resourced[T] = Resourced(any)
      }
    }
  }

  val resources: Resources

  val logic: Resource[F, E \/ Unit]

  def flattenThrowLogic(implicit
    F: MonadCancel[F, Throwable],
    env: E <:< Throwable
  ): Resource[F, Unit] =
    logic.flatMap {
      case Left(error)  => Resource.eval(F.raiseError(error))
      case Right(value) => Resource.pure(value)
    }

  def flattenThrowNelLogic(implicit
    F: MonadCancel[F, Throwable],
    env: E <:< NonEmptyList[Throwable],
    semigroup: Semigroup[Throwable]
  ): Resource[F, Unit] =
    reduceFailures.flattenThrowLogic

  // ---------------------- RUN ----------------------
  final def run(implicit F: MonadCancel[F, Throwable], env: E <:< Throwable): F[Unit] =
    flattenThrowLogic.use_

  final def runE(implicit F: MonadCancel[F, Throwable]): F[E \/ Unit] =
    logic.use(_.pure[F])

  // -------------------- MAPPING --------------------
  final def logicMap[EE](
    f: Resourced[Resource[F, E \/ Unit]] => Resource[F, EE \/ Unit]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    App.of(resources, f(logic.resourced))

  final def map[EE](
    f: Resourced[E \/ Unit] => EE \/ Unit
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.map(v => f(v.resourced)))

  final def flatMap[EE](
    f: Resourced[E \/ Unit] => Resource[F, EE \/ Unit]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.flatMap(v => f(v.resourced)))

  final def evalMap[EE](
    f: Resourced[E \/ Unit] => F[EE \/ Unit]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.evalMap(v => f(v.resourced)))

  final def evalTap(
    f: Resourced[E \/ Unit] => F[Unit]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.evalTap(v => f(v.resourced)))

  final def preRun(
    f: Resources => F[Unit]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.preAllocate(f(resources)))

  final def onFinalize(f: Resources => F[Unit])(implicit
    F: Applicative[F]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.onFinalize(f(resources)))

  final def onFinalizeCase(f: Resourced[ExitCase] => F[Unit])(implicit
    F: Applicative[F]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.onFinalizeCase(ec => f(ec.resourced)))

  final def onCancel(f: Resources => F[Unit])(implicit
    F: Applicative[F]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    onFinalizeCase { r =>
      r.value match {
        case ExitCase.Canceled => f(r.resources)
        case _                 => F.unit
      }
    }

  // -------------------- FAILURE --------------------
  final def takeFirstFailure[EE](implicit
    env: E <:< NonEmptyList[EE]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    failureMap(_.value.head)

  final def reduceFailures[EE](implicit
    env: E <:< NonEmptyList[EE],
    semigroup: Semigroup[EE]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    failureMap(_.value.reduce)

  final def onFailure(f: Resourced[E] => F[Unit])(implicit
    F: Applicative[F]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    failureEvalTap(f)

  final def failureMap[EE](f: Resourced[E] => EE): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    map(_.value.leftMap(e => f(e.resourced)))

  final def failureEvalMap[EE](
    f: Resourced[E] => F[EE]
  )(implicit F: Applicative[F]): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    evalMap {
      _.value match {
        case Left(e)      => f(e.resourced).map(Left(_))
        case Right(value) => Right(value).pure[F]
      }
    }

  final def failureEvalTap(f: Resourced[E] => F[Unit])(implicit
    F: Applicative[F]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    failureEvalMap(resed => f(resed).as(resed.value))

  final def recoverFailureWith(
    f: Resourced[E] => F[Unit]
  )(implicit F: Applicative[F]): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    evalMap {
      _.value match {
        case Left(e)      => f(e.resourced).map(Right(_))
        case Right(value) => Right(value).pure[F]
      }
    }

  // -------------------- ERROR --------------------
  final def onError(f: Resourced[Throwable] => F[Unit])(implicit
    F: Applicative[F]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    onFinalizeCase {
      _.value match {
        case ExitCase.Errored(e) => f(e.resourced)
        case _                   => F.unit
      }
    }

  final def handleErrorWith(
    f: Resourced[Throwable] => F[E \/ Unit]
  )(implicit
    F: ApplicativeError[F, Throwable]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(
      _.value.handleErrorWith[E \/ Unit, Throwable](e => Resource.eval(f(e.resourced)))
    )
}
object App {

  def fromServices[F[+_]: Async: Parallel, E, APP_INFO <: SimpleAppInfo[?], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show](
    appResources: AppResources[APP_INFO, LOGGER_T[F], CONFIG],
    appProvServices: List[F[E \/ Any]],
    onFailure: E => OnFailure
  ): App[F, NonEmptyList[E], APP_INFO, LOGGER_T, CONFIG] = {

    import cats.effect.syntax.all.*
    import cats.syntax.all.*

    val toolkitLogger = LoggerAdapter[LOGGER_T].toToolkit[F](appResources.logger)
    val logic: Resource[F, NonEmptyList[E] \/ Unit] = {
      val info = appResources.info
      val app: F[NonEmptyList[E] \/ Unit] =
        for {
          fibers   <- Ref[F].of(List.empty[Fiber[F, Throwable, Unit]])
          failures <- Ref[F].of(List.empty[E])
          onFailureTask: (E => F[Unit]) =
            onFailure(_) match {
              case OnFailure.CancelAll =>
                fibers.get.flatMap(_.parTraverse(_.cancel.start).void)
              case OnFailure.DoNothing =>
                Async[F].unit
            }

          services = appProvServices.map(_.flatTap {
            case Left(failure) => failures.update(_ :+ failure) >> onFailureTask(failure)
            case Right(_)      => Async[F].unit
          })
          _ <- services.parTraverse(t => t.void.start.flatMap(f => fibers.update(_ :+ f)))
          _ <- fibers.get.flatMap(_.parTraverse(_.joinWithUnit))
          maybeReducedFailures <- failures.get.map(NonEmptyList.fromList(_))
        } yield maybeReducedFailures.toLeft(())

      Resource
        .eval[F, NonEmptyList[E] \/ Unit](
          toolkitLogger.info(s"Starting ${info.buildRefName}...") >>
            app
              .onCancel(toolkitLogger.info(s"${info.name} was stopped."))
              .onError(e => toolkitLogger.error(e)(s"${info.name} was stopped due an error."))
        )
        .onFinalize(toolkitLogger.info(s"Shutting down ${info.name}..."))
    }

    App.of(appResources, logic)
  }

  def of[F[+_], E, APP_INFO <: SimpleAppInfo[?], LOGGER_T[_[_]], CONFIG](
    appResources: AppResources[APP_INFO, LOGGER_T[F], CONFIG],
    appLogic: Resource[F, E \/ Unit]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] = new App[F, E, APP_INFO, LOGGER_T, CONFIG] {
    override val resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG] = appResources
    override val logic: Resource[F, E \/ Unit]                          = appLogic
  }
}
