package com.geirolz.app.toolkit

import cats.syntax.all.given
import com.geirolz.app.toolkit.novalues.{NoDependencies, NoResources}

final case class AppContext[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
  info: INFO,
  messages: AppMessages,
  args: AppArgs,
  logger: LOGGER,
  config: CONFIG,
  dependencies: DEPENDENCIES,
  resources: RESOURCES
) {
  type AppInfo      = INFO
  type Logger       = LOGGER
  type Config       = CONFIG
  type Dependencies = DEPENDENCIES
  type Resources    = RESOURCES

  def withDependencies[D](newDependencies: D): AppContext[INFO, LOGGER, CONFIG, D, RESOURCES] =
    AppContext(
      info         = info,
      messages     = messages,
      args         = args,
      logger       = logger,
      config       = config,
      dependencies = newDependencies,
      resources    = resources
    )

  override def toString: String =
    s"""AppContext(
       |  info = $info,
       |  args = $args,
       |  logger = $logger,
       |  config = $config,
       |  dependencies = $dependencies,
       |  resources = $resources
       |)""".stripMargin
}

object AppContext:

  type NoDeps[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES] =
    AppContext[INFO, LOGGER, CONFIG, NoDependencies, RESOURCES]

  type NoDepsAndRes[INFO <: SimpleAppInfo[?], LOGGER, CONFIG] =
    NoDeps[INFO, LOGGER, CONFIG, NoResources]

  private[toolkit] def noDependencies[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, RESOURCES](
    info: INFO,
    messages: AppMessages,
    args: AppArgs,
    logger: LOGGER,
    config: CONFIG,
    resources: RESOURCES
  ): NoDeps[INFO, LOGGER, CONFIG, RESOURCES] =
    apply(
      info         = info,
      messages     = messages,
      args         = args,
      logger       = logger,
      config       = config,
      dependencies = NoDependencies.value,
      resources    = resources
    )

  private[toolkit] def apply[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
    info: INFO,
    messages: AppMessages,
    args: AppArgs,
    logger: LOGGER,
    config: CONFIG,
    dependencies: DEPENDENCIES,
    resources: RESOURCES
  ): AppContext[INFO, LOGGER, CONFIG, DEPENDENCIES, RESOURCES] =
    new AppContext[INFO, LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
      info         = info,
      messages     = messages,
      args         = args,
      logger       = logger,
      config       = config,
      dependencies = dependencies,
      resources    = resources
    )

  def unapply[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, DEPENDENCIES, RESOURCES](
    res: AppContext[INFO, LOGGER, CONFIG, DEPENDENCIES, RESOURCES]
  ): Option[(INFO, AppMessages, AppArgs, LOGGER, CONFIG, DEPENDENCIES, RESOURCES)] =
    (
      res.info,
      res.messages,
      res.args,
      res.logger,
      res.config,
      res.dependencies,
      res.resources
    ).some
