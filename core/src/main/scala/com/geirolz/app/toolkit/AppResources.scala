package com.geirolz.app.toolkit

import cats.syntax.all.given

final case class AppResources[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES](
  info: INFO,
  messages: AppMessages,
  args: AppArgs,
  logger: LOGGER,
  config: CONFIG,
  resources: RESOURCES
) {
  type AppInfo   = INFO
  type Logger    = LOGGER
  type Config    = CONFIG
  type Resources = RESOURCES

  override def toString: String =
    s"""AppResources(
       |  info = $info,
       |  args = $args,
       |  logger = $logger,
       |  config = $config,
       |  resources = $resources
       |)""".stripMargin
}

object AppResources:

  private[toolkit] def apply[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES](
    info: INFO,
    messages: AppMessages,
    args: AppArgs,
    logger: LOGGER,
    config: CONFIG,
    resources: RESOURCES
  ): AppResources[INFO, LOGGER, CONFIG, RESOURCES] =
    new AppResources[INFO, LOGGER, CONFIG, RESOURCES](
      info      = info,
      messages  = messages,
      args      = args,
      logger    = logger,
      config    = config,
      resources = resources
    )

  def unapply[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES](
    res: AppResources[INFO, LOGGER, CONFIG, RESOURCES]
  ): Option[(INFO, AppMessages, AppArgs, LOGGER, CONFIG, RESOURCES)] =
    (
      res.info,
      res.messages,
      res.args,
      res.logger,
      res.config,
      res.resources
    ).some
