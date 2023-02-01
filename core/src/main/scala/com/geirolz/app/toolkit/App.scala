package com.geirolz.app.toolkit

import cats.{Applicative, ApplicativeError, Parallel, Show}
import cats.effect.{Async, Resource}
import cats.effect.implicits.monadCancelOps_
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Resource.ExitCase
import com.geirolz.app.toolkit.logger.LoggerAdapter

trait App[F[+_], E, APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]], CONFIG] {

  import cats.syntax.all.*
  import Resourced.Syntax.*

  // ------------------- DEFINITION ------------------
  type Resources = AppResources[APP_INFO, LOGGER_T[F], CONFIG]
  class Resourced[T] private (val resources: Resources, val value: T) {
    val info: APP_INFO      = resources.info
    val config: CONFIG      = resources.config
    val logger: LOGGER_T[F] = resources.logger
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

  val logic: Resource[F, Either[E, Unit]]

  def flattenLogic(implicit F: MonadCancel[F, Throwable], env: E <:< Throwable): Resource[F, Unit] =
    logic.flatMap {
      case Left(left)   => Resource.eval(F.raiseError(left))
      case Right(value) => Resource.pure(value)
    }

  // ---------------------- RUN ----------------------
  final def run(implicit F: MonadCancel[F, Throwable], env: E <:< Throwable): F[Unit] =
    flattenLogic.use_

  // -------------------- MAPPING --------------------
  final def logicMap[EE](
    f: Resourced[Resource[F, Either[E, Unit]]] => Resource[F, Either[EE, Unit]]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    App.of(resources, f(logic.resourced))

  final def map[EE](
    f: Resourced[Either[E, Unit]] => Either[EE, Unit]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.map(v => f(v.resourced)))

  final def flatMap[EE](
    f: Resourced[Either[E, Unit]] => Resource[F, Either[EE, Unit]]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.flatMap(v => f(v.resourced)))

  final def evalMap[EE](
    f: Resourced[Either[E, Unit]] => F[Either[EE, Unit]]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.evalMap(v => f(v.resourced)))

  final def evalTap(
    f: Resourced[Either[E, Unit]] => F[Unit]
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
    f: (Resources, Throwable) => F[Either[E, Unit]]
  )(implicit
    F: ApplicativeError[F, Throwable]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(
      _.value.handleErrorWith[Either[E, Unit], Throwable](e => Resource.eval(f(resources, e)))
    )
}
object App {

  import cats.syntax.all.*

  type Throw[F[+_], APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]], CONFIG] =
    App[F, Throwable, APP_INFO, LOGGER_T, CONFIG]

  def apply[F[+_]: Async: Parallel, E]: AppBuilderRuntimeSelected[F, E] =
    new AppBuilderRuntimeSelected[F, E]

  def apply[F[+_]: Async: Parallel](implicit
    di: DummyImplicit
  ): AppBuilderRuntimeSelected[F, Throwable] =
    App[F, Throwable]

  final class AppBuilderRuntimeSelected[F[+_]: Async: Parallel, E] {

    def withResources[APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG: Show](
      resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG]
    ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, Unit] =
      withResourcesLoader(AppResources.pureLoader(resources))

    def withResourcesLoader[APP_INFO <: BasicAppInfo[?], LOGGER_T[
      _[_]
    ]: LoggerAdapter, CONFIG: Show](
      resourcesLoader: AppResources.Loader[F, APP_INFO, LOGGER_T, CONFIG]
    ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, Unit] =
      new AppBuilder(
        resourcesLoader,
        _ => Resource.unit[F]
      )
  }

  /** @param resourcesLoader
    *   App resources loader instance
    * @param dependenciesBuilder
    *   Function which builds app dependencies
    * @tparam F
    *   App effect type
    * @tparam E
    *   App failures type
    * @tparam APP_INFO
    *   App information type
    * @tparam LOGGER_T
    *   App logger type
    * @tparam CONFIG
    *   App configuration type
    * @tparam DEPENDENCIES
    *   App dependencies type
    */
  class AppBuilder[F[+_]: Async: Parallel, E, APP_INFO <: BasicAppInfo[?], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, DEPENDENCIES](
    resourcesLoader: AppResources.Loader[F, APP_INFO, LOGGER_T, CONFIG],
    dependenciesBuilder: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, DEPENDENCIES]
  ) { $this =>

    def dependsOn[DEP_2](
      dependenciesBuilder: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, DEP_2]
    ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEP_2] =
      new AppBuilder(
        resourcesLoader     = $this.resourcesLoader,
        dependenciesBuilder = dependenciesBuilder
      )

    def provideOne(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[Any]
    ): Resource[F, App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      provide(deps => List(f(deps)))

    def provide(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[Any]]
    ): Resource[F, App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      provideF(deps => f(deps).pure[F])

    def provideF(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[Any]]]
    ): Resource[F, App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      for {
        // -------------------- RESOURCES -------------------
        appResources <- Resource.eval(resourcesLoader.load)
        resLogger = LoggerAdapter[LOGGER_T].toToolkit(appResources.logger).mapK(Resource.liftK[F])

        // ------------------- DEPENDENCIES -----------------
        _              <- resLogger.info("Building services environment...")
        appDepServices <- dependenciesBuilder(appResources)
        _              <- resLogger.info("Services environment successfully built.")

        // --------------------- SERVICES -------------------
        _ <- resLogger.info("Building App...")
        appProvServices <- Resource.eval(
          f(AppDependencies(appResources, appDepServices))
        )
        _ <- resLogger.info("App successfully built.")
      } yield App.fromServices(appResources, appProvServices)
  }

  def fromServices[F[+_]: Async: Parallel, E, APP_INFO <: BasicAppInfo[?], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show](
    appResources: AppResources[APP_INFO, LOGGER_T[F], CONFIG],
    appProvServices: List[F[Any]]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] = {
    val toolkitLogger = LoggerAdapter[LOGGER_T].toToolkit[F](appResources.logger)
    val logic: Resource[F, Either[E, Unit]] = {
      val info = appResources.info
      Resource
        .eval[F, Either[E, Unit]](
          toolkitLogger.info(s"Starting ${info.buildRefName}...") >>
            appProvServices
              .parTraverse[F, Any](identity)
              .onCancel(toolkitLogger.info(s"${info.name} was stopped."))
              .onError(e => toolkitLogger.error(e)(s"${info.name} was stopped due an error."))
              .void
              .map(Right(_))
        )
        .onFinalize(toolkitLogger.info(s"Shutting down ${info.name}..."))
    }

    App.of(appResources, logic)
  }

  def of[F[+_], E, APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]], CONFIG](
    appResources: AppResources[APP_INFO, LOGGER_T[F], CONFIG],
    appLogic: Resource[F, Either[E, Unit]]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] = new App[F, E, APP_INFO, LOGGER_T, CONFIG] {
    override val resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG] = appResources
    override val logic: Resource[F, Either[E, Unit]]                    = appLogic
  }
}
