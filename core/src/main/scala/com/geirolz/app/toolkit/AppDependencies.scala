package com.geirolz.app.toolkit

import cats.syntax.all.given

final case class AppDependencies[APP_INFO <: SimpleAppInfo[?], LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
  private val _resources: AppResources[APP_INFO, LOGGER, CONFIG, RESOURCES],
  private val _dependencies: DEPENDENCIES
):
  // proxies
  val info: APP_INFO             = _resources.info
  val args: AppArgs              = _resources.args
  val logger: LOGGER             = _resources.logger
  val config: CONFIG             = _resources.config
  val resources: RESOURCES       = _resources.resources
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

  private[toolkit] def apply[APP_INFO <: SimpleAppInfo[?], LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
    resources: AppResources[APP_INFO, LOGGER, CONFIG, RESOURCES],
    dependencies: DEPENDENCIES
  ): AppDependencies[APP_INFO, LOGGER, CONFIG, DEPENDENCIES, RESOURCES] =
    new AppDependencies[APP_INFO, LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
      _resources    = resources,
      _dependencies = dependencies
    )

  def unapply[APP_INFO <: SimpleAppInfo[?], LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
    deps: AppDependencies[APP_INFO, LOGGER, CONFIG, DEPENDENCIES, RESOURCES]
  ): Option[(APP_INFO, AppArgs, LOGGER, CONFIG, RESOURCES, DEPENDENCIES)] =
    (
      deps.info,
      deps.args,
      deps.logger,
      deps.config,
      deps.resources,
      deps.dependencies
    ).some
