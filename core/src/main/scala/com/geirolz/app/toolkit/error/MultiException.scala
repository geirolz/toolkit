package com.geirolz.app.toolkit.error

import cats.{Foldable, Show}
import cats.data.NonEmptyList
import cats.kernel.Semigroup

import java.io.{PrintStream, PrintWriter}
import scala.util.control.NoStackTrace

final class MultiException(override val errors: NonEmptyList[Throwable])
    extends Throwable(MultiException.buildThrowMessage(errors))
    with NoStackTrace
    with MultiError[Throwable] {

  override type Self = MultiException

  def getStackTraces: Map[Throwable, Array[StackTraceElement]] =
    errors.toList.map(e => (e, e.getStackTrace)).toMap

  override def printStackTrace(): Unit =
    printStackTrace(System.err)

  override def printStackTrace(s: PrintStream): Unit =
    printStackTrace(new PrintWriter(s))

  override def printStackTrace(s: PrintWriter): Unit = {
    errors.toList.foreach(e => {
      e.printStackTrace(s)
      s.print(s"\n${(0 to 70).map(_ => "#").mkString("")}\n")
    })
    s.close()
  }

  override protected def copyWith(errors: NonEmptyList[Throwable]): MultiException =
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

  private def buildThrowMessage(errors: NonEmptyList[Throwable]): String = {
    s"""
       |Multiple [${errors.size}] exceptions.
       |${errors.toList
        .map(ex =>
          s" -${ex.getMessage} [${ex.getStackTrace.headOption.map(_.toString).getOrElse("")}]"
        )
        .mkString("\n")}""".stripMargin
  }

  def fromFoldable[F[_]: Foldable](errors: F[Throwable]): Option[MultiException] =
    NonEmptyList.fromFoldable(errors).map(fromNel(_))

  def fromNel(errors: NonEmptyList[Throwable]): MultiException =
    new MultiException(errors)

  def of(e1: Throwable, eN: Throwable*): MultiException =
    MultiException.fromNel(NonEmptyList.of(e1, eN*))

  implicit val semigroup: Semigroup[MultiException] =
    (x: MultiException, y: MultiException) => x + y

  implicit val show: Show[MultiException] = Show.fromToString
}
