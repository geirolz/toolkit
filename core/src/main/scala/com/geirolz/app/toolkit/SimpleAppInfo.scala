package com.geirolz.app.toolkit

import cats.Show
import cats.implicits.showInterpolator

import java.time.LocalDateTime

trait SimpleAppInfo[T]:
  val name: T
  val version: T
  val scalaVersion: T
  val sbtVersion: T
  val buildRefName: T
  val builtOn: LocalDateTime

object SimpleAppInfo:

  def apply[T: Show](
    name: T,
    version: T,
    scalaVersion: T,
    sbtVersion: T,
    buildRefName: T,
    builtOn: LocalDateTime = LocalDateTime.now()
  ): SimpleAppInfo[T] =
    new SimpleAppInfoImpl[T](
      name         = name,
      version      = version,
      scalaVersion = scalaVersion,
      sbtVersion   = sbtVersion,
      buildRefName = buildRefName,
      builtOn      = builtOn
    )

  def of[T: Show](
    name: T,
    version: T,
    scalaVersion: T,
    sbtVersion: T,
    buildRefName: (T, T, LocalDateTime) => T,
    builtOn: LocalDateTime = LocalDateTime.now()
  ): SimpleAppInfo[T] =
    SimpleAppInfo[T](
      name         = name,
      version      = version,
      scalaVersion = scalaVersion,
      sbtVersion   = sbtVersion,
      buildRefName = buildRefName(name, version, builtOn),
      builtOn      = builtOn
    )

  def string(
    name: String,
    version: String,
    scalaVersion: String,
    sbtVersion: String,
    builtOn: LocalDateTime
  ): SimpleAppInfo[String] =
    SimpleAppInfo.of[String](
      name         = name,
      version      = version,
      scalaVersion = scalaVersion,
      sbtVersion   = sbtVersion,
      buildRefName = genRefNameString[String](_, _, _),
      builtOn      = builtOn
    )

  private class SimpleAppInfoImpl[T](
    val name: T,
    val version: T,
    val scalaVersion: T,
    val sbtVersion: T,
    val buildRefName: T,
    val builtOn: LocalDateTime
  ) extends SimpleAppInfo[T]

  def genRefNameString[T: Show](name: T, version: T, builtOn: LocalDateTime): String =
    given Show[LocalDateTime] = Show.fromToString[LocalDateTime]
    show"$name:$version-$builtOn"
