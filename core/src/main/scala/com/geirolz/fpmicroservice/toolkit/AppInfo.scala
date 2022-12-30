package com.geirolz.fpmicroservice.toolkit

import java.time.LocalDateTime

trait AppInfo[T] {
  val name: T
  val description: T
  val boundedContext: T
  val tags: Seq[T]
  val version: T
  val scalaVersion: T
  val sbtVersion: T
  val javaVersion: Option[T]
  val builtOn: LocalDateTime
  val buildRefName: String = s"$name:$version-$builtOn"
}
object AppInfo {

  type AppInfo = AppInfo[String]

  def apply[T](
    name: T,
    description: T,
    boundedContext: T,
    tags: Seq[T],
    version: T,
    scalaVersion: T,
    sbtVersion: T,
    javaVersion: Option[T],
    builtOn: LocalDateTime
  ): AppInfo[T] =
    Basic[T](
      name           = name,
      description    = description,
      boundedContext = boundedContext,
      tags           = tags,
      version        = version,
      scalaVersion   = scalaVersion,
      sbtVersion     = sbtVersion,
      javaVersion    = javaVersion,
      builtOn        = builtOn
    )

  private case class Basic[T](
    name: T,
    description: T,
    boundedContext: T,
    tags: Seq[T],
    version: T,
    scalaVersion: T,
    sbtVersion: T,
    javaVersion: Option[T],
    builtOn: LocalDateTime
  ) extends AppInfo[T]
}
