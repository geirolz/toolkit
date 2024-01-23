package com.geirolz.app.toolkit

import cats.effect.*
import cats.syntax.all.given
import cats.{Endo, Foldable, Parallel, Show}
import com.geirolz.app.toolkit.FailureHandler.OnFailureBehaviour
import com.geirolz.app.toolkit.logger.LoggerAdapter

import scala.reflect.ClassTag
import scala.util.NotGiven

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
  val beforeProvidingF: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
  val onFinalizeF: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
  val failureHandlerLoader: AppResources[INFO, LOGGER_T[F], CONFIG, RESOURCES] => FailureHandler[F, FAILURE],
  val depsLoader: AppResources[INFO, LOGGER_T[F], CONFIG, RESOURCES] => Resource[F, FAILURE \/ DEPENDENCIES],
  val servicesBuilder: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[FAILURE \/ List[F[FAILURE \/ Unit]]]
):
  type AppInfo               = INFO
  type Logger                = LOGGER_T[F]
  type Config                = CONFIG
  type Self                  = App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
  private type SelfResources = AppResources[INFO, LOGGER_T[F], CONFIG, RESOURCES]

  class Resourced[T](val res: SelfResources, val value: T):
    export res.*
    def map[U](f: T => U): Resourced[U]                                       = Resourced(res, f(value))
    def tupled: (SelfResources, T)                                            = (res, value)
    def useTupled[U](f: (SelfResources, T) => U): U                           = f.tupled(tupled)
    def use[U](f: SelfResources => T => U): U                                 = f(res)(value)
    def tupledAll: (INFO, CONFIG, LOGGER_T[F], RESOURCES, T)                  = (info, config, logger, resources, value)
    def useTupledAll[U](f: (INFO, CONFIG, LOGGER_T[F], RESOURCES, T) => U): U = f.tupled(tupledAll)

  def onFinalize(
    f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]
  ): Self = copyWith(onFinalizeF = f)

  def onFinalizeSeq(
    f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit],
    fN: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit]*
  ): Self =
    onFinalizeSeq(deps => (f +: fN).map(_(deps)))

  def onFinalizeSeq[G[_]: Foldable](
    f: AppDependencies[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => G[F[Unit]]
  ): Self =
    copyWith(onFinalizeF = f(_).sequence_)

  // -------------------- AppFailureSyntax --------------------
  // failures
  def mapFailure[FAILURE2](
    fhLoader: AppResources[INFO, LOGGER_T[F], CONFIG, RESOURCES] => FailureHandler[F, FAILURE2]
  )(
    f: FAILURE => FAILURE2
  )(using NotNothing[FAILURE], NotNothing[FAILURE2]): App[F, FAILURE2, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
    copyWith[F, FAILURE2, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES](
      failureHandlerLoader = fhLoader,
      dependenciesLoader   = depsLoader.andThen(_.map(_.leftMap(f))),
      provideBuilder       = servicesBuilder.andThen(_.map(_.leftMap(f).map(_.map(_.map(_.leftMap(f))))))
    )

  def onFailure_(
    f: Resourced[FAILURE] => F[Unit]
  )(using NotNothing[FAILURE]): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
    _updateFailureHandlerLoader(appRes => _.onFailure(failure => f(Resourced(appRes, failure)) >> failureHandlerLoader(appRes).onFailureF(failure)))

  def onFailure(
    f: Resourced[FAILURE] => F[OnFailureBehaviour]
  )(using NotNothing[FAILURE]): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
    _updateFailureHandlerLoader(appRes => _.onFailure(f.compose(Resourced(appRes, _))))

  def handleFailureWith(
    f: Resourced[FAILURE] => F[FAILURE \/ Unit]
  )(using NotNothing[FAILURE]): App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
    _updateFailureHandlerLoader(appRes => _.handleFailureWith(f.compose(Resourced(appRes, _))))

  // compile and run
  def compile(appArgs: List[String])(using c: AppCompiler[F], i: AppLogicTypeInterpreter[F, FAILURE]): Resource[F, F[i.Result[Unit]]] =
    i.interpret(c.compile(appArgs, this))

  def run_(appArgs: List[String])(using AppCompiler[F], AppLogicTypeInterpreter[F, FAILURE]): F[Unit] =
    run(appArgs).void

  def run(appArgs: List[String])(using c: AppCompiler[F], i: AppLogicTypeInterpreter[F, FAILURE]): F[ExitCode] =
    runRaw(appArgs)
      .map(i.isSuccess(_))
      .ifF(
        ifTrue  = ExitCode.Success,
        ifFalse = ExitCode.Error
      )

  def runRaw(appArgs: List[String])(using c: AppCompiler[F], i: AppLogicTypeInterpreter[F, FAILURE]): F[i.Result[Unit]] =
    compile(appArgs).useEval

  private def _updateFailureHandlerLoader(
    fh: AppResources[INFO, LOGGER_T[F], CONFIG, RESOURCES] => Endo[FailureHandler[F, FAILURE]]
  ): App[
    F,
    FAILURE,
    INFO,
    LOGGER_T,
    CONFIG,
    RESOURCES,
    DEPENDENCIES
  ] =
    copyWith(
      failureHandlerLoader = appRes => fh(appRes)(failureHandlerLoader(appRes))
    )

  private def copyWith[
    G[+_]: Async: Parallel,
    FAILURE2,
    APP_INFO2 <: SimpleAppInfo[?],
    LOGGER_T2[_[_]]: LoggerAdapter,
    CONFIG2: Show,
    RES2,
    DEPS2
  ](
    appInfo: APP_INFO2                                                                                         = this.info,
    appMessages: AppMessages                                                                                   = this.messages,
    loggerBuilder: G[LOGGER_T2[G]]                                                                             = this.loggerBuilder,
    configLoader: Resource[G, CONFIG2]                                                                         = this.configLoader,
    resourcesLoader: Resource[G, RES2]                                                                         = this.resourcesLoader,
    beforeProvidingF: AppDependencies[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[Unit]                = this.beforeProvidingF,
    onFinalizeF: AppDependencies[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[Unit]                     = this.onFinalizeF,
    failureHandlerLoader: AppResources[APP_INFO2, LOGGER_T2[G], CONFIG2, RES2] => FailureHandler[G, FAILURE2]  = this.failureHandlerLoader,
    dependenciesLoader: AppResources[APP_INFO2, LOGGER_T2[G], CONFIG2, RES2] => Resource[G, FAILURE2 \/ DEPS2] = this.depsLoader,
    provideBuilder: AppDependencies[APP_INFO2, LOGGER_T2[G], CONFIG2, DEPS2, RES2] => G[FAILURE2 \/ List[G[FAILURE2 \/ Unit]]] = this.servicesBuilder
  ): App[G, FAILURE2, APP_INFO2, LOGGER_T2, CONFIG2, RES2, DEPS2] =
    new App[G, FAILURE2, APP_INFO2, LOGGER_T2, CONFIG2, RES2, DEPS2](
      info                 = appInfo,
      messages             = appMessages,
      loggerBuilder        = loggerBuilder,
      configLoader         = configLoader,
      resourcesLoader      = resourcesLoader,
      beforeProvidingF     = beforeProvidingF,
      onFinalizeF          = onFinalizeF,
      failureHandlerLoader = failureHandlerLoader,
      depsLoader           = dependenciesLoader,
      servicesBuilder      = provideBuilder
    )

object App:

  def apply[F[+_]: Async: Parallel]: AppBuilder[F, Nothing] =
    AppBuilder[F]

  def apply[F[+_]: Async: Parallel, FAILURE: ClassTag]: AppBuilder[F, FAILURE] =
    AppBuilder[F, FAILURE]
