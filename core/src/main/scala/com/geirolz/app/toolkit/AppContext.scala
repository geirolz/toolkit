package com.geirolz.app.toolkit

import cats.syntax.all.given

final case class AppContext[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES](
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
    s"""AppContext(
       |  info = $info,
       |  args = $args,
       |  logger = $logger,
       |  config = $config,
       |  resources = $resources
       |)""".stripMargin
}

object AppContext:

  private[toolkit] def apply[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES](
    info: INFO,
    messages: AppMessages,
    args: AppArgs,
    logger: LOGGER,
    config: CONFIG,
    resources: RESOURCES
  ): AppContext[INFO, LOGGER, CONFIG, RESOURCES] =
    new AppContext[INFO, LOGGER, CONFIG, RESOURCES](
      info      = info,
      messages  = messages,
      args      = args,
      logger    = logger,
      config    = config,
      resources = resources
    )

  def unapply[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES](
    res: AppContext[INFO, LOGGER, CONFIG, RESOURCES]
  ): Option[(INFO, AppMessages, AppArgs, LOGGER, CONFIG, RESOURCES)] =
    (
      res.info,
      res.messages,
      res.args,
      res.logger,
      res.config,
      res.resources
    ).some
