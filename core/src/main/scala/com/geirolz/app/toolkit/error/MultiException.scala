package com.geirolz.app.toolkit.error

import cats.data.NonEmptyList
import cats.kernel.Semigroup
import cats.{Foldable, Show}
import com.geirolz.app.toolkit.Nel

import java.io.{OutputStreamWriter, PrintStream, PrintWriter}

class MultiException(override val errors: Nel[Throwable])
    extends Throwable(
      s"Multiple [${errors.size}] exceptions. ${errors.toList.map(_.getMessage.prependedAll(" - ")).mkString("\n")}"
    )
    with MultiError[Throwable] {

  override type Self = MultiException

  def getStackTraces: Map[Throwable, Array[StackTraceElement]] =
    errors.toList.map(e => (e, e.getStackTrace)).toMap

  override def printStackTrace(): Unit = printStackTrace(System.err)

  override def printStackTrace(s: PrintStream): Unit =
    printStackTrace(new PrintWriter(new OutputStreamWriter(s)))

  override def printStackTrace(s: PrintWriter): Unit =
    errors.toList.foreach(e => {
      e.printStackTrace(s)
      s.print(s"\n\n${(0 to 70).map(_ => "#").mkString("")}\n\n")
    })

  override protected def copyWith(errors: Nel[Throwable]): MultiException =
    new MultiException(errors)

  /** @deprecated Use [[MultiException.getStackTraces]] instead */
  @Deprecated
  override def getStackTrace: Array[StackTraceElement] = super.getStackTrace

  /** @deprecated This method is not supported by [[MultiException]] */
  @Deprecated
  override def setStackTrace(stackTrace: Array[StackTraceElement]): Unit =
    throw new UnsupportedOperationException
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
