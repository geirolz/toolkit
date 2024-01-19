package com.geirolz.app.toolkit

import scala.annotation.targetName

@targetName("Either")
type \/[+A, +B] = Either[A, B]


