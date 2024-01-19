package com.geirolz.app.toolkit

import cats.data.NonEmptyList
import cats.effect.*
import cats.{Endo, Foldable, Parallel, Semigroup, Show}
import com.geirolz.app.toolkit.FailureHandler.OnFailureBehaviour
import com.geirolz.app.toolkit.error.MultiException
import com.geirolz.app.toolkit.logger.{LoggerAdapter, NoopLogger}
import com.geirolz.app.toolkit.novalues.{NoConfig, NoDependencies, NoResources}

import scala.util.NotGiven
import cats.syntax.all.given

class App[
  F[+_]: Async: Parallel,
  FAILURE,
  APP_INFO <: SimpleAppInfo[?],
  LOGGER_T[_[_]]: LoggerAdapter,
  CONFIG: Show,
  RESOURCES,
  DEPENDENCIES
] private[toolkit] (
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
  type AppInfo               = APP_INFO
  type Logger                = LOGGER_T[F]
  type Config                = CONFIG
  type Self                  = App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
  private type SelfResources = AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES]

  class Resourced[T](val appRes: SelfResources, val value: T):
    export appRes.*
    def map[U](f: T => U): Resourced[U]                                           = Resourced(appRes, f(value))
    def tupled: (SelfResources, T)                                                = (appRes, value)
    def useTupled[U](f: (SelfResources, T) => U): U                               = f.tupled(tupled)
    def use[U](f: SelfResources => T => U): U                                     = f(appRes)(value)
    def tupledAll: (APP_INFO, CONFIG, LOGGER_T[F], RESOURCES, T)                  = (info, config, logger, resources, value)
    def useTupledAll[U](f: (APP_INFO, CONFIG, LOGGER_T[F], RESOURCES, T) => U): U = f.tupled(tupledAll)

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

  def apply[F[+_]: Async: Parallel](using DummyImplicit): AppBuilder[F, Throwable] =
    AppBuilder[F, Throwable]

  def apply[F[+_]: Async: Parallel, FAILURE]: AppBuilder[F, FAILURE] =
    AppBuilder[F, FAILURE]

sealed trait AppSyntax:

  import cats.syntax.all.*

  extension [
    F[+_]: Async: Parallel,
    FAILURE,
    APP_INFO <: SimpleAppInfo[?],
    LOGGER_T[_[_]]: LoggerAdapter,
    CONFIG: Show,
    RESOURCES,
    DEPENDENCIES
  ](app: App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES])

    // -------------------- AppFailureSyntax --------------------
    // failures
    def mapFailure[FAILURE2](
      fhLoader: AppResources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] => FailureHandler[F, FAILURE2]
    )(f: FAILURE => FAILURE2)(using NotGiven[FAILURE =:= Throwable]): App[F, FAILURE2, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      app.copyWith[F, FAILURE2, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES](
        failureHandlerLoader = fhLoader,
        dependenciesLoader   = app.dependenciesLoader.andThen(_.map(_.leftMap(f))),
        provideBuilder       = app.provideBuilder.andThen(_.map(_.leftMap(f).map(_.map(_.map(_.leftMap(f))))))
      )

    def onFailure_(
      f: app.Resourced[FAILURE] => F[Unit]
    )(using NotGiven[FAILURE =:= Throwable]): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      app._updateFailureHandlerLoader(appRes =>
        _.onFailure(failure => f(app.Resourced(appRes, failure)) >> app.failureHandlerLoader(appRes).onFailureF(failure))
      )

    def onFailure(
      f: app.Resourced[FAILURE] => F[OnFailureBehaviour]
    )(using NotGiven[FAILURE =:= Throwable]): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      app._updateFailureHandlerLoader(appRes => _.onFailure(f.compose(app.Resourced(appRes, _))))

    def handleFailureWith(
      f: app.Resourced[FAILURE] => F[FAILURE \/ Unit]
    )(using NotGiven[FAILURE =:= Throwable]): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      app._updateFailureHandlerLoader(appRes => _.handleFailureWith(f.compose(app.Resourced(appRes, _))))

    // compile and run
    def compile(
      appArgs: List[String]
    )(using AppCompiler[F], NotGiven[FAILURE =:= Throwable]): Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ Unit]] =
      AppCompiler[F].compile(appArgs, app)

    def run(appArgs: List[String])(using AppCompiler[F], NotGiven[FAILURE =:= Throwable]): F[ExitCode] =
      runMap[ExitCode](appArgs).apply {
        case Left(_)  => ExitCode.Error
        case Right(_) => ExitCode.Success
      }

    def runReduce[B](appArgs: List[String], f: FAILURE \/ Unit => B)(using
      AppCompiler[F],
      Semigroup[FAILURE],
      NotGiven[FAILURE =:= Throwable]
    ): F[B] =
      runMap[FAILURE \/ Unit](appArgs)
        .apply {
          case Left(failures) => Left(failures.reduce)
          case Right(_)       => Right(())
        }
        .map(f)

    def runRaw(appArgs: List[String])(using AppCompiler[F], NotGiven[FAILURE =:= Throwable]): F[NonEmptyList[FAILURE] \/ Unit] =
      runMap[NonEmptyList[FAILURE] \/ Unit](appArgs)(identity)

    def runMap[B](appArgs: List[String])(f: NonEmptyList[FAILURE] \/ Unit => B)(using AppCompiler[F], NotGiven[FAILURE =:= Throwable]): F[B] =
      AppCompiler[F].run[B](
        this.compile(appArgs).map {
          case Left(failure)   => f(Left(NonEmptyList.one(failure))).pure[F]
          case Right(appLogic) => appLogic.map(f)
        }
      )

    // -------------------- AppThrowableSyntax --------------------
    def compile(appArgs: List[String])(using AppCompiler[F])(using env: FAILURE =:= Throwable): Resource[F, F[Unit]] =
      AppCompiler[F].compile(appArgs, app).flatMap {
        case Left(failure) =>
          Resource.raiseError(env(failure))
        case Right(value) =>
          Resource.pure(value.flatMap {
            case Left(failures: NonEmptyList[Throwable]) =>
              MultiException.fromNel(failures).raiseError[F, Unit]
            case Right(value) => value.pure[F]
          })
      }

      def run_(using AppCompiler[F], FAILURE =:= Throwable): F[Unit] =
        run().void

      def run(appArgs: List[String] = Nil)(using AppCompiler[F], FAILURE =:= Throwable): F[ExitCode] =
        AppCompiler[F].run(compile(appArgs)).as(ExitCode.Success)
