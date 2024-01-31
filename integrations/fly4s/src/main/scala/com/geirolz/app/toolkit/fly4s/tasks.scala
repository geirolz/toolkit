package com.geirolz.app.toolkit.fly4s
import _root_.fly4s.core.Fly4s
import _root_.fly4s.core.data.Fly4sConfig
import cats.effect.Resource
import cats.effect.kernel.Async
import com.geirolz.app.toolkit.logger.LoggerAdapter
import cats.syntax.all.*
import com.geirolz.app.toolkit.*

def migrateDatabaseWith[F[_]: Async, INFO <: SimpleAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG, DEPENDENCIES, RESOURCES](
  url: String,
  user: Option[String]          = None,
  password: Option[Array[Char]] = None,
  config: Fly4sConfig           = Fly4sConfig.default,
  classLoader: ClassLoader      = Thread.currentThread.getContextClassLoader
)(using AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES]): F[Unit] =
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
)(using AppContext[INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES]): F[Unit] =
  fly4s
    .evalMap(fl4s =>
      for {
        logger <- LoggerAdapter[LOGGER_T].toToolkit(ctx.logger).pure[F]
        _      <- logger.debug(s"Applying migration to database...")
        result <- fl4s.migrate.onError(logger.error(_)(s"Unable to apply database migrations to database."))
        _      <- logger.info(s"Applied ${result.migrationsExecuted} migrations to database.")
      } yield ()
    )
    .use_
