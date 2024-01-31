package com.geirolz.app.toolkit

import cats.effect.*
import cats.syntax.all.given
import cats.{Endo, Parallel, Show}
import com.geirolz.app.toolkit.failure.FailureHandler
import com.geirolz.app.toolkit.failure.FailureHandler.OnFailureBehaviour
import com.geirolz.app.toolkit.logger.LoggerAdapter
import com.geirolz.app.toolkit.novalues.NoFailure.NotNoFailure
import com.geirolz.app.toolkit.novalues.NoFailure

import scala.reflect.ClassTag

class App[
  F[+_]: Async: Parallel,
  FAILURE,
  INFO <: SimpleAppInfo[?],
  LOGGER_T[_[_]]: LoggerAdapter,
  CONFIG: Show,
  RESOURCES,
  DEPENDENCIES
] private[toolkit] (
  val info: INFO,
  val messages: AppMessages,
  val loggerBuilder: F[LOGGER_T[F]],
  val configLoader: Resource[F, CONFIG],
  val resourcesLoader: Resource[F, RESOURCES],
  val beforeProvidingTask: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
  val onFinalizeTask: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
  val failureHandlerLoader: AppContext.NoDeps[INFO, LOGGER_T[F], CONFIG, RESOURCES] ?=> FailureHandler[F, FAILURE],
  val depsLoader: AppContext.NoDeps[INFO, LOGGER_T[F], CONFIG, RESOURCES] ?=> Resource[F, FAILURE \/ DEPENDENCIES],
  val servicesBuilder: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[FAILURE \/ List[F[FAILURE \/ Unit]]]
):
  type AppInfo       = INFO
  type Logger        = LOGGER_T[F]
  type Config        = CONFIG
  type Self          = App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
  type ContextNoDeps = AppContext.NoDeps[INFO, LOGGER_T[F], CONFIG, RESOURCES]

  inline def onFinalize(
    f: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] ?=> F[Unit]
  ): Self =
    copyWith(onFinalizeTask = deps => this.onFinalizeTask(deps) >> f(using deps))

  // compile and run
  inline def compile[R[_]](
    appArgs: List[String] = Nil
  )(using c: AppCompiler[F], i: AppLogicInterpreter[F, R, FAILURE]): Resource[F, F[R[Unit]]] =
    i.interpret(c.compile(appArgs, this))

  inline def run[R[_]](appArgs: List[String] = Nil)(using c: AppCompiler[F], i: AppLogicInterpreter[F, R, FAILURE]): F[ExitCode] =
    runRaw[R](appArgs)
      .map(i.isSuccess(_))
      .ifF(
        ifTrue  = ExitCode.Success,
        ifFalse = ExitCode.Error
      )

  inline def runRaw[R[_]](appArgs: List[String] = Nil)(using c: AppCompiler[F], i: AppLogicInterpreter[F, R, FAILURE]): F[R[Unit]] =
    compile(appArgs).useEval

  private[toolkit] def copyWith[
    G[+_]: Async: Parallel,
    FAILURE2,
    APP_INFO2 <: SimpleAppInfo[?],
    LOGGER_T2[_[_]]: LoggerAdapter,
    CONFIG2: Show,
    RES2,
    DEPS2
  ](
    appInfo: APP_INFO2                                                                                                    = this.info,
    appMessages: AppMessages                                                                                              = this.messages,
    loggerBuilder: G[LOGGER_T2[G]]                                                                                        = this.loggerBuilder,
    configLoader: Resource[G, CONFIG2]                                                                                    = this.configLoader,
    resourcesLoader: Resource[G, RES2]                                                                                    = this.resourcesLoader,
    beforeProvidingTask: AppContext[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[Unit]                             = this.beforeProvidingTask,
    onFinalizeTask: AppContext[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[Unit]                                  = this.onFinalizeTask,
    failureHandlerLoader: AppContext.NoDeps[APP_INFO2, LOGGER_T2[G], CONFIG2, RES2] ?=> FailureHandler[G, FAILURE2]       = this.failureHandlerLoader,
    dependenciesLoader: AppContext.NoDeps[APP_INFO2, LOGGER_T2[G], CONFIG2, RES2] ?=> Resource[G, FAILURE2 \/ DEPS2]      = this.depsLoader,
    provideBuilder: AppContext[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[FAILURE2 \/ List[G[FAILURE2 \/ Unit]]] = this.servicesBuilder
  ): App[G, FAILURE2, APP_INFO2, LOGGER_T2, CONFIG2, RES2, DEPS2] =
    new App[G, FAILURE2, APP_INFO2, LOGGER_T2, CONFIG2, RES2, DEPS2](
      info                 = appInfo,
      messages             = appMessages,
      loggerBuilder        = loggerBuilder,
      configLoader         = configLoader,
      resourcesLoader      = resourcesLoader,
      beforeProvidingTask  = beforeProvidingTask,
      onFinalizeTask       = onFinalizeTask,
      failureHandlerLoader = failureHandlerLoader,
      depsLoader           = dependenciesLoader,
      servicesBuilder      = provideBuilder
    )

object App extends AppFailureSyntax:

  inline def apply[F[+_]: Async: Parallel]: AppBuilder[F, NoFailure] =
    AppBuilder[F]

  inline def apply[F[+_]: Async: Parallel, FAILURE: ClassTag]: AppBuilder[F, FAILURE] =
    AppBuilder[F, FAILURE]

sealed transparent trait AppFailureSyntax:

  extension [
    F[+_]: Async: Parallel,
    FAILURE: NotNoFailure,
    INFO <: SimpleAppInfo[?],
    LOGGER_T[_[_]]: LoggerAdapter,
    CONFIG: ClassTag: Show,
    RESOURCES: ClassTag,
    DEPENDENCIES: ClassTag
  ](app: App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES])

    // failures
    inline def mapFailure[FAILURE2](
      fhLoader: AppContext.NoDeps[INFO, LOGGER_T[F], CONFIG, RESOURCES] ?=> FailureHandler[F, FAILURE2]
    )(
      f: FAILURE => FAILURE2
    )(using NotNoFailure[FAILURE2]): App[F, FAILURE2, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      app.copyWith[F, FAILURE2, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES](
        failureHandlerLoader = fhLoader,
        dependenciesLoader   = app.depsLoader.map(_.leftMap(f)),
        provideBuilder       = app.servicesBuilder.andThen(_.map(_.leftMap(f).map(_.map(_.map(_.leftMap(f))))))
      )

    inline def onFailure_(
      f: app.ContextNoDeps ?=> FAILURE => F[Unit]
    ): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      _updateFailureHandlerLoader(_.onFailure(failure => f(failure) >> app.failureHandlerLoader.onFailureF(failure)))

    inline def onFailure(
      f: app.ContextNoDeps ?=> FAILURE => F[OnFailureBehaviour]
    ): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      _updateFailureHandlerLoader(_.onFailure(f))

    inline def handleFailureWith(
      f: app.ContextNoDeps ?=> FAILURE => F[FAILURE \/ Unit]
    ): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      _updateFailureHandlerLoader(_.handleFailureWith(f))

    private def _updateFailureHandlerLoader(
      fh: AppContext.NoDeps[INFO, LOGGER_T[F], CONFIG, RESOURCES] ?=> Endo[FailureHandler[F, FAILURE]]
    ): App[
      F,
      FAILURE,
      INFO,
      LOGGER_T,
      CONFIG,
      RESOURCES,
      DEPENDENCIES
    ] = app.copyWith(failureHandlerLoader = fh(app.failureHandlerLoader))
