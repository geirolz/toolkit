package com.geirolz.app.toolkit.novalues

import com.geirolz.app.toolkit.utils.=:!=

sealed trait NoFailure
object NoFailure:
  type NotNoFailure[T] = T =:!= NoFailure
