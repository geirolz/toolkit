package com.geirolz.app.toolkit
import _root_.fly4s.core.Fly4s
import _root_.fly4s.core.data.Fly4sConfig
import cats.effect.Resource
import cats.effect.kernel.Async
import com.geirolz.app.toolkit.logger.LoggerAdapter

package object fly4s {

  import cats.syntax.all.*

  def migrateDatabaseWithConfig[F[_]: Async, APP_INFO <: SimpleAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG, DEPENDENCIES, RESOURCES](
    url: CONFIG => String,
    user: CONFIG => Option[String]          = (_: CONFIG) => None,
    password: CONFIG => Option[Array[Char]] = (_: CONFIG) => None,
    config: CONFIG => Fly4sConfig           = (_: CONFIG) => Fly4sConfig.default,
    classLoader: ClassLoader                = Thread.currentThread.getContextClassLoader
  ): App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit] =
    migrateDatabaseWith(
      url         = d => url(d.config),
      user        = d => user(d.config),
      password    = d => password(d.config),
      config      = d => config(d.config),
      classLoader = classLoader
    )

  def migrateDatabaseWith[F[_]: Async, APP_INFO <: SimpleAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG, DEPENDENCIES, RESOURCES](
    url: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => String,
    user: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => Option[String]          = (_: Any) => None,
    password: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => Option[Array[Char]] = (_: Any) => None,
    config: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => Fly4sConfig           = (_: Any) => Fly4sConfig.default,
    classLoader: ClassLoader = Thread.currentThread.getContextClassLoader
  ): App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit] =
    migrateDatabase(dep =>
      Fly4s
        .make[F](
          url         = url(dep),
          user        = user(dep),
          password    = password(dep),
          config      = config(dep),
          classLoader = classLoader
        )
    )

  def migrateDatabase[F[_]: Async, APP_INFO <: SimpleAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG, DEPENDENCIES, RESOURCES](
    f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => Resource[F, Fly4s[F]]
  ): App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => F[Unit] =
    dep =>
      f(dep)
        .evalMap(fl4s =>
          for {
            logger <- LoggerAdapter[LOGGER_T].toToolkit(dep.logger).pure[F]
            _      <- logger.debug(s"Applying migration to database...")
            result <- fl4s.migrate.onError(logger.error(_)(s"Unable to apply database migrations to database."))
            _      <- logger.info(s"Applied ${result.migrationsExecuted} migrations to database.")
          } yield ()
        )
        .use_
}
