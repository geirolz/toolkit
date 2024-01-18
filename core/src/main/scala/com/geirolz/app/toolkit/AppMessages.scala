package com.geirolz.app.toolkit

case class AppMessages(
  loadingConfig: String,
  configSuccessfullyLoaded: String,
  buildingServicesEnv: String,
  servicesEnvSuccessfullyBuilt: String,
  buildingApp: String,
  appSuccessfullyBuilt: String,
  startingApp: String,
  appWasStopped: String,
  appEnErrorOccurred: String,
  shuttingDownApp: String
)
object AppMessages:

  def fromAppInfo[APP_INFO <: SimpleAppInfo[?]](info: APP_INFO)(
    f: APP_INFO => AppMessages
  ): AppMessages = f(info)

  def default(info: SimpleAppInfo[?]): AppMessages =
    AppMessages.fromAppInfo(info)(info =>
      AppMessages(
        loadingConfig                = "Loading configuration...",
        configSuccessfullyLoaded     = "Configuration successfully loaded.",
        buildingServicesEnv          = "Building services environment...",
        servicesEnvSuccessfullyBuilt = "Services environment successfully built.",
        buildingApp                  = "Building App...",
        appSuccessfullyBuilt         = "App successfully built.",
        startingApp                  = s"Starting ${info.buildRefName}...",
        appWasStopped                = s"${info.name} was stopped.",
        appEnErrorOccurred           = s"${info.name} was stopped due an error.",
        shuttingDownApp              = s"Shutting down ${info.name}..."
      )
    )
