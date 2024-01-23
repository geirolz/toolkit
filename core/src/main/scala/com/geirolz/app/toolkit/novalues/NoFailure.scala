package com.geirolz.app.toolkit.novalues

import com.geirolz.app.toolkit.=:!=

sealed trait NoFailure
object NoFailure:
  type NotNoFailure[T] = T =:!= NoFailure
