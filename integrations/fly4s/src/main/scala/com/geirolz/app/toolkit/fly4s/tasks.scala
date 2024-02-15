package com.geirolz.app.toolkit.fly4s
import _root_.fly4s.core.Fly4s
import _root_.fly4s.core.data.Fly4sConfig
import cats.effect.Resource
import cats.effect.kernel.Async
import cats.syntax.all.*
import com.geirolz.app.toolkit.*
import com.geirolz.app.toolkit.logger.LoggerAdapter

def migrateDatabaseWith[F[_]: Async, INFO <: SimpleAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG, DEPENDENCIES, RESOURCES](
  url: String,
  user: Option[String]          = None,
  password: Option[Array[Char]] = None,
  config: Fly4sConfig           = Fly4sConfig.default,
  classLoader: ClassLoader      = Thread.currentThread.getContextClassLoader
)(using c: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES], msgs: Fly4sAppMessages): F[Unit] =
  migrateDatabase(
    Fly4s.make[F](
      url         = url,
      user        = user,
      password    = password,
      config      = config,
      classLoader = classLoader
    )
  )

def migrateDatabase[F[_]: Async, INFO <: SimpleAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG, DEPENDENCIES, RESOURCES](
  fly4s: Resource[F, Fly4s[F]]
)(using c: AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES], msgs: Fly4sAppMessages): F[Unit] =
  fly4s
    .evalMap(fl4s =>
      for {
        logger <- LoggerAdapter[LOGGER_T].toToolkit(ctx.logger).pure[F]
        _      <- logger.debug(msgs.applyingMigrations)
        result <- fl4s.migrate.onError(logger.error(_)(msgs.failedToApplyMigrations))
        _      <- logger.info(msgs.successfullyApplied(result))
      } yield ()
    )
    .use_
