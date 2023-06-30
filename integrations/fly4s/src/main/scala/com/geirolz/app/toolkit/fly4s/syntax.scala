package com.geirolz.app.toolkit.fly4s

import cats.effect.Resource
import cats.effect.kernel.Async
import com.geirolz.app.toolkit.{App, SimpleAppInfo}
import fly4s.core.Fly4s
import fly4s.core.data.Fly4sConfig

object syntax extends AllSyntax
private[fly4s] sealed trait AllSyntax {

  import cats.syntax.all.*

  implicit class AppResourcesLoaderOps[F[+_]: Async, FAILURE, APP_INFO <: SimpleAppInfo[?], LOGGER_T[_[_]], CONFIG, RESOURCES, DEPENDENCIES](
    app: App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES]
  ) {

    def beforeProvidingMigrateDatabaseWithConfig(
      url: CONFIG => String,
      user: CONFIG => Option[String]          = _ => None,
      password: CONFIG => Option[Array[Char]] = _ => None,
      config: Fly4sConfig                     = Fly4sConfig.default,
      classLoader: ClassLoader                = Thread.currentThread.getContextClassLoader
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      beforeProvidingMigrateDatabaseWith(
        url         = d => url(d.config),
        user        = d => user(d.config),
        password    = d => password(d.config),
        config      = config,
        classLoader = classLoader
      )

    def beforeProvidingMigrateDatabaseWith(
      url: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => String,
      user: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => Option[String]          = _ => None,
      password: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => Option[Array[Char]] = _ => None,
      config: Fly4sConfig                                                                                       = Fly4sConfig.default,
      classLoader: ClassLoader = Thread.currentThread.getContextClassLoader
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      beforeProvidingMigrateDatabase(dep =>
        Fly4s
          .make[F](
            url         = url(dep),
            user        = user(dep),
            password    = password(dep),
            config      = config,
            classLoader = classLoader
          )
      )

    def beforeProvidingMigrateDatabase(
      f: App.Dependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES, RESOURCES] => Resource[F, Fly4s[F]]
    ): App[F, FAILURE, APP_INFO, LOGGER_T, CONFIG, RESOURCES, DEPENDENCIES] =
      app.beforeProviding(dep => f(dep).use(_.migrate).void)
  }
}
