package com.geirolz.app.toolkit.config.pureconfig

import cats.Show
import cats.effect.kernel.Async
import com.geirolz.app.toolkit.{AppResources, SimpleAppInfo}
import pureconfig.{ConfigReader, ConfigSource}

import scala.reflect.ClassTag

object syntax extends AllSyntax
private[pureconfig] sealed trait AllSyntax {
  implicit class AppResourcesLoaderOps[F[_], APP_INFO <: SimpleAppInfo[?], LOGGER_T[
    _[_]
  ], CONFIG](arp: AppResources.Loader[F, APP_INFO, LOGGER_T, CONFIG]) {

    def withPureConfigLoader[PURE_CONFIG: ClassTag: ConfigReader: Show](implicit
      F: Async[F]
    ): AppResources.Loader[F, APP_INFO, LOGGER_T, PURE_CONFIG] =
      arp.withConfigLoader(_ => F.delay(ConfigSource.default.loadOrThrow[PURE_CONFIG]))
  }
}
