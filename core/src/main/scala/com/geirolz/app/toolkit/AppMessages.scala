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
  appAnErrorOccurred: String,
  appAFailureOccurred: String,
  shuttingDownApp: String
)
object AppMessages:

  def fromAppInfo[INFO <: SimpleAppInfo[?]](info: INFO)(
    f: INFO => AppMessages
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
        appAnErrorOccurred           = s"Error occurred.",
        appAFailureOccurred          = s"Failure occurred.",
        shuttingDownApp              = s"Shutting down ${info.name}..."
      )
    )
