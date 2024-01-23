package com.geirolz.app.toolkit

import scala.annotation.targetName
import scala.util.NotGiven

@targetName("Either")
type \/[+A, +B]    = Either[A, B]
type NotNothing[T] = NotGiven[T =:= Nothing]