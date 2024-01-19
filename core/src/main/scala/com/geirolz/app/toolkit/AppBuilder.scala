package com.geirolz.app.toolkit

import cats.{Foldable, Parallel, Show}
import cats.effect.{Async, Resource}
import com.geirolz.app.toolkit.AppBuilder.SelectResAndDeps
import com.geirolz.app.toolkit.logger.{LoggerAdapter, NoopLogger}
import com.geirolz.app.toolkit.novalues.{NoConfig, NoDependencies, NoResources}

import cats.syntax.all.given

final class AppBuilder[F[+_]: Async: Parallel, FAILURE]:
  def withInfo[APP_INFO <: SimpleAppInfo[?]](
    appInfo: APP_INFO
  ): AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, NoopLogger, NoConfig, NoResources] =
    new AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, NoopLogger, NoConfig, NoResources](
      appInfo         = appInfo,
      loggerBuilder   = NoopLogger[F].pure[F],
      configLoader    = Resource.pure(NoConfig.value),
      resourcesLoader = Resource.pure(NoResources.value)
    )

object AppBuilder {

  def apply[F[+_]: Async: Parallel](using DummyImplicit): AppBuilder[F, Throwable] =
    new AppBuilder[F, Throwable]

  def apply[F[+_]: Async: Parallel, FAILURE]: AppBuilder[F, FAILURE] =
    new AppBuilder[F, FAILURE]

  final class SelectResAndDeps[
    F[+_]: Async: Parallel,
    FAILURE,
    APP_INFO <: SimpleAppInfo[?],
    LOGGER_T[_[_]]: LoggerAdapter,
    CONFIG: Show,
    RESOURCES
  ] private[AppBuilder] (
    appInfo: APP_INFO,
    loggerBuilder: F[LOGGER_T[F]],
    configLoader: Resource[F, CONFIG],
    resourcesLoader: Resource[F, RESOURCES]
  ):

    // ------- LOGGER -------
    def withNoopLogger: AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, NoopLogger, CONFIG, RESOURCES] =
      withLogger(logger = NoopLogger[F])

    def withLogger[LOGGER_T2[_[_]]: LoggerAdapter](
      logger: LOGGER_T2[F]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T2, CONFIG, RESOURCES] =
      withLogger[LOGGER_T2](loggerF = (_: APP_INFO) => logger)

    def withLogger[LOGGER_T2[_[_]]: LoggerAdapter](
      loggerF: APP_INFO => LOGGER_T2[F]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T2, CONFIG, RESOURCES] =
      withLoggerBuilder(loggerBuilder = appInfo => loggerF(appInfo).pure[F])

    def withLoggerBuilder[LOGGER_T2[_[_]]: LoggerAdapter](
      loggerBuilder: APP_INFO => F[LOGGER_T2[F]]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T2, CONFIG, RESOURCES] =
      copyWith(loggerBuilder = loggerBuilder(appInfo))

    // ------- CONFIG -------
    def withoutConfig: AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, NoConfig, RESOURCES] =
      withConfig[NoConfig](NoConfig.value)

    def withConfig[CONFIG2: Show](
      config: CONFIG2
    ): AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfigLoader(config.pure[F])

    def withConfigLoader[CONFIG2: Show](
      configLoader: APP_INFO => F[CONFIG2]
    )(using DummyImplicit): AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfigLoader(i => Resource.eval(configLoader(i)))

    def withConfigLoader[CONFIG2: Show](
      configLoader: F[CONFIG2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfigLoader(Resource.eval(configLoader))

    def withConfigLoader[CONFIG2: Show](
      configLoader: Resource[F, CONFIG2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfigLoader(_ => configLoader)

    def withConfigLoader[CONFIG2: Show](
      configLoader: APP_INFO => Resource[F, CONFIG2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG2, RESOURCES] =
      copyWith(configLoader = configLoader(this.appInfo))

    // ------- RESOURCES -------
    def withoutResources: AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, NoResources] =
      withResources[NoResources](NoResources.value)

    def withResources[RESOURCES2](
      resources: RESOURCES2
    ): AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES2] =
      withResourcesLoader(resources.pure[F])

    def withResourcesLoader[RESOURCES2](
      resourcesLoader: F[RESOURCES2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES2] =
      withResourcesLoader(Resource.eval(resourcesLoader))

    def withResourcesLoader[RESOURCES2](
      resourcesLoader: Resource[F, RESOURCES2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES2] =
      copyWith(resourcesLoader = resourcesLoader)

    // ------- DEPENDENCIES -------
    def withoutDependencies: AppBuilder.SelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, NoDependencies] =
      dependsOn[NoDependencies, FAILURE](_ => Resource.pure(NoDependencies.value.asRight[FAILURE]))

    def dependsOn[DEPENDENCIES](
      f: AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, DEPENDENCIES]
    ): AppBuilder.SelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      dependsOn[DEPENDENCIES, FAILURE](f.andThen(_.map(_.asRight[FAILURE])))

    def dependsOn[DEPENDENCIES, FAILURE2 <: FAILURE](
      f: AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, FAILURE2 \/ DEPENDENCIES]
    )(using DummyImplicit): AppBuilder.SelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      AppBuilder.SelectProvide(
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
    ) = new AppBuilder.SelectResAndDeps[G, ERROR2, APP_INFO2, LOGGER_T2, CONFIG2, RESOURCES2](
      appInfo         = appInfo,
      loggerBuilder   = loggerBuilder,
      configLoader    = configLoader,
      resourcesLoader = resourcesLoader
    )

  final case class SelectProvide[
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
    ): AppBuilder.SelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      copy(beforeProvidingF = f)

    def beforeProvidingSeq(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
      fN: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]*
    ): AppBuilder.SelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      beforeProvidingSeq(deps => (f +: fN).map(_(deps)))

    def beforeProvidingSeq[G[_]: Foldable](
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => G[F[Unit]]
    ): AppBuilder.SelectProvide[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
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
}
