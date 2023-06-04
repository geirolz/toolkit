package com.geirolz.app.toolkit.config.pureconfig

import cats.Show
import cats.effect.Async
import com.geirolz.app.toolkit.{App, SimpleAppInfo}
import pureconfig.{ConfigReader, ConfigSource}

import scala.reflect.ClassTag

object syntax extends AllSyntax
private[pureconfig] sealed trait AllSyntax {

  implicit class AppResourcesLoaderOps[F[+_], FAILURE, APP_INFO <: SimpleAppInfo[?], LOGGER_T[_[_]], RESOURCES](
    arp: App.AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, ?, RESOURCES]
  ) {

    def withPureConfigLoader[PURE_CONFIG: ClassTag: ConfigReader: Show](implicit
      F: Async[F]
    ): App.AppBuilderSelectResAndDeps[F, FAILURE, APP_INFO, LOGGER_T, PURE_CONFIG, RESOURCES] =
      arp.withConfigLoader(_ => F.delay(ConfigSource.default.loadOrThrow[PURE_CONFIG]))
  }
}
