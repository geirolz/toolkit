package com.geirolz.app.toolkit.error

import cats.kernel.Semigroup
import com.geirolz.app.toolkit.Nel

trait MultiError[E] {

  type Self <: MultiError[E]

  val errors: Nel[E]

  def prepend(e: E): Self =
    update(errors.prepend(e))

  def append(e: E): Self =
    update(errors.append(e))

  def +(me: MultiError[E]): Self =
    update(errors.appendList(me.errors.toList))

  protected def update(errors: Nel[E]): Self
}
object MultiError {

  def semigroup[E, ME <: E & MultiError[E]](f: Nel[E] => ME): Semigroup[E] =
    (x: E, y: E) =>
      (x, y) match {
        case (m1: MultiError[?], m2: MultiError[?]) =>
          (m1.asInstanceOf[MultiError[E]] + m2.asInstanceOf[MultiError[E]]).asInstanceOf[E]
        case (m1: MultiError[?], e2) => m1.asInstanceOf[MultiError[E]].append(e2).asInstanceOf[E]
        case (e1, e2)                => f(Nel.of(e1, e2))
      }
}
