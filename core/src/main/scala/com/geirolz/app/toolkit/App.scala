package com.geirolz.app.toolkit

import cats.{Endo, Parallel, Semigroup, Show}
import cats.data.{EitherT, NonEmptyList}
import cats.effect.*
import com.geirolz.app.toolkit.logger.{LoggerAdapter, NoopLogger}
import com.geirolz.app.toolkit.novalues.{NoConfig, NoDependencies, NoResources}
import com.geirolz.app.toolkit.FailureHandler.OnFailureBehaviour
import com.geirolz.app.toolkit.error.MultiException

class App[
  F[+_]: Async: Parallel,
  FAILURE,
  APP_INFO <: SimpleAppInfo[?],
  LOGGER_T[_[_]]: LoggerAdapter,
  CONFIG: Show,
  RESOURCES,
  DEPENDENCIES
] private[App] (
  val appInfo: APP_INFO,
  val appMessages: AppMessages,
  val loggerBuilder: F[LOGGER_T[F]],
  val configLoader: F[CONFIG],
  val resourcesLoader: F[RESOURCES],
  val beforeRunF: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
  val onFinalizeF: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
  val failureHandlerLoader: App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => FailureHandler[F, FAILURE],
  val dependenciesLoader: App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, FAILURE \/ DEPENDENCIES],
  val provideBuilder: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[FAILURE \/ List[F[FAILURE \/ Any]]]
) {
  type AppInfo = APP_INFO
  type Logger  = LOGGER_T[F]
  type Config  = CONFIG

  import cats.effect.syntax.all.*
  import cats.syntax.all.*

  type Self         = App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
  type AppResources = App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES]

  class Resourced[T] private (val appRes: AppResources, val value: T) {
    val info: APP_INFO       = appRes.info
    val config: CONFIG       = appRes.config
    val logger: LOGGER_T[F]  = appRes.logger
    val resources: RESOURCES = appRes.resources

    def map[U](f: T => U): Resourced[U]            = Resourced(appRes, f(value))
    def tupled: (AppResources, T)                  = Resourced.unapply(this)
    def useTupled[U](f: (AppResources, T) => U): U = f.tupled(tupled)
    def use[U](f: AppResources => T => U): U       = f(appRes)(value)

    def tupledAll: (APP_INFO, CONFIG, LOGGER_T[F], RESOURCES, T)                  = (info, config, logger, resources, value)
    def useTupledAll[U](f: (APP_INFO, CONFIG, LOGGER_T[F], RESOURCES, T) => U): U = f.tupled(tupledAll)
  }
  object Resourced {
    def apply[T](appRes: AppResources, value: T): Resourced[T] = new Resourced[T](appRes, value)
    def unapply[T](r: Resourced[T]): (AppResources, T)         = (r.appRes, r.value)
  }

  def withMessages(messages: AppMessages): Self =
    copyWith(appMessages = messages)

  def beforeRun(
    f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]
  ): Self =
    copyWith(beforeRunF = f)

  def onFinalize(
    f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]
  ): Self =
    copyWith(onFinalizeF = f)

  private[toolkit] def _compile: Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ Unit]] =
    (
      for {

        // -------------------- RESOURCES-------------------
        // logger
        appLogger <- EitherT.right[FAILURE](Resource.eval(this.loggerBuilder))
        appLoggerF = LoggerAdapter[LOGGER_T].toToolkit[F](appLogger)
        appResLogger = appLoggerF.mapK(
          Resource.liftK[F].andThen(EitherT.liftK[Resource[F, *], FAILURE])
        )

        // config
        _         <- appResLogger.info(appMessages.loadingConfig)
        appConfig <- EitherT.right[FAILURE](Resource.eval(this.configLoader))
        _         <- appResLogger.info(appMessages.configSuccessfullyLoaded)
        _         <- appResLogger.info(appConfig.show)

        // other resources
        otherResources <- EitherT.right[FAILURE](Resource.eval(resourcesLoader))

        // group resources
        appResources: App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] = App.Resources(
          info      = this.appInfo,
          logger    = appLogger,
          config    = appConfig,
          resources = otherResources
        )

        // ------------------- DEPENDENCIES -----------------
        _              <- appResLogger.info(appMessages.buildingServicesEnv)
        appDepServices <- EitherT(dependenciesLoader(appResources))
        _              <- appResLogger.info(appMessages.servicesEnvSuccessfullyBuilt)
        appDependencies = App.Dependencies(appResources, appDepServices)

        // --------------------- SERVICES -------------------
        _               <- appResLogger.info(appMessages.buildingApp)
        appProvServices <- EitherT(Resource.eval(provideBuilder(appDependencies)))
        _               <- appResLogger.info(appMessages.appSuccessfullyBuilt)

        // --------------------- APP ------------------------
        appLogic = for {
          fibers   <- Ref[F].of(List.empty[Fiber[F, Throwable, Unit]])
          failures <- Ref[F].of(List.empty[FAILURE])
          failureHandler = failureHandlerLoader(appResources)
          onFailureTask: (FAILURE => F[Unit]) =
            failureHandler
              .handleFailureWithF(_)
              .flatMap {
                case Left(failure) =>
                  failureHandler
                    .onFailureF(failure)
                    .attemptT
                    .semiflatMap {
                      case OnFailureBehaviour.CancelAll =>
                        fibers.get.flatMap(_.parTraverse(_.cancel.start).void)
                      case OnFailureBehaviour.DoNothing =>
                        Async[F].unit
                    }
                    .rethrowT
                case Right(_) =>
                  Async[F].unit
              }

          services = appProvServices.map(_.flatTap {
            case Left(failure) =>
              failures.update(_ :+ failure) >> onFailureTask(failure)
            case Right(_) => Async[F].unit
          })
          _                    <- services.parTraverse(t => t.void.start.flatMap(f => fibers.update(_ :+ f)))
          _                    <- fibers.get.flatMap(_.parTraverse(_.joinWithUnit))
          maybeReducedFailures <- failures.get.map(NonEmptyList.fromList(_))
        } yield maybeReducedFailures.toLeft(())
      } yield {
        appLoggerF.info(appMessages.startingApp) >>
        beforeRunF(appDependencies) >>
        appLogic
          .onCancel(appLoggerF.info(appMessages.appWasStopped))
          .onError(e => appLoggerF.error(e)(appMessages.appEnErrorOccurred))
          .guarantee(
            onFinalizeF(appDependencies) >> appLoggerF.info(appMessages.shuttingDownApp)
          )
      }
    ).value

  private[toolkit] def _updateFailureHandlerLoader(
    fh: App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Endo[FailureHandler[F, FAILURE]]
  ): App[
    F,
    FAILURE,
    APP_INFO,
    LOGGER_T,
    CONFIG,
    RESOURCES,
    DEPENDENCIES
  ] =
    copyWith(
      failureHandlerLoader = appRes => fh(appRes)(failureHandlerLoader(appRes))
    )

  private[toolkit] def copyWith[
    G[+_]: Async: Parallel,
    FAILURE2,
    APP_INFO2 <: SimpleAppInfo[?],
    LOGGER_T2[_[_]]: LoggerAdapter,
    CONFIG2: Show,
    RES2,
    DEPS2
  ](
    appInfo: APP_INFO2                                                                      = this.appInfo,
    appMessages: AppMessages                                                                = this.appMessages,
    loggerBuilder: G[LOGGER_T2[G]]                                                          = this.loggerBuilder,
    configLoader: G[CONFIG2]                                                                = this.configLoader,
    resourcesLoader: G[RES2]                                                                = this.resourcesLoader,
    beforeRunF: App.Dependencies[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[Unit]  = this.beforeRunF,
    onFinalizeF: App.Dependencies[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[Unit] = this.onFinalizeF,
    failureHandlerLoader: App.Resources[APP_INFO2, LOGGER_T2[G], CONFIG2, RES2] => FailureHandler[
      G,
      FAILURE2
    ] = this.failureHandlerLoader,
    dependenciesLoader: App.Resources[APP_INFO2, LOGGER_T2[G], CONFIG2, RES2] => Resource[
      G,
      FAILURE2 \/ DEPS2
    ] = this.dependenciesLoader,
    provideBuilder: App.Dependencies[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[
      FAILURE2 \/ List[G[FAILURE2 \/ Any]]
    ] = this.provideBuilder
  ): App[G, FAILURE2, APP_INFO2, LOGGER_T2, CONFIG2, RES2, DEPS2] =
    new App[G, FAILURE2, APP_INFO2, LOGGER_T2, CONFIG2, RES2, DEPS2](
      appInfo              = appInfo,
      appMessages          = appMessages,
      loggerBuilder        = loggerBuilder,
      configLoader         = configLoader,
      resourcesLoader      = resourcesLoader,
      beforeRunF           = beforeRunF,
      onFinalizeF          = onFinalizeF,
      failureHandlerLoader = failureHandlerLoader,
      dependenciesLoader   = dependenciesLoader,
      provideBuilder       = provideBuilder
    )
}
object App extends AppSyntax {

  import cats.syntax.all.*

  final case class Dependencies[APP_INFO <: SimpleAppInfo[?], LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
    resources: App.Resources[APP_INFO, LOGGER, CONFIG, RESOURCES],
    dependencies: DEPENDENCIES
  ) {
    // proxies
    val info: APP_INFO = resources.info
    val logger: LOGGER = resources.logger
    val config: CONFIG = resources.config
  }

  final case class Resources[APP_INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES](
    info: APP_INFO,
    logger: LOGGER,
    config: CONFIG,
    resources: RESOURCES
  ) {
    type AppInfo   = APP_INFO
    type Logger    = LOGGER
    type Config    = CONFIG
    type Resources = RESOURCES
  }

  def apply[F[+_]: Async: Parallel](implicit dummyImplicit: DummyImplicit): AppBuilderRuntimeSelected[F, Throwable] =
    App[F, Throwable]

  def apply[F[+_]: Async: Parallel, FAILURE]: AppBuilderRuntimeSelected[F, FAILURE] =
    new AppBuilderRuntimeSelected[F, FAILURE]

  final class AppBuilderRuntimeSelected[F[+_]: Async: Parallel, FAILURE] private[App] () {
    def withInfo[APP_INFO <: SimpleAppInfo[?]](
      appInfo: APP_INFO
    ): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, NoopLogger, NoConfig, NoResources] =
      new AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, NoopLogger, NoConfig, NoResources](
        appInfo         = appInfo,
        loggerBuilder   = NoopLogger[F].pure[F],
        configLoader    = NoConfig.value.pure[F],
        resourcesLoader = NoResources.value.pure[F]
      )
  }

  final class AppBuilderSelectResAndDeps[F[+_]: Async: Parallel, FAILURE, APP_INFO <: SimpleAppInfo[
    ?
  ], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, RESOURCES] private[App] (
    appInfo: APP_INFO,
    loggerBuilder: F[LOGGER_T[F]],
    configLoader: F[CONFIG],
    resourcesLoader: F[RESOURCES]
  ) {

    // ------- LOGGER -------
    def withNoopLogger: AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, NoopLogger, CONFIG, RESOURCES] =
      withLogger(logger = NoopLogger[F])

    def withLogger[LOGGER_T2[_[_]]: LoggerAdapter](
      logger: LOGGER_T2[F]
    ): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T2, CONFIG, RESOURCES] =
      withLogger[LOGGER_T2](loggerF = (_: APP_INFO) => logger)

    def withLogger[LOGGER_T2[_[_]]: LoggerAdapter](
      loggerF: APP_INFO => LOGGER_T2[F]
    ): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T2, CONFIG, RESOURCES] =
      withLoggerBuilder(loggerBuilder = appInfo => loggerF(appInfo).pure[F])

    def withLoggerBuilder[LOGGER_T2[_[_]]: LoggerAdapter](
      loggerBuilder: APP_INFO => F[LOGGER_T2[F]]
    ): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T2, CONFIG, RESOURCES] =
      copyWith(loggerBuilder = loggerBuilder(appInfo))

    // ------- CONFIG -------
    def withoutConfig: AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, NoConfig, RESOURCES] =
      withConfig[NoConfig](NoConfig.value)

    def withConfig[CONFIG2: Show](
      config: CONFIG2
    ): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG2, RESOURCES] =
      copyWith(configLoader = config.pure[F])

    def withConfigLoader[CONFIG2: Show](
      configF: APP_INFO => F[CONFIG2]
    ): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG2, RESOURCES] =
      copyWith(configLoader = configF(appInfo))

    // ------- RESOURCES -------
    def withoutResources: AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, NoResources] =
      withResources[NoResources](NoResources.value)

    def withResources[RESOURCES2](
      resources: RESOURCES2
    ): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES2] =
      withResourcesLoader(resources.pure[F])

    def withResourcesLoader[RESOURCES2](
      resourcesLoader: F[RESOURCES2]
    ): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES2] =
      copyWith(resourcesLoader = resourcesLoader)

    // ------- DEPENDENCIES -------
    def withoutDependencies: AppBuilderSelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, NoDependencies] =
      dependsOn[NoDependencies, FAILURE](_ => Resource.pure(NoDependencies.value.asRight[FAILURE]))

    def dependsOn[DEPENDENCIES](
      f: App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, DEPENDENCIES]
    ): AppBuilderSelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      dependsOn[DEPENDENCIES, FAILURE](f.andThen(_.map(_.asRight[FAILURE])))

    def dependsOn[DEPENDENCIES, F2 <: FAILURE](
      f: App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, F2 \/ DEPENDENCIES]
    )(implicit dummyImplicit: DummyImplicit): AppBuilderSelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      new AppBuilderSelectProvide(
        appInfo            = appInfo,
        loggerBuilder      = loggerBuilder,
        configLoader       = configLoader,
        resourcesLoader    = resourcesLoader,
        dependenciesLoader = f
      )

    private def copyWith[G[+_]: Async: Parallel, ERROR2, APP_INFO2 <: SimpleAppInfo[?], LOGGER_T2[
      _[_]
    ]: LoggerAdapter, CONFIG2: Show, RESOURCES2](
      appInfo: APP_INFO2             = this.appInfo,
      loggerBuilder: G[LOGGER_T2[G]] = this.loggerBuilder,
      configLoader: G[CONFIG2]       = this.configLoader,
      resourcesLoader: G[RESOURCES2] = this.resourcesLoader
    ) = new AppBuilderSelectResAndDeps[G, ERROR2, APP_INFO2, LOGGER_T2, CONFIG2, RESOURCES2](
      appInfo         = appInfo,
      loggerBuilder   = loggerBuilder,
      configLoader    = configLoader,
      resourcesLoader = resourcesLoader
    )
  }

  final class AppBuilderSelectProvide[F[+_]: Async: Parallel, FAILURE, APP_INFO <: SimpleAppInfo[
    ?
  ], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, RESOURCES, DEPENDENCIES] private[App] (
    appInfo: APP_INFO,
    loggerBuilder: F[LOGGER_T[F]],
    configLoader: F[CONFIG],
    resourcesLoader: F[RESOURCES],
    dependenciesLoader: App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[
      F,
      FAILURE \/ DEPENDENCIES
    ]
  ) {

    def provideOne(
      f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Any]
    )(implicit
      env: FAILURE =:= Throwable
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideOne[FAILURE](f.andThen(_.map(_.asRight[FAILURE])))

    def provideOne[F2 <: FAILURE](
      f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[F2 \/ Any]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provide[F2](f.andThen(List(_)))

    def provideOneF[F2 <: FAILURE](
      f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[F2 \/ F[Any]]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideAttemptF[F2](f.andThen((fa: F[F2 \/ F[Any]]) => fa.map(_.map(v => List(v.map(_.asRight[F2]))))))

    // provide
    def provide(
      f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => List[F[Any]]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provide[FAILURE](f.andThen(_.map(_.map(_.asRight[FAILURE]))))

    def provide[F2 <: FAILURE](
      f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => List[F[F2 \/ Any]]
    )(implicit dummyImplicit: DummyImplicit): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideF[F2](f.andThen(_.pure[F]))

    // provideF
    def provideF(
      f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[List[F[Any]]]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideF[FAILURE](f.andThen(_.map(_.map(_.map(_.asRight[FAILURE])))))

    def provideF[F2 <: FAILURE](
      f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[List[F[F2 \/ Any]]]
    )(implicit dummyImplicit: DummyImplicit): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideAttemptF(f.andThen(_.map(Right(_))))

    def provideAttemptF[F2 <: FAILURE](
      f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[F2 \/ List[F[F2 \/ Any]]]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] = new App(
      appInfo              = appInfo,
      appMessages          = AppMessages.default(appInfo),
      failureHandlerLoader = _ => FailureHandler.cancelAll,
      loggerBuilder        = loggerBuilder,
      resourcesLoader      = resourcesLoader,
      beforeRunF           = _ => ().pure[F],
      onFinalizeF          = _ => ().pure[F],
      configLoader         = configLoader,
      dependenciesLoader   = dependenciesLoader,
      provideBuilder       = f
    )
  }
}
sealed trait AppSyntax {

  import cats.syntax.all.*

  implicit class AppOps[F[
    +_
  ]: Async: Parallel, FAILURE: * =:!= Throwable, APP_INFO <: SimpleAppInfo[
    ?
  ], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, RESOURCES, DEPENDENCIES](
    app: App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
  ) {

    // failures
    def mapFailure[FAILURE2](
      fhLoader: App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => FailureHandler[
        F,
        FAILURE2
      ]
    )(
      f: FAILURE => FAILURE2
    ): App[F, FAILURE2, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      app.copyWith[F, FAILURE2, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES](
        failureHandlerLoader = fhLoader,
        dependenciesLoader   = app.dependenciesLoader.andThen(_.map(_.leftMap(f))),
        provideBuilder       = app.provideBuilder.andThen(_.map(_.leftMap(f).map(_.map(_.map(_.leftMap(f))))))
      )

    def onFailure_(
      f: app.Resourced[FAILURE] => F[Unit]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      app._updateFailureHandlerLoader(appRes =>
        _.onFailure(failure => f(app.Resourced(appRes, failure)) >> app.failureHandlerLoader(appRes).onFailureF(failure))
      )

    def onFailure(
      f: app.Resourced[FAILURE] => F[OnFailureBehaviour]
    )(implicit
      dummyImplicit: DummyImplicit
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      app._updateFailureHandlerLoader(appRes => _.onFailure(f.compose(app.Resourced(appRes, _))))

    def handleFailureWith(
      f: app.Resourced[FAILURE] => F[FAILURE \/ Unit]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      app._updateFailureHandlerLoader(appRes => _.handleFailureWith(f.compose(app.Resourced(appRes, _))))

    // compile and run
    def compile: Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ Unit]] =
      app._compile

    def run: F[ExitCode] = run {
      case Left(_)  => ExitCode.Error
      case Right(_) => ExitCode.Success
    }

    def runReduce[B](f: FAILURE \/ Unit => B)(implicit semigroup: Semigroup[FAILURE]): F[B] =
      run {
        case Left(failures) => Left(failures.reduce)
        case Right(_)       => Right(())
      }.map(f)

    def run[B](f: NonEmptyList[FAILURE] \/ Unit => B): F[B] =
      compile
        .map {
          case Left(failure)   => f(Left(NonEmptyList.one(failure))).pure[F]
          case Right(appLogic) => appLogic.map(f)
        }
        .use(_.pure[F])
        .flatten
  }

  implicit class AppThrowOps[F[+_]: Async: Parallel, APP_INFO <: SimpleAppInfo[?], LOGGER_T[
    _[_]
  ], CONFIG, RESOURCES, DEPENDENCIES](
    app: App[F, Throwable, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
  ) {

    def compile: Resource[F, F[Unit]] =
      app._compile.flatMap {
        case Left(failure) =>
          Resource.raiseError(failure)
        case Right(value) =>
          Resource.pure(value.flatMap {
            case Left(failures: NonEmptyList[Throwable]) =>
              MultiException.fromNel(failures).raiseError[F, Unit]
            case Right(value) => value.pure[F]
          })
      }

    def run: F[ExitCode] =
      run(ExitCode.Success)

    def run[U](b: U): F[U] =
      compile
        .use(_.pure[F])
        .flatten
        .as(b)
  }
}
