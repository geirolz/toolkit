package com.geirolz.app

import cats.data.NonEmptyList

package object toolkit {
  type |[+A, +B] = Either[A, B]
  type Nel[+A]   = NonEmptyList[A]
  val Nel = NonEmptyList
}
