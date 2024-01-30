package com.geirolz.app.toolkit

import cats.effect.{Async, Resource}
import cats.syntax.all.given
import cats.{Foldable, Parallel, Show}
import com.geirolz.app.toolkit.App.*
import com.geirolz.app.toolkit.AppBuilder.SelectResAndDeps
import com.geirolz.app.toolkit.failure.FailureHandler
import com.geirolz.app.toolkit.logger.{LoggerAdapter, NoopLogger}
import com.geirolz.app.toolkit.novalues.NoFailure.NotNoFailure
import com.geirolz.app.toolkit.novalues.{NoConfig, NoDependencies, NoFailure, NoResources}

import scala.reflect.ClassTag

final class AppBuilder[F[+_]: Async: Parallel, FAILURE: ClassTag]:

  def withInfo[INFO <: SimpleAppInfo[?]](
    appInfo: INFO
  ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, NoopLogger, NoConfig, NoResources] =
    new AppBuilder.SelectResAndDeps[F, FAILURE, INFO, NoopLogger, NoConfig, NoResources](
      info            = appInfo,
      messages        = AppMessages.default(appInfo),
      loggerBuilder   = NoopLogger[F].pure[F],
      configLoader    = Resource.pure(NoConfig.value),
      resourcesLoader = Resource.pure(NoResources.value)
    )

object AppBuilder:

  inline def apply[F[+_]: Async: Parallel]: AppBuilder[F, NoFailure] =
    new AppBuilder[F, NoFailure]

  inline def apply[F[+_]: Async: Parallel, FAILURE: ClassTag: NotNoFailure]: AppBuilder[F, FAILURE] =
    new AppBuilder[F, FAILURE]

  final class SelectResAndDeps[
    F[+_]: Async: Parallel,
    FAILURE: ClassTag,
    INFO <: SimpleAppInfo[?],
    LOGGER_T[_[_]]: LoggerAdapter,
    CONFIG: Show,
    RESOURCES
  ] private[AppBuilder] (
    info: INFO,
    messages: AppMessages,
    loggerBuilder: F[LOGGER_T[F]],
    configLoader: Resource[F, CONFIG],
    resourcesLoader: Resource[F, RESOURCES]
  ):

    // ------- MESSAGES -------
    inline def withMessages(messages: AppMessages): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES] =
      copyWith(messages = messages)

    // ------- LOGGER -------
    inline def withNoopLogger: AppBuilder.SelectResAndDeps[F, FAILURE, INFO, NoopLogger, CONFIG, RESOURCES] =
      withPureLogger(logger = NoopLogger[F])

    inline def withPureLogger[LOGGER_T2[_[_]]: LoggerAdapter](
      logger: LOGGER_T2[F]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T2, CONFIG, RESOURCES] =
      withPureLogger[LOGGER_T2](f = (_: INFO) => logger)

    inline def withPureLogger[LOGGER_T2[_[_]]: LoggerAdapter](
      f: INFO => LOGGER_T2[F]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T2, CONFIG, RESOURCES] =
      withLoggerF(f = appInfo => f(appInfo).pure[F])

    // TODO: Add failure
    inline def withLoggerF[LOGGER_T2[_[_]]: LoggerAdapter](
      f: INFO => F[LOGGER_T2[F]]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T2, CONFIG, RESOURCES] =
      copyWith(loggerBuilder = f(info))

    // ------- CONFIG -------
    inline def withoutConfig: AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, NoConfig, RESOURCES] =
      withPureConfig[NoConfig](NoConfig.value)

    inline def withPureConfig[CONFIG2: Show](
      config: CONFIG2
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfigF(config.pure[F])

    // TODO: Add failure
    inline def withConfigF[CONFIG2: Show](
      configLoader: INFO => F[CONFIG2]
    )(using DummyImplicit): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfig(i => Resource.eval(configLoader(i)))

    // TODO: Add failure
    inline def withConfigF[CONFIG2: Show](
      configLoader: F[CONFIG2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfig(Resource.eval(configLoader))

    // TODO: Add failure
    inline def withConfig[CONFIG2: Show](
      configLoader: Resource[F, CONFIG2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfig(_ => configLoader)

    // TODO: Add failure
    inline def withConfig[CONFIG2: Show](
      configLoader: INFO => Resource[F, CONFIG2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG2, RESOURCES] =
      copyWith(configLoader = configLoader(this.info))

    // ------- RESOURCES -------
    inline def withoutResources: AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG, NoResources] =
      withPureResources[NoResources](NoResources.value)

    inline def withPureResources[RESOURCES2](
      resources: RESOURCES2
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES2] =
      withResourcesF(resources.pure[F])

    // TODO: Add failure
    inline def withResourcesF[RESOURCES2](
      resourcesLoader: F[RESOURCES2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES2] =
      withResources(Resource.eval(resourcesLoader))

    // TODO: Add failure
    inline def withResources[RESOURCES2](
      resourcesLoader: Resource[F, RESOURCES2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES2] =
      copyWith(resourcesLoader = resourcesLoader)

    // ------- DEPENDENCIES -------
    inline def withoutDependencies: AppBuilder.SelectProvide[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, NoDependencies] =
      dependsOn[NoDependencies, FAILURE](Resource.pure(NoDependencies.value))

    def dependsOn[DEPENDENCIES: ClassTag, FAILURE2 <: FAILURE: ClassTag](
      f: AppContext[INFO, LOGGER_T[F], CONFIG, RESOURCES] ?=> Resource[F, FAILURE2 | DEPENDENCIES]
    ): AppBuilder.SelectProvide[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      dependsOnE[DEPENDENCIES, FAILURE2](f.map {
        case deps: DEPENDENCIES => Right(deps)
        case failure: FAILURE2  => Left(failure)
      })

    def dependsOnE[DEPENDENCIES, FAILURE2 <: FAILURE](
      f: AppContext[INFO, LOGGER_T[F], CONFIG, RESOURCES] ?=> Resource[F, FAILURE2 \/ DEPENDENCIES]
    )(using DummyImplicit): AppBuilder.SelectProvide[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      AppBuilder.SelectProvide(
        info               = info,
        messages           = messages,
        loggerBuilder      = loggerBuilder,
        configLoader       = configLoader,
        resourcesLoader    = resourcesLoader,
        dependenciesLoader = ctx => { given AppContext[INFO, LOGGER_T[F], CONFIG, RESOURCES] = ctx; f },
        beforeProvidingF   = _ => ().pure[F]
      )

    private def copyWith[G[+_]: Async: Parallel, FAILURE2: ClassTag, INFO2 <: SimpleAppInfo[?], LOGGER_T2[
      _[_]
    ]: LoggerAdapter, CONFIG2: Show, RESOURCES2](
      info: INFO2                              = this.info,
      messages: AppMessages                    = this.messages,
      loggerBuilder: G[LOGGER_T2[G]]           = this.loggerBuilder,
      configLoader: Resource[G, CONFIG2]       = this.configLoader,
      resourcesLoader: Resource[G, RESOURCES2] = this.resourcesLoader
    ) = new AppBuilder.SelectResAndDeps[G, FAILURE2, INFO2, LOGGER_T2, CONFIG2, RESOURCES2](
      info            = info,
      messages        = messages,
      loggerBuilder   = loggerBuilder,
      configLoader    = configLoader,
      resourcesLoader = resourcesLoader
    )

  final case class SelectProvide[
    F[+_]: Async: Parallel,
    FAILURE,
    INFO <: SimpleAppInfo[?],
    LOGGER_T[_[_]]: LoggerAdapter,
    CONFIG: Show,
    RESOURCES,
    DEPENDENCIES
  ](
    info: INFO,
    messages: AppMessages,
    loggerBuilder: F[LOGGER_T[F]],
    configLoader: Resource[F, CONFIG],
    resourcesLoader: Resource[F, RESOURCES],
    dependenciesLoader: AppContext[INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, FAILURE \/ DEPENDENCIES],
    beforeProvidingF: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]
  ):

    // ------- BEFORE PROVIDING -------
    // TODO: Add failure
    inline def beforeProvidingSeq(
      f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
      fN: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]*
    ): AppBuilder.SelectProvide[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      beforeProvidingSeq(deps => (f +: fN).map(_(deps)))

    // TODO: Add failure
    inline def beforeProvidingSeq[G[_]: Foldable](
      f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => G[F[Unit]]
    ): AppBuilder.SelectProvide[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      copy(beforeProvidingF = d => this.beforeProvidingF(d) >> f(d).sequence_)

    // ------- PROVIDE -------
    def provideOne[FAILURE2 <: FAILURE: ClassTag](
      f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[FAILURE2 | Unit]
    ): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideOneE[FAILURE2](f.andThen(_.map {
        case failure: FAILURE2 => Left(failure)
        case _: Unit           => Right(())
      }))

    inline def provideOneE[FAILURE2 <: FAILURE](
      f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[FAILURE2 \/ Unit]
    ): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideE[FAILURE2](f.andThen(List(_)))

    inline def provideOneF[FAILURE2 <: FAILURE](
      f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[FAILURE2 \/ F[Unit]]
    ): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideAttemptFE[FAILURE2](f.andThen(_.map(_.map(v => List(v.map(_.asRight[FAILURE2]))))))

    // provide
    def provide[FAILURE2 <: FAILURE: ClassTag](
      f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => List[F[FAILURE2 | Unit]]
    ): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideE(f.andThen(_.map(_.map {
        case failure: FAILURE2 => Left(failure)
        case _: Unit           => Right(())
      })))

    inline def provideE[FAILURE2 <: FAILURE](
      f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => List[F[FAILURE2 \/ Unit]]
    )(using DummyImplicit): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideFE[FAILURE2](f.andThen(_.pure[F]))

    // provideF
    def provideF[FAILURE2 <: FAILURE: ClassTag](
      f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[List[F[FAILURE2 | Unit]]]
    )(using DummyImplicit): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideFE(f.andThen(_.map(_.map(_.map {
        case failure: FAILURE2 => Left(failure)
        case _: Unit           => Right(())
      }))))

    inline def provideFE[FAILURE2 <: FAILURE](
      f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[List[F[FAILURE2 \/ Unit]]]
    )(using DummyImplicit): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideAttemptFE(f.andThen(_.map(Right(_))))

    // TODO Missing the union version
    def provideAttemptFE[FAILURE2 <: FAILURE](
      f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[FAILURE2 \/ List[F[FAILURE2 \/ Unit]]]
    ): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      // TODO Allow custom AppMessages
      new App(
        info     = info,
        messages = messages,
        failureHandlerLoader = FailureHandler.logAndCancelAll[F, FAILURE](
          appMessages = ctx.messages,
          logger      = LoggerAdapter[LOGGER_T].toToolkit(ctx.logger)
        ),
        loggerBuilder       = loggerBuilder,
        resourcesLoader     = resourcesLoader,
        beforeProvidingTask = beforeProvidingF,
        onFinalizeTask      = _ => ().pure[F],
        configLoader        = configLoader,
        depsLoader          = dependenciesLoader(ctx),
        servicesBuilder     = f
      )
