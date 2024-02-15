package com.geirolz.app.toolkit.utils

import scala.annotation.{implicitAmbiguous, implicitNotFound}
import scala.language.postfixOps

@implicitNotFound(msg = "Cannot prove that ${A} =:!= ${B}.")
sealed trait =:!=[A, B]
object =:!= {

  given neq[A, B]: =:!=[A, B] = new =:!=[A, B] {}

  @implicitAmbiguous(msg = "Expected a different type from ${A}")
  given neqAmbig1[A]: =:!=[A, A] = null

  @implicitAmbiguous(msg = "Expected a different type from ${A}")
  given neqAmbig2[A]: =:!=[A, A] = null
}
