package com.geirolz.app.toolkit

import cats.data.{EitherT, NonEmptyList}
import cats.effect.implicits.{genSpawnOps, monadCancelOps_}
import cats.effect.{Async, Fiber, Ref, Resource}
import cats.{Parallel, Show}
import com.geirolz.app.toolkit.AppContext.NoDeps
import com.geirolz.app.toolkit.failure.FailureHandler.OnFailureBehaviour
import com.geirolz.app.toolkit.logger.LoggerAdapter
import com.geirolz.app.toolkit.novalues.NoDependencies

trait AppCompiler[F[+_]]:

  def compile[
    FAILURE,
    INFO <: SimpleAppInfo[?],
    LOGGER_T[_[_]]: LoggerAdapter,
    CONFIG: Show,
    RESOURCES,
    DEPENDENCIES
  ](appArgs: List[String], app: App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES])(using
    F: Async[F],
    P: Parallel[F]
  ): Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ Unit]]

object AppCompiler:

  import cats.syntax.all.*

  def apply[F[+_]](using ac: AppCompiler[F]): AppCompiler[F] = ac

  given [F[+_]]: AppCompiler[F] = new AppCompiler[F] {

    override def compile[FAILURE, INFO <: SimpleAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG: Show, RESOURCES, DEPENDENCIES](
      appArgs: List[String],
      app: App[F, FAILURE, INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
    )(using F: Async[F], P: Parallel[F]): Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ Unit]] =
      (
        for {

          // -------------------- CONTEXT -------------------
          // logger
          userLogger <- EitherT.right[FAILURE](Resource.eval(app.loggerBuilder))
          toolkitLogger = LoggerAdapter[LOGGER_T].toToolkit[F](userLogger)
          toolkitResLogger = toolkitLogger.mapK(
            Resource.liftK[F].andThen(EitherT.liftK[Resource[F, *], FAILURE])
          )

          // config
          _         <- toolkitResLogger.debug(app.messages.loadingConfig)
          appConfig <- EitherT.right[FAILURE](app.configLoader)
          _         <- toolkitResLogger.info(app.messages.configSuccessfullyLoaded)
          _         <- toolkitResLogger.info(appConfig.show)

          // group resources
          given AppContext.NoDeps[INFO, LOGGER_T[F], CONFIG, RESOURCES] <-
            EitherT.right[FAILURE](
              Resource.eval(
                app.resourcesLoader.use(otherResources =>
                  AppContext
                    .noDependencies(
                      info      = app.info,
                      messages  = app.messages,
                      args      = AppArgs(appArgs),
                      logger    = userLogger,
                      config    = appConfig,
                      resources = otherResources
                    )
                    .pure[F]
                )
              )
            )

          // ------------------- DEPENDENCIES -----------------
          _              <- toolkitResLogger.debug(app.messages.buildingServicesEnv)
          appDepServices <- EitherT(app.depsLoader)
          _              <- toolkitResLogger.info(app.messages.servicesEnvSuccessfullyBuilt)
          appContext = ctx.withDependencies(appDepServices)

          // --------------------- SERVICES -------------------
          _               <- toolkitResLogger.debug(app.messages.buildingApp)
          appProvServices <- EitherT(Resource.eval(app.servicesBuilder(appContext)))
          _               <- toolkitResLogger.info(app.messages.appSuccessfullyBuilt)

          // --------------------- APP ------------------------
          appLogic = for {
            fibers   <- Ref[F].of(List.empty[Fiber[F, Throwable, Unit]])
            failures <- Ref[F].of(List.empty[FAILURE])
            failureHandler = app.failureHandlerLoader
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
          toolkitLogger.info(app.messages.startingApp) >>
          app.beforeProvidingTask(appContext) >>
          appLogic
            .onCancel(toolkitLogger.info(app.messages.appWasStopped))
            .onError(e => toolkitLogger.error(e)(app.messages.appAnErrorOccurred))
            .guarantee(
              app.onFinalizeTask(appContext) >> toolkitLogger.info(app.messages.shuttingDownApp)
            )
        }
      ).value
  }
