package com.geirolz.app.toolkit

import cats.{Parallel, Show}
import cats.data.{EitherT, NonEmptyList}
import cats.effect.implicits.{genSpawnOps, monadCancelOps_}
import cats.effect.kernel.MonadCancelThrow
import cats.effect.{Async, Fiber, Ref, Resource}
import com.geirolz.app.toolkit.FailureHandler.OnFailureBehaviour
import com.geirolz.app.toolkit.logger.LoggerAdapter

trait AppInterpreter[F[+_]]:

  def run[T](compiledApp: Resource[F, F[T]])(implicit F: MonadCancelThrow[F]): F[T]

  def compile[
    FAILURE,
    APP_INFO <: SimpleAppInfo[?],
    LOGGER_T[_[_]]: LoggerAdapter,
    CONFIG: Show,
    RESOURCES,
    DEPENDENCIES
  ](appArgs: List[String], app: App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES])(implicit
    F: Async[F],
    P: Parallel[F]
  ): Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ Unit]]

object AppInterpreter:

  import cats.syntax.all.*

  def apply[F[+_]](implicit ac: AppInterpreter[F]): AppInterpreter[F] = ac

  given [F[+_]]: AppInterpreter[F] = new AppInterpreter[F] {

    override def run[T](compiledApp: Resource[F, F[T]])(implicit F: MonadCancelThrow[F]): F[T] = compiledApp.useEval

    override def compile[FAILURE, APP_INFO <: SimpleAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG: Show, RESOURCES, DEPENDENCIES](
      appArgs: List[String],
      app: App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
    )(implicit F: Async[F], P: Parallel[F]): Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ Unit]] =
      (
        for {

          // -------------------- RESOURCES-------------------
          // logger
          userLogger <- EitherT.right[FAILURE](Resource.eval(app.loggerBuilder))
          toolkitLogger = LoggerAdapter[LOGGER_T].toToolkit[F](userLogger)
          toolkitResLogger = toolkitLogger.mapK(
            Resource.liftK[F].andThen(EitherT.liftK[Resource[F, *], FAILURE])
          )

          // config
          _         <- toolkitResLogger.debug(app.appMessages.loadingConfig)
          appConfig <- EitherT.right[FAILURE](app.configLoader)
          _         <- toolkitResLogger.info(app.appMessages.configSuccessfullyLoaded)
          _         <- toolkitResLogger.info(appConfig.show)

          // other resources
          otherResources <- EitherT.right[FAILURE](app.resourcesLoader)

          // group resources
          appResources: App.Resources[APP_INFO, LOGGER_T[F], CONFIG, RESOURCES] = App.Resources(
            info      = app.appInfo,
            args      = AppArgs(appArgs),
            logger    = userLogger,
            config    = appConfig,
            resources = otherResources
          )

          // ------------------- DEPENDENCIES -----------------
          _              <- toolkitResLogger.debug(app.appMessages.buildingServicesEnv)
          appDepServices <- EitherT(app.dependenciesLoader(appResources))
          _              <- toolkitResLogger.info(app.appMessages.servicesEnvSuccessfullyBuilt)
          appDependencies = App.Dependencies(appResources, appDepServices)

          // --------------------- SERVICES -------------------
          _               <- toolkitResLogger.debug(app.appMessages.buildingApp)
          appProvServices <- EitherT(Resource.eval(app.provideBuilder(appDependencies)))
          _               <- toolkitResLogger.info(app.appMessages.appSuccessfullyBuilt)

          // --------------------- APP ------------------------
          appLogic = for {
            fibers   <- Ref[F].of(List.empty[Fiber[F, Throwable, Unit]])
            failures <- Ref[F].of(List.empty[FAILURE])
            failureHandler = app.failureHandlerLoader(appResources)
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
          toolkitLogger.info(app.appMessages.startingApp) >>
          app.beforeProvidingF(appDependencies) >>
          appLogic
            .onCancel(toolkitLogger.info(app.appMessages.appWasStopped))
            .onError(e => toolkitLogger.error(e)(app.appMessages.appEnErrorOccurred))
            .guarantee(
              app.onFinalizeF(appDependencies) >> toolkitLogger.info(app.appMessages.shuttingDownApp)
            )
        }
      ).value
  }
