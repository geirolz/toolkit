package com.geirolz.app.toolkit.error

import cats.data.NonEmptyList
import cats.kernel.Semigroup

trait MultiError[E] {

  type Self <: MultiError[E]

  val errors: NonEmptyList[E]

  def prepend(e: E): Self =
    copyWith(errors.prepend(e))

  def append(e: E): Self =
    copyWith(errors.append(e))

  def +(me: MultiError[E]): Self =
    copyWith(errors.appendList(me.errors.toList))

  protected def copyWith(errors: NonEmptyList[E]): Self
}
object MultiError {

  def semigroup[E, ME <: E & MultiError[E]](f: NonEmptyList[E] => ME): Semigroup[E] =
    (x: E, y: E) =>
      (x, y) match {
        case (m1: MultiError[?], m2: MultiError[?]) =>
          (m1.asInstanceOf[MultiError[E]] + m2.asInstanceOf[MultiError[E]]).asInstanceOf[E]
        case (m1: MultiError[?], e2) => m1.asInstanceOf[MultiError[E]].append(e2).asInstanceOf[E]
        case (e1, e2)                => f(NonEmptyList.of(e1, e2))
      }
}
