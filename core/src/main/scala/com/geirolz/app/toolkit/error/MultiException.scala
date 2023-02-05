package com.geirolz.app.toolkit.error

import cats.data.NonEmptyList
import cats.kernel.Semigroup

class MultiException(override val errors: NonEmptyList[Throwable])
    extends Throwable
    with MultiError[Throwable] {

  override type Self = MultiException
  override protected def update(errors: NonEmptyList[Throwable]): MultiException =
    new MultiException(errors)
}

object MultiException {

  def of(e1: Throwable, eN: Throwable*): MultiException =
    new MultiException(NonEmptyList.of(e1, eN*))

  implicit val semigroup: Semigroup[MultiException] =
    (x: MultiException, y: MultiException) => x + y
}
