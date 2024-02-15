package com.geirolz.app.toolkit.config.pureconfig

import _root_.pureconfig.{ConfigObjectSource, ConfigReader, ConfigSource}
import cats.effect.Async

import scala.reflect.ClassTag

def pureconfigLoader[F[_]: Async, PURE_CONFIG: ClassTag: ConfigReader]: F[PURE_CONFIG] =
  pureconfigLoader(_.default)

def pureconfigLoader[F[_]: Async, PURE_CONFIG: ClassTag: ConfigReader](f: ConfigSource.type => ConfigObjectSource): F[PURE_CONFIG] =
  pureconfigLoader(f(ConfigSource))

def pureconfigLoader[F[_]: Async, PURE_CONFIG: ClassTag: ConfigReader](appSource: ConfigObjectSource): F[PURE_CONFIG] =
  Async[F].delay(appSource.loadOrThrow[PURE_CONFIG])
