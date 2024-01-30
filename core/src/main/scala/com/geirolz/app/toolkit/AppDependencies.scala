package com.geirolz.app.toolkit

import cats.syntax.all.given

final case class AppDependencies[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
  private val _context: AppContext[INFO, LOGGER, CONFIG, RESOURCES],
  private val _dependencies: DEPENDENCIES
):

  export _context.*
  val dependencies: DEPENDENCIES = _dependencies

  override def toString: String =
    s"""AppDependencies(
       |  info = $info,
       |  args = $args,
       |  logger = $logger,
       |  config = $config,
       |  resources = $resources,
       |  dependencies = $dependencies
       |)""".stripMargin

object AppDependencies:

  private[toolkit] def apply[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
    resources: AppContext[INFO, LOGGER, CONFIG, RESOURCES],
    dependencies: DEPENDENCIES
  ): AppDependencies[INFO, LOGGER, CONFIG, DEPENDENCIES, RESOURCES] =
    new AppDependencies[INFO, LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
      _context      = resources,
      _dependencies = dependencies
    )

  def unapply[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
    deps: AppDependencies[INFO, LOGGER, CONFIG, DEPENDENCIES, RESOURCES]
  ): Option[(INFO, AppArgs, LOGGER, CONFIG, RESOURCES, DEPENDENCIES)] =
    (
      deps.info,
      deps.args,
      deps.logger,
      deps.config,
      deps.resources,
      deps.dependencies
    ).some
