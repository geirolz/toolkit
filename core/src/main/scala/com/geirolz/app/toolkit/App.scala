package com.geirolz.app.toolkit

import cats.{Applicative, ApplicativeError, Parallel, Show}
import cats.data.{EitherT, NonEmptyList}
import cats.effect.{Async, Fiber, Ref, Resource, Sync}
import cats.effect.kernel.{MonadCancel, Outcome}
import cats.effect.kernel.Resource.ExitCase
import cats.kernel.Semigroup
import com.geirolz.app.toolkit.error.ErrorLifter
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

  val logic: Resource[F, E | Unit]

  def flattenThrowLogic(implicit
    F: MonadCancel[F, Throwable],
    env: E <:< Throwable
  ): Resource[F, Unit] =
    logic.flatMap {
      case Left(left)   => Resource.eval(F.raiseError(left))
      case Right(value) => Resource.pure(value)
    }

  // ---------------------- RUN ----------------------
  final def run(implicit F: MonadCancel[F, Throwable], env: E <:< Throwable): F[Unit] =
    flattenThrowLogic.use_

  final def runE(implicit F: MonadCancel[F, Throwable]): F[E | Unit] =
    logic.use(_.pure[F])

  // -------------------- MAPPING --------------------
  final def logicMap[EE](
    f: Resourced[Resource[F, E | Unit]] => Resource[F, EE | Unit]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    App.of(resources, f(logic.resourced))

  final def map[EE](
    f: Resourced[E | Unit] => EE | Unit
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.map(v => f(v.resourced)))

  final def flatMap[EE](
    f: Resourced[E | Unit] => Resource[F, EE | Unit]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.flatMap(v => f(v.resourced)))

  final def evalMap[EE](
    f: Resourced[E | Unit] => F[EE | Unit]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.value.evalMap(v => f(v.resourced)))

  final def evalTap(
    f: Resourced[E | Unit] => F[Unit]
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
    f: (Resources, Throwable) => F[E | Unit]
  )(implicit
    F: ApplicativeError[F, Throwable]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(
      _.value.handleErrorWith[E | Unit, Throwable](e => Resource.eval(f(resources, e)))
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
        _ => Resource.pure(Right(()))
      )
  }

  /** @param resourcesLoader
    *   App resources loader instance
    * @param depsBuilder
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
  class AppBuilder[F[+_]: Async: Parallel, E, APP_INFO <: BasicAppInfo[
    ?
  ], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, DEPENDENCIES](
    resourcesLoader: AppResources.Loader[F, APP_INFO, LOGGER_T, CONFIG],
    depsBuilder: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, E | DEPENDENCIES]
  ) { $this =>

    def dependsOnE[DEP_2](
      dependenciesBuilder: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, E | DEP_2]
    ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEP_2] =
      new AppBuilder(
        resourcesLoader = $this.resourcesLoader,
        depsBuilder     = dependenciesBuilder
      )

    def provideOneE(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[E | Any]
    ): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      provideE(f.andThen(_.pure[List]))(Semigroup.first)

    def provideE(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[E | Any]]
    )(implicit semigroupE: Semigroup[E]): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      provideFEE(f.andThen(_.asRight.pure[F]))

    def provideFE(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[E | Any]]]
    )(implicit
      semigroupE: Semigroup[E],
      el: ErrorLifter[F, E]
    ): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      provideFEE(el.liftFunction(f))

    def provideFEE(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[E | List[F[E | Any]]]
    )(implicit semigroupE: Semigroup[E]): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] = {
      (
        for {
          // -------------------- RESOURCES -------------------
          appResources <- EitherT.right[E](Resource.eval(resourcesLoader.load))
          resLogger = LoggerAdapter[LOGGER_T]
            .toToolkit(appResources.logger)
            .mapK(Resource.liftK[F].andThen(EitherT.liftK[Resource[F, *], E]))

          // ------------------- DEPENDENCIES -----------------
          _              <- resLogger.info("Building services environment...")
          appDepServices <- EitherT(depsBuilder(appResources))
          _              <- resLogger.info("Services environment successfully built.")

          // --------------------- SERVICES -------------------
          _ <- resLogger.info("Building App...")
          appProvServices <- EitherT(
            Resource.eval(f(AppDependencies(appResources, appDepServices)))
          )
          _ <- resLogger.info("App successfully built.")
        } yield App.fromServices(appResources, appProvServices)
      ).value
    }
  }
  object AppBuilder {

    type Throw[F[+_], APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]], CONFIG, DEPENDENCIES] =
      AppBuilder[F, Throwable, APP_INFO, LOGGER_T, CONFIG, DEPENDENCIES]

    implicit class AppBuilderErrorLiftOps[F[+_]: Async, E: Semigroup, APP_INFO <: BasicAppInfo[
      ?
    ], LOGGER_T[
      _[_]
    ]: LoggerAdapter, CONFIG: Show, DEPENDENCIES](
      appBuilder: AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEPENDENCIES]
    ) {

      def dependsOn[DEP_2](
        dependenciesBuilder: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, DEP_2]
      )(implicit
        el: ErrorLifter.Resource[F, E]
      ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEP_2] =
        appBuilder.dependsOnE[DEP_2](el.liftFunction(dependenciesBuilder))

      def provideOne(f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[Any])(
        implicit el: ErrorLifter[F, E]
      ): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
        appBuilder.provideOneE(el.liftFunction(f))

      def provide(
        f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[Any]]
      )(implicit
        el: ErrorLifter[F, E]
      ): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
        appBuilder.provideE(f.andThen(_.map(el.lift(_))))

      def provideF(
        f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[Any]]]
      )(implicit
        el: ErrorLifter[F, E]
      ): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
        appBuilder
          .provideFE(f.andThen(_.map(_.map(el.lift(_)))))
    }

    implicit class AppBuilderThrowOps[F[+_]: Async, APP_INFO <: BasicAppInfo[
      ?
    ], LOGGER_T[
      _[_]
    ]: LoggerAdapter, CONFIG: Show, DEPENDENCIES](
      appBuilder: AppBuilder.Throw[F, APP_INFO, LOGGER_T, CONFIG, DEPENDENCIES]
    )(implicit semigroupThrow: Semigroup[Throwable]) {

      def provideOneT(f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[Any])(
        implicit el: ErrorLifter[F, Throwable]
      ): Resource[F, App.Throw[F, APP_INFO, LOGGER_T, CONFIG]] =
        appBuilder
          .provideOne(f)
          .evalMap(flatThrowError)

      def provideT(
        f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[Any]]
      )(implicit
        el: ErrorLifter[F, Throwable]
      ): Resource[F, App.Throw[F, APP_INFO, LOGGER_T, CONFIG]] =
        appBuilder
          .provideE(f.andThen(_.map(_.attempt)))
          .evalMap(flatThrowError)

      def provideFT(
        f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[Any]]]
      )(implicit
        el: ErrorLifter[F, Throwable]
      ): Resource[F, App.Throw[F, APP_INFO, LOGGER_T, CONFIG]] =
        appBuilder
          .provideFE(f.andThen(_.map(_.map(el.lift(_)))))
          .evalMap(flatThrowError)

      private def flatThrowError[R]: Throwable | R => F[R] = {
        case Left(e)    => Sync[F].raiseError(e)
        case Right(app) => Sync[F].pure(app)
      }
    }
  }

  def fromServices[F[+_]: Async: Parallel, E: Semigroup, APP_INFO <: BasicAppInfo[?], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show](
    appResources: AppResources[APP_INFO, LOGGER_T[F], CONFIG],
    appProvServices: List[F[E | Any]]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] = {

    import cats.syntax.all.*
    import cats.effect.syntax.all.*

    val toolkitLogger = LoggerAdapter[LOGGER_T].toToolkit[F](appResources.logger)
    val logic: Resource[F, E | Unit] = {
      val info = appResources.info

      val app: F[E | Unit] =
        for {
          fibers   <- Ref[F].of(List.empty[Fiber[F, Throwable, Unit]])
          failures <- Ref[F].of(List.empty[E])
          onFailureTask = fibers.get.flatMap(_.parTraverse(_.cancel.start).void)
          services = appProvServices.map(_.flatTap {
            case Left(failure) => failures.update(_ :+ failure) >> onFailureTask
            case Right(_)      => Async[F].unit
          })
          _ <- services.parTraverse(t => t.void.start.flatMap(f => fibers.update(_ :+ f)))
          _ <- fibers.get.flatMap(_.parTraverse(_.joinWithUnit))
          maybeReducedFailures <- failures.get.map(NonEmptyList.fromList(_).map(_.reduce))
        } yield maybeReducedFailures.toLeft(())

      Resource
        .eval[F, E | Unit](
          toolkitLogger.info(s"Starting ${info.buildRefName}...") >>
            app
              .onCancel(toolkitLogger.info(s"${info.name} was stopped."))
              .onError(e => toolkitLogger.error(e)(s"${info.name} was stopped due an error."))
        )
        .onFinalize(toolkitLogger.info(s"Shutting down ${info.name}..."))
    }

    App.of(appResources, logic)
  }

  def of[F[+_], E, APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]], CONFIG](
    appResources: AppResources[APP_INFO, LOGGER_T[F], CONFIG],
    appLogic: Resource[F, E | Unit]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] = new App[F, E, APP_INFO, LOGGER_T, CONFIG] {
    override val resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG] = appResources
    override val logic: Resource[F, E | Unit]                           = appLogic
  }
}
