package com.geirolz.app.toolkit

import cats.data.NonEmptyList
import cats.effect.*
import cats.{Endo, Foldable, Parallel, Semigroup, Show}
import com.geirolz.app.toolkit.FailureHandler.OnFailureBehaviour
import com.geirolz.app.toolkit.error.MultiException
import com.geirolz.app.toolkit.logger.{LoggerAdapter, NoopLogger}
import com.geirolz.app.toolkit.novalues.{NoConfig, NoDependencies, NoResources}

import scala.util.NotGiven

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
  val beforeProvidingF: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
  val onFinalizeF: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
  val failureHandlerLoader: AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => FailureHandler[F, FAILURE],
  val dependenciesLoader: AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, FAILURE \/ DEPENDENCIES],
  val provideBuilder: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[FAILURE \/ List[F[FAILURE \/ Any]]]
):
  type AppInfo = APP_INFO
  type Logger  = LOGGER_T[F]
  type Config  = CONFIG

  import cats.syntax.all.*

  type Self      = App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
  type Resources = AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES]

  class Resourced[T] private (val appRes: Resources, val value: T):
    val info: APP_INFO       = appRes.info
    val config: CONFIG       = appRes.config
    val logger: LOGGER_T[F]  = appRes.logger
    val resources: RESOURCES = appRes.resources

    def map[U](f: T => U): Resourced[U]         = Resourced(appRes, f(value))
    def tupled: (Resources, T)                  = Resourced.unapply(this)
    def useTupled[U](f: (Resources, T) => U): U = f.tupled(tupled)
    def use[U](f: Resources => T => U): U       = f(appRes)(value)

    def tupledAll: (APP_INFO, CONFIG, LOGGER_T[F], RESOURCES, T)                  = (info, config, logger, resources, value)
    def useTupledAll[U](f: (APP_INFO, CONFIG, LOGGER_T[F], RESOURCES, T) => U): U = f.tupled(tupledAll)

  object Resourced:
    def apply[T](appRes: Resources, value: T): Resourced[T] = new Resourced[T](appRes, value)
    def unapply[T](r: Resourced[T]): (Resources, T)         = (r.appRes, r.value)

  def withMessages(messages: AppMessages): Self =
    copyWith(appMessages = messages)

  def onFinalize(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]
  ): Self = copyWith(onFinalizeF = f)

  def onFinalizeSeq(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
    fN: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]*
  ): Self =
    onFinalizeSeq(deps => (f +: fN).map(_(deps)))

  def onFinalizeSeq[G[_]: Foldable](
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => G[F[Unit]]
  ): Self =
    copyWith(onFinalizeF = f(_).sequence_)

  private[toolkit] def _updateFailureHandlerLoader(
    fh: AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Endo[FailureHandler[F, FAILURE]]
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
    appInfo: APP_INFO2                                                                                         = this.appInfo,
    appMessages: AppMessages                                                                                   = this.appMessages,
    loggerBuilder: G[LOGGER_T2[G]]                                                                             = this.loggerBuilder,
    configLoader: Resource[G, CONFIG2]                                                                         = this.configLoader,
    resourcesLoader: Resource[G, RES2]                                                                         = this.resourcesLoader,
    beforeProvidingF: AppDependencies[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[Unit]                = this.beforeProvidingF,
    onFinalizeF: AppDependencies[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[Unit]                     = this.onFinalizeF,
    failureHandlerLoader: AppResources[APP_INFO2, LOGGER_T2[G], CONFIG2, RES2] => FailureHandler[G, FAILURE2]  = this.failureHandlerLoader,
    dependenciesLoader: AppResources[APP_INFO2, LOGGER_T2[G], CONFIG2, RES2] => Resource[G, FAILURE2 \/ DEPS2] = this.dependenciesLoader,
    provideBuilder: AppDependencies[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[FAILURE2 \/ List[G[FAILURE2 \/ Any]]] = this.provideBuilder
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

object App extends AppSyntax:

  import cats.syntax.all.*

  def apply[F[+_]: Async: Parallel](using DummyImplicit): AppBuilderRuntimeSelected[F, Throwable] =
    App[F, Throwable]

  def apply[F[+_]: Async: Parallel, FAILURE]: AppBuilderRuntimeSelected[F, FAILURE] =
    new AppBuilderRuntimeSelected[F, FAILURE]

  final class AppBuilderRuntimeSelected[F[+_]: Async: Parallel, FAILURE] private[App] ():
    def withInfo[APP_INFO <: SimpleAppInfo[?]](
      appInfo: APP_INFO
    ): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, NoopLogger, NoConfig, NoResources] =
      new AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, NoopLogger, NoConfig, NoResources](
        appInfo         = appInfo,
        loggerBuilder   = NoopLogger[F].pure[F],
        configLoader    = Resource.pure(NoConfig.value),
        resourcesLoader = Resource.pure(NoResources.value)
      )

  final class AppBuilderSelectResAndDeps[
    F[+_]: Async: Parallel,
    FAILURE,
    APP_INFO <: SimpleAppInfo[?],
    LOGGER_T[_[_]]: LoggerAdapter,
    CONFIG: Show,
    RESOURCES
  ] private[App] (
    appInfo: APP_INFO,
    loggerBuilder: F[LOGGER_T[F]],
    configLoader: Resource[F, CONFIG],
    resourcesLoader: Resource[F, RESOURCES]
  ):

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
    )(using DummyImplicit): AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG2, RESOURCES] =
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
      f: AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, DEPENDENCIES]
    ): AppBuilderSelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      dependsOn[DEPENDENCIES, FAILURE](f.andThen(_.map(_.asRight[FAILURE])))

    def dependsOn[DEPENDENCIES, FAILURE2 <: FAILURE](
      f: AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, FAILURE2 \/ DEPENDENCIES]
    )(using DummyImplicit): AppBuilderSelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      AppBuilderSelectProvide(
        appInfo            = appInfo,
        loggerBuilder      = loggerBuilder,
        configLoader       = configLoader,
        resourcesLoader    = resourcesLoader,
        dependenciesLoader = f,
        beforeProvidingF   = _ => ().pure[F]
      )

    private def copyWith[G[+_]: Async: Parallel, ERROR2, APP_INFO2 <: SimpleAppInfo[?], LOGGER_T2[_[_]]: LoggerAdapter, CONFIG2: Show, RESOURCES2](
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

  final case class AppBuilderSelectProvide[
    F[+_]: Async: Parallel,
    FAILURE,
    APP_INFO <: SimpleAppInfo[?],
    LOGGER_T[_[_]]: LoggerAdapter,
    CONFIG: Show,
    RESOURCES,
    DEPENDENCIES
  ](
    private val appInfo: APP_INFO,
    private val loggerBuilder: F[LOGGER_T[F]],
    private val configLoader: Resource[F, CONFIG],
    private val resourcesLoader: Resource[F, RESOURCES],
    private val dependenciesLoader: AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, FAILURE \/ DEPENDENCIES],
    private val beforeProvidingF: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]
  ):

    // ------- BEFORE PROVIDING -------
    def beforeProviding(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]
    ): AppBuilderSelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      copy(beforeProvidingF = f)

    def beforeProvidingSeq(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
      fN: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]*
    ): AppBuilderSelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      beforeProvidingSeq(deps => (f +: fN).map(_(deps)))

    def beforeProvidingSeq[G[_]: Foldable](
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => G[F[Unit]]
    ): AppBuilderSelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      copy(beforeProvidingF = f(_).sequence_)

    // ------- PROVIDE -------
    def provideOne(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Any]
    )(using FAILURE =:= Throwable): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideOne[FAILURE](f.andThen(_.map(_.asRight[FAILURE])))

    def provideOne[FAILURE2 <: FAILURE](
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[FAILURE2 \/ Any]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provide[FAILURE2](f.andThen(List(_)))

    def provideOneF[FAILURE2 <: FAILURE](
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[FAILURE2 \/ F[Any]]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideAttemptF[FAILURE2](f.andThen((fa: F[FAILURE2 \/ F[Any]]) => fa.map(_.map(v => List(v.map(_.asRight[FAILURE2]))))))

    // provide
    def provide(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => List[F[Any]]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provide[FAILURE](f.andThen(_.map(_.map(_.asRight[FAILURE]))))

    def provide[FAILURE2 <: FAILURE](
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => List[F[FAILURE2 \/ Any]]
    )(using DummyImplicit): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideF[FAILURE2](f.andThen(_.pure[F]))

    // provideF
    def provideF(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[List[F[Any]]]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideF[FAILURE](f.andThen(_.map(_.map(_.map(_.asRight[FAILURE])))))

    def provideF[FAILURE2 <: FAILURE](
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[List[F[FAILURE2 \/ Any]]]
    )(using DummyImplicit): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideAttemptF(f.andThen(_.map(Right(_))))

    def provideAttemptF[FAILURE2 <: FAILURE](
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[FAILURE2 \/ List[F[FAILURE2 \/ Any]]]
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

  object AppBuilderSelectProvide:
    private[App] def apply[
      F[+_]: Async: Parallel,
      FAILURE,
      APP_INFO <: SimpleAppInfo[?],
      LOGGER_T[_[_]]: LoggerAdapter,
      CONFIG: Show,
      RESOURCES,
      DEPENDENCIES
    ](
      appInfo: APP_INFO,
      loggerBuilder: F[LOGGER_T[F]],
      configLoader: Resource[F, CONFIG],
      resourcesLoader: Resource[F, RESOURCES],
      dependenciesLoader: AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, FAILURE \/ DEPENDENCIES],
      beforeProvidingF: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]
    ): AppBuilderSelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      new AppBuilderSelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES](
        appInfo            = appInfo,
        loggerBuilder      = loggerBuilder,
        configLoader       = configLoader,
        resourcesLoader    = resourcesLoader,
        dependenciesLoader = dependenciesLoader,
        beforeProvidingF   = beforeProvidingF
      )

end App

sealed trait AppSyntax:

  import cats.syntax.all.*

  extension [F[+_]: Async: Parallel, FAILURE, APP_INFO <: SimpleAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG: Show, RESOURCES, DEPENDENCIES](
    app: App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
  )(using NotGiven[FAILURE =:= Throwable]) {

    // failures
    def mapFailure[FAILURE2](
      fhLoader: AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => FailureHandler[F, FAILURE2]
    )(f: FAILURE => FAILURE2): App[F, FAILURE2, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
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
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      app._updateFailureHandlerLoader(appRes => _.onFailure(f.compose(app.Resourced(appRes, _))))

    def handleFailureWith(
      f: app.Resourced[FAILURE] => F[FAILURE \/ Unit]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      app._updateFailureHandlerLoader(appRes => _.handleFailureWith(f.compose(app.Resourced(appRes, _))))

    // compile and run
    def compile(appArgs: List[String] = Nil)(using AppCompiler[F]): Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ Unit]] =
      AppCompiler[F].compile(appArgs, app)

    def run(appArgs: List[String] = Nil)(using AppCompiler[F]): F[ExitCode] =
      runMap[ExitCode](appArgs).apply {
        case Left(_)  => ExitCode.Error
        case Right(_) => ExitCode.Success
      }

    def runReduce[B](appArgs: List[String] = Nil, f: FAILURE \/ Unit => B)(using AppCompiler[F], Semigroup[FAILURE]): F[B] =
      runMap[FAILURE \/ Unit](appArgs)
        .apply {
          case Left(failures) => Left(failures.reduce)
          case Right(_)       => Right(())
        }
        .map(f)

    def runRaw(appArgs: List[String] = Nil)(using AppCompiler[F]): F[NonEmptyList[FAILURE] \/ Unit] =
      runMap[NonEmptyList[FAILURE] \/ Unit](appArgs)(identity)

    def runMap[B](appArgs: List[String] = Nil)(f: NonEmptyList[FAILURE] \/ Unit => B)(using AppCompiler[F]): F[B] =
      AppCompiler[F].run[B](
        compile(appArgs).map {
          case Left(failure)   => f(Left(NonEmptyList.one(failure))).pure[F]
          case Right(appLogic) => appLogic.map(f)
        }
      )
  }

  extension [F[+_]: Async: Parallel, APP_INFO <: SimpleAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG: Show, RESOURCES, DEPENDENCIES](
    app: App[F, Throwable, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
  )(using Throwable =:= Throwable)

    def compile(appArgs: List[String] = Nil)(using AppCompiler[F]): Resource[F, F[Unit]] =
      AppCompiler[F].compile(appArgs, app).flatMap {
        case Left(failure) =>
          Resource.raiseError(failure)
        case Right(value) =>
          Resource.pure(value.flatMap {
            case Left(failures: NonEmptyList[Throwable]) =>
              MultiException.fromNel(failures).raiseError[F, Unit]
            case Right(value) => value.pure[F]
          })
      }

    def run_(implicit c: AppCompiler[F]): F[Unit] =
      run().void

    def run(appArgs: List[String] = Nil)(implicit c: AppCompiler[F]): F[ExitCode] =
      c.run(compile(appArgs)).as(ExitCode.Success)
