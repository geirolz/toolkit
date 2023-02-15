package com.geirolz.app.toolkit

sealed trait OnFailure
object OnFailure {
  case object CancelAll extends OnFailure
  case object DoNothing extends OnFailure
}
