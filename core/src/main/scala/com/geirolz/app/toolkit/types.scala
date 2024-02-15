package com.geirolz.app.toolkit

import scala.annotation.targetName

@targetName("Either")
type \/[+A, +B] = Either[A, B]

inline def ctx[INFO <: SimpleAppInfo[?], LOGGER, CONFIG, DEPENDENCIES, RESOURCES](using
  c: AppContext[INFO, LOGGER, CONFIG, DEPENDENCIES, RESOURCES]
): AppContext[INFO, LOGGER, CONFIG, DEPENDENCIES, RESOURCES] = c
