package com.geirolz.app.toolkit

import cats.data.NonEmptyList
import cats.effect.*
import cats.{Endo, Parallel, Semigroup, Show}
import com.geirolz.app.toolkit.FailureHandler.OnFailureBehaviour
import com.geirolz.app.toolkit.error.MultiException
import com.geirolz.app.toolkit.logger.{LoggerAdapter, NoopLogger}
import com.geirolz.app.toolkit.novalues.{NoConfig, NoDependencies, NoResources}

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
  val configLoader: Resource[F, CONFIG],
  val resourcesLoader: Resource[F, RESOURCES],
  val beforeProvidingF: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
  val onFinalizeF: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
  val failureHandlerLoader: App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => FailureHandler[F, FAILURE],
  val dependenciesLoader: App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, FAILURE \/ DEPENDENCIES],
  val provideBuilder: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[FAILURE \/ List[F[FAILURE \/ Any]]]
) {
  type AppInfo = APP_INFO
  type Logger  = LOGGER_T[F]
  type Config  = CONFIG

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

  def onFinalize(
    f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
    fN: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]*
  ): Self =
    copyWith(onFinalizeF = (f +: fN).reduce(_ >> _))

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
    appInfo: APP_INFO2                                                                           = this.appInfo,
    appMessages: AppMessages                                                                     = this.appMessages,
    loggerBuilder: G[LOGGER_T2[G]]                                                               = this.loggerBuilder,
    configLoader: Resource[G, CONFIG2]                                                           = this.configLoader,
    resourcesLoader: Resource[G, RES2]                                                           = this.resourcesLoader,
    beforeProvidingF: App.Dependencies[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[Unit] = this.beforeProvidingF,
    onFinalizeF: App.Dependencies[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[Unit]      = this.onFinalizeF,
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
      beforeProvidingF     = beforeProvidingF,
      onFinalizeF          = onFinalizeF,
      failureHandlerLoader = failureHandlerLoader,
      dependenciesLoader   = dependenciesLoader,
      provideBuilder       = provideBuilder
    )
}
object App extends AppSyntax {

  import cats.syntax.all.*

  final case class Dependencies[APP_INFO <: SimpleAppInfo[?], LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
    private val _resources: App.Resources[APP_INFO, LOGGER, CONFIG, RESOURCES],
    private val _dependencies: DEPENDENCIES
  ) {
    // proxies
    val info: APP_INFO             = _resources.info
    val args: AppArgs              = _resources.args
    val logger: LOGGER             = _resources.logger
    val config: CONFIG             = _resources.config
    val resources: RESOURCES       = _resources.resources
    val dependencies: DEPENDENCIES = _dependencies

    override def toString: String =
      s"""App.Dependencies(
        |  info = $info,
        |  args = $args,
        |  logger = $logger,
        |  config = $config,
        |  resources = $resources,
        |  dependencies = $dependencies
        |)""".stripMargin
  }

  final case class Resources[APP_INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES](
    info: APP_INFO,
    args: AppArgs,
    logger: LOGGER,
    config: CONFIG,
    resources: RESOURCES
  ) {
    type AppInfo   = APP_INFO
    type Logger    = LOGGER
    type Config    = CONFIG
    type Resources = RESOURCES

    override def toString: String =
      s"""App.Dependencies(
         |  info = $info,
         |  args = $args,
         |  logger = $logger,
         |  config = $config,
         |  resources = $resources
         |)""".stripMargin
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
        configLoader    = Resource.pure(NoConfig.value),
        resourcesLoader = Resource.pure(NoResources.value)
      )
  }

  final class AppBuilderSelectResAndDeps[F[+_]: Async: Parallel, FAILURE, APP_INFO <: SimpleAppInfo[
    ?
  ], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, RESOURCES] private[App] (
    appInfo: APP_INFO,
    loggerBuilder: F[LOGGER_T[F]],
    configLoader: Resource[F, CONFIG],
    resourcesLoader: Resource[F, RESOURCES]
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
      withConfigLoader(config.pure[F])

    def withConfigLoader[CONFIG2: Show](
      configLoader: APP_INFO => F[CONFIG2]
    )(implicit dummyImplicit: DummyImplicit): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfigLoader(i => Resource.eval(configLoader(i)))

    def withConfigLoader[CONFIG2: Show](
      configLoader: F[CONFIG2]
    ): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfigLoader(Resource.eval(configLoader))

    def withConfigLoader[CONFIG2: Show](
      configLoader: Resource[F, CONFIG2]
    ): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfigLoader(_ => configLoader)

    def withConfigLoader[CONFIG2: Show](
      configLoader: APP_INFO => Resource[F, CONFIG2]
    ): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG2, RESOURCES] =
      copyWith(configLoader = configLoader(this.appInfo))

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
      withResourcesLoader(Resource.eval(resourcesLoader))

    def withResourcesLoader[RESOURCES2](
      resourcesLoader: Resource[F, RESOURCES2]
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
      AppBuilderSelectProvide(
        appInfo            = appInfo,
        loggerBuilder      = loggerBuilder,
        configLoader       = configLoader,
        resourcesLoader    = resourcesLoader,
        dependenciesLoader = f,
        beforeProvidingF   = _ => ().pure[F]
      )

    private def copyWith[G[+_]: Async: Parallel, ERROR2, APP_INFO2 <: SimpleAppInfo[?], LOGGER_T2[
      _[_]
    ]: LoggerAdapter, CONFIG2: Show, RESOURCES2](
      appInfo: APP_INFO2                       = this.appInfo,
      loggerBuilder: G[LOGGER_T2[G]]           = this.loggerBuilder,
      configLoader: Resource[G, CONFIG2]       = this.configLoader,
      resourcesLoader: Resource[G, RESOURCES2] = this.resourcesLoader
    ) = new AppBuilderSelectResAndDeps[G, ERROR2, APP_INFO2, LOGGER_T2, CONFIG2, RESOURCES2](
      appInfo         = appInfo,
      loggerBuilder   = loggerBuilder,
      configLoader    = configLoader,
      resourcesLoader = resourcesLoader
    )
  }

  final case class AppBuilderSelectProvide[F[+_]: Async: Parallel, FAILURE, APP_INFO <: SimpleAppInfo[
    ?
  ], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, RESOURCES, DEPENDENCIES] private[App] (
    private val appInfo: APP_INFO,
    private val loggerBuilder: F[LOGGER_T[F]],
    private val configLoader: Resource[F, CONFIG],
    private val resourcesLoader: Resource[F, RESOURCES],
    private val dependenciesLoader: App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[
      F,
      FAILURE \/ DEPENDENCIES
    ],
    private val beforeProvidingF: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]
  ) {

    // ------- BEFORE PROVIDING -------
    def beforeProviding(
      f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
      fN: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]*
    ): AppBuilderSelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      copy(beforeProvidingF = (f +: fN).reduce(_ >> _))

    // ------- PROVIDE -------
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
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      new App(
        appInfo              = appInfo,
        appMessages          = AppMessages.default(appInfo),
        failureHandlerLoader = _ => FailureHandler.cancelAll,
        loggerBuilder        = loggerBuilder,
        resourcesLoader      = resourcesLoader,
        beforeProvidingF     = beforeProvidingF,
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
  ]: Async: Parallel, FAILURE, APP_INFO <: SimpleAppInfo[
    ?
  ], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, RESOURCES, DEPENDENCIES](
    val app: App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
  )(implicit env: FAILURE =:!= Throwable) {

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
    def compile(appArgs: List[String] = Nil)(implicit c: AppInterpreter[F]): Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ Unit]] =
      c.compile(appArgs, app)

    def run(appArgs: List[String] = Nil)(implicit c: AppInterpreter[F]): F[ExitCode] =
      runMap[ExitCode](appArgs).apply {
        case Left(_)  => ExitCode.Error
        case Right(_) => ExitCode.Success
      }

    def runReduce[B](appArgs: List[String] = Nil, f: FAILURE \/ Unit => B)(implicit
      c: AppInterpreter[F],
      semigroup: Semigroup[FAILURE]
    ): F[B] =
      runMap[FAILURE \/ Unit](appArgs)
        .apply {
          case Left(failures) => Left(failures.reduce)
          case Right(_)       => Right(())
        }
        .map(f)

    def runRaw(appArgs: List[String] = Nil)(implicit c: AppInterpreter[F]): F[NonEmptyList[FAILURE] \/ Unit] =
      runMap[NonEmptyList[FAILURE] \/ Unit](appArgs)(identity)

    def runMap[B](appArgs: List[String] = Nil)(f: NonEmptyList[FAILURE] \/ Unit => B)(implicit c: AppInterpreter[F]): F[B] =
      c.run[B](
        compile(appArgs).map {
          case Left(failure)   => f(Left(NonEmptyList.one(failure))).pure[F]
          case Right(appLogic) => appLogic.map(f)
        }
      )
  }

  implicit class AppThrowOps[F[+_]: Async: Parallel, APP_INFO <: SimpleAppInfo[?], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, RESOURCES, DEPENDENCIES](
    app: App[F, Throwable, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
  ) {

    def compile(appArgs: List[String] = Nil)(implicit c: AppInterpreter[F]): Resource[F, F[Unit]] =
      c.compile(appArgs, app).flatMap {
        case Left(failure) =>
          Resource.raiseError(failure)
        case Right(value) =>
          Resource.pure(value.flatMap {
            case Left(failures: NonEmptyList[Throwable]) =>
              MultiException.fromNel(failures).raiseError[F, Unit]
            case Right(value) => value.pure[F]
          })
      }

    def run_(implicit c: AppInterpreter[F]): F[Unit] =
      run().void

    def run(appArgs: List[String] = Nil)(implicit c: AppInterpreter[F]): F[ExitCode] =
      c.run(compile(appArgs)).as(ExitCode.Success)
  }
}
