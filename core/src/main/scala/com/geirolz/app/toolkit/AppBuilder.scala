package com.geirolz.app.toolkit

import cats.effect.{Async, Resource}
import cats.syntax.all.given
import cats.{Endo, Parallel, Show}
import com.geirolz.app.toolkit
import com.geirolz.app.toolkit.App.*
import com.geirolz.app.toolkit.AppBuilder.SelectResAndDeps
import com.geirolz.app.toolkit.failure.FailureHandler
import com.geirolz.app.toolkit.logger.Logger.Level
import com.geirolz.app.toolkit.logger.{ConsoleLogger, Logger, LoggerAdapter, NoopLogger}
import com.geirolz.app.toolkit.novalues.NoFailure.NotNoFailure
import com.geirolz.app.toolkit.novalues.{NoConfig, NoDependencies, NoFailure, NoResources}

import java.time.LocalDateTime
import scala.reflect.ClassTag

final class AppBuilder[F[+_]: Async: Parallel, FAILURE: ClassTag]:

  inline def withInfo(
    name: String           = "",
    version: String        = "",
    scalaVersion: String   = "",
    sbtVersion: String     = "",
    builtOn: LocalDateTime = LocalDateTime.now()
  ): AppBuilder.SelectResAndDeps[F, FAILURE, SimpleAppInfo[String], NoopLogger, NoConfig, NoResources] =
    withInfo(
      SimpleAppInfo.string(
        name         = name,
        version      = version,
        scalaVersion = scalaVersion,
        sbtVersion   = sbtVersion,
        builtOn      = builtOn
      )
    )

  def withInfo[INFO <: SimpleAppInfo[?]](
    appInfo: INFO
  ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, NoopLogger, NoConfig, NoResources] =
    new AppBuilder.SelectResAndDeps[F, FAILURE, INFO, NoopLogger, NoConfig, NoResources](
      info             = appInfo,
      messages         = AppMessages.default(appInfo),
      loggerBuilder    = NoopLogger[F].pure[F],
      configBuilder    = NoConfig.value.pure[F],
      resourcesBuilder = NoResources.value.pure[F]
    )

object AppBuilder:

  type Simple[F[+_]] = AppBuilder[F, NoFailure]

  inline def simple[F[+_]: Async: Parallel]: AppBuilder.Simple[F] =
    new AppBuilder[F, NoFailure]

  inline def withFailure[F[+_]: Async: Parallel, FAILURE: ClassTag: NotNoFailure]: AppBuilder[F, FAILURE] =
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
    configBuilder: F[CONFIG],
    resourcesBuilder: F[RESOURCES]
  ):

    // ------- MESSAGES -------
    inline def withMessages(messages: AppMessages): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES] =
      updateMessages(_ => messages)

    inline def updateMessages(f: Endo[AppMessages]): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES] =
      copyWith(messages = f(this.messages))

    // ------- LOGGER -------
    inline def withNoopLogger: AppBuilder.SelectResAndDeps[F, FAILURE, INFO, NoopLogger, CONFIG, RESOURCES] =
      withLoggerPure(_ => Logger.noop[F])

    inline def withConsoleLogger(minLevel: Level = Level.Info): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, ConsoleLogger, CONFIG, RESOURCES] =
      withLoggerPure(_ => ConsoleLogger[F](info, minLevel))

    inline def withLoggerPure[LOGGER_T2[_[_]]: LoggerAdapter](
      f: INFO => LOGGER_T2[F]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T2, CONFIG, RESOURCES] =
      withLogger(f = appInfo => f(appInfo).pure[F])

    // TODO: Add failure
    inline def withLogger[LOGGER_T2[_[_]]: LoggerAdapter](
      f: INFO => F[LOGGER_T2[F]]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T2, CONFIG, RESOURCES] =
      copyWith(loggerBuilder = f(info))

    // ------- CONFIG -------
    inline def withoutConfig: AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, NoConfig, RESOURCES] =
      withConfigPure[NoConfig](NoConfig.value)

    inline def withConfigPure[CONFIG2: Show](
      config: CONFIG2
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfig(config.pure[F])

    // TODO: Add failure
    inline def withConfig[CONFIG2: Show](
      loader: F[CONFIG2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG2, RESOURCES] =
      withConfig(_ => loader)

    // TODO: Add failure
    inline def withConfig[CONFIG2: Show](
      loader: INFO => F[CONFIG2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG2, RESOURCES] =
      copyWith(configLoader = loader(this.info))

    // ------- RESOURCES -------
    inline def withoutResources: AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG, NoResources] =
      withResources[NoResources](NoResources.value.pure[F])

    // TODO: Add failure
    inline def withResources[RESOURCES2](
      loader: F[RESOURCES2]
    ): AppBuilder.SelectResAndDeps[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES2] =
      copyWith(resourcesLoader = loader)

    // ------- DEPENDENCIES -------
    inline def withoutDependencies: AppBuilder.SelectProvide[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, NoDependencies] =
      dependsOn[NoDependencies, FAILURE](Resource.pure(NoDependencies.value))

    /** Dependencies are loaded into context and released at the end of the application. */
    inline def dependsOn[DEPENDENCIES, FAILURE2 <: FAILURE: ClassTag](
      f: AppContext.NoDeps[INFO, LOGGER_T[F], CONFIG, RESOURCES] ?=> Resource[F, FAILURE2 | DEPENDENCIES]
    ): AppBuilder.SelectProvide[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      dependsOnE[DEPENDENCIES, FAILURE2](f.map {
        case deps: DEPENDENCIES => Right(deps)
        case failure: FAILURE2  => Left(failure)
      })

    /** Dependencies are loaded into context and released at the end of the application. */
    def dependsOnE[DEPENDENCIES, FAILURE2 <: FAILURE](
      f: AppContext.NoDeps[INFO, LOGGER_T[F], CONFIG, RESOURCES] ?=> Resource[F, FAILURE2 \/ DEPENDENCIES]
    )(using DummyImplicit): AppBuilder.SelectProvide[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      AppBuilder.SelectProvide(
        info                = info,
        messages            = messages,
        loggerBuilder       = loggerBuilder,
        configBuilder       = configBuilder,
        resourcesBuilder    = resourcesBuilder,
        dependenciesLoader  = f(using _),
        beforeProvidingTask = _ => ().pure[F]
      )

    private def copyWith[
      G[+_]: Async: Parallel,
      FAILURE2: ClassTag,
      INFO2 <: SimpleAppInfo[?],
      LOGGER_T2[_[_]]: LoggerAdapter,
      CONFIG2: Show,
      RESOURCES2
    ](
      info: INFO2                    = this.info,
      messages: AppMessages          = this.messages,
      loggerBuilder: G[LOGGER_T2[G]] = this.loggerBuilder,
      configLoader: G[CONFIG2]       = this.configBuilder,
      resourcesLoader: G[RESOURCES2] = this.resourcesBuilder
    ) = new AppBuilder.SelectResAndDeps[G, FAILURE2, INFO2, LOGGER_T2, CONFIG2, RESOURCES2](
      info             = info,
      messages         = messages,
      loggerBuilder    = loggerBuilder,
      configBuilder    = configLoader,
      resourcesBuilder = resourcesLoader
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
    configBuilder: F[CONFIG],
    resourcesBuilder: F[RESOURCES],
    dependenciesLoader: AppContext.NoDeps[INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, FAILURE \/ DEPENDENCIES],
    beforeProvidingTask: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]
  ):

    // ------- BEFORE PROVIDING -------
    inline def beforeProviding(
      f: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] ?=> F[Unit]
    ): AppBuilder.SelectProvide[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      copy(beforeProvidingTask = d => this.beforeProvidingTask(d) >> f(using d))

    // ------- PROVIDE -------
    def provideOne[FAILURE2 <: FAILURE: ClassTag](
      f: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] ?=> F[FAILURE2 | Unit]
    ): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideOneE[FAILURE2](f.map {
        case failure: FAILURE2 => Left(failure)
        case _: Unit           => Right(())
      })

    inline def provideOneE[FAILURE2 <: FAILURE](
      f: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] ?=> F[FAILURE2 \/ Unit]
    ): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideParallelE[FAILURE2](List(f))

    inline def provideOneF[FAILURE2 <: FAILURE](
      f: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] ?=> F[FAILURE2 \/ F[Unit]]
    ): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideParallelAttemptFE[FAILURE2](f.map(_.map(v => List(v.map(_.asRight[FAILURE2])))))

    // provide
    def provideParallel[FAILURE2 <: FAILURE: ClassTag](
      f: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] ?=> List[F[FAILURE2 | Unit]]
    ): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideParallelE(f.map(_.map {
        case failure: FAILURE2 => Left(failure)
        case _: Unit           => Right(())
      }))

    inline def provideParallelE[FAILURE2 <: FAILURE](
      f: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] ?=> List[F[FAILURE2 \/ Unit]]
    )(using DummyImplicit): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideParallelFE[FAILURE2](f.pure[F])

    // provideF
    def provideParallelF[FAILURE2 <: FAILURE: ClassTag](
      f: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] ?=> F[List[F[FAILURE2 | Unit]]]
    )(using DummyImplicit): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideParallelFE(f.map(_.map(_.map {
        case failure: FAILURE2 => Left(failure)
        case _: Unit           => Right(())
      })))

    inline def provideParallelFE[FAILURE2 <: FAILURE](
      f: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] ?=> F[List[F[FAILURE2 \/ Unit]]]
    )(using DummyImplicit): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      provideParallelAttemptFE(f.map(Right(_)))

    // TODO Missing the union version
    def provideParallelAttemptFE[FAILURE2 <: FAILURE](
      f: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] ?=> F[FAILURE2 \/ List[F[FAILURE2 \/ Unit]]]
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
        configBuilder       = configBuilder,
        resourcesBuilder    = resourcesBuilder,
        beforeProvidingTask = beforeProvidingTask,
        onFinalizeTask      = _ => ().pure[F],
        depsLoader          = dependenciesLoader(ctx),
        servicesBuilder     = f(using _)
      )
