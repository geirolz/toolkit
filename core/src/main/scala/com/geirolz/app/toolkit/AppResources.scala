package com.geirolz.app.toolkit

import cats.syntax.all.given

final case class AppResources[APP_INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES](
  info: APP_INFO,
  args: AppArgs,
  logger: LOGGER,
  config: CONFIG,
  resources: RESOURCES
) {
  type AppInfo   = APP_INFO
  type Logger    = LOGGER
  type Config    = CONFIG
  type Resources = RESOURCES

  override def toString: String =
    s"""AppDependencies(
       |  info = $info,
       |  args = $args,
       |  logger = $logger,
       |  config = $config,
       |  resources = $resources
       |)""".stripMargin
}

object AppResources:

  private[toolkit] def apply[APP_INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES](
    info: APP_INFO,
    args: AppArgs,
    logger: LOGGER,
    config: CONFIG,
    resources: RESOURCES
  ): AppResources[APP_INFO, LOGGER, CONFIG, RESOURCES] =
    new AppResources[APP_INFO, LOGGER, CONFIG, RESOURCES](
      info      = info,
      args      = args,
      logger    = logger,
      config    = config,
      resources = resources
    )

  def unapply[APP_INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES](
    res: AppResources[APP_INFO, LOGGER, CONFIG, RESOURCES]
  ): Option[(APP_INFO, AppArgs, LOGGER, CONFIG, RESOURCES)] =
    (
      res.info,
      res.args,
      res.logger,
      res.config,
      res.resources
    ).some
