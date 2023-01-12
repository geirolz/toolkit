package com.geirolz.app.toolkit

case class AppDependencies[APP_INFO <: BasicAppInfo[?], LOGGER, CONFIG, DEPS](
  resources: AppResources[APP_INFO, LOGGER, CONFIG],
  dependencies: DEPS
)
