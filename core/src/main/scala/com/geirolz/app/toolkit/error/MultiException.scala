package com.geirolz.app.toolkit.error

import cats.data.NonEmptyList
import cats.kernel.Semigroup
import cats.{Foldable, Show}
import com.geirolz.app.toolkit.Nel

class MultiException(override val errors: Nel[Throwable])
    extends Throwable(
      s"Multiple [${errors.size}] exceptions. ${errors.toList.map(_.getMessage.prepended(" - ")).mkString("\n")}"
    )
    with MultiError[Throwable] {

  override type Self = MultiException
  override protected def update(errors: Nel[Throwable]): MultiException =
    new MultiException(errors)
}

object MultiException {

  def fromFoldable[F[_]: Foldable](errors: F[Throwable]): Option[MultiException] =
    NonEmptyList.fromFoldable(errors).map(fromNel(_))

  def fromNel(errors: NonEmptyList[Throwable]): MultiException =
    new MultiException(errors)

  def of(e1: Throwable, eN: Throwable*): MultiException =
    MultiException.fromNel(Nel.of(e1, eN*))

  implicit val semigroup: Semigroup[MultiException] =
    (x: MultiException, y: MultiException) => x + y

  implicit val show: Show[MultiException] = Show.fromToString
}
