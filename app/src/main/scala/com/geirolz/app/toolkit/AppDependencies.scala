package com.geirolz.app.toolkit

case class AppDependencies[APP_INFO <: AppInfo[?], LOGGER, CONFIG, DEPS](
  resources: AppResources[APP_INFO, LOGGER, CONFIG],
  dependencies: DEPS
)
