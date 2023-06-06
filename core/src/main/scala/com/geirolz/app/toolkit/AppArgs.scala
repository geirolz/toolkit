package com.geirolz.app.toolkit

import cats.Show
import com.geirolz.app.toolkit.ArgDecoder.{ArgDecodingError, MissingArgAtIndex, MissingVariable}

import scala.util.Try

final case class AppArgs(private val value: List[String]) extends AnyVal {

  def exists(p: AppArgs => Boolean, pN: AppArgs => Boolean*): Boolean =
    (p +: pN).forall(_.apply(this))

  def stringAtOrThrow(idx: Int): String =
    atOrThrow[String](idx)

  def stringAt(idx: Int): Either[ArgDecodingError, String] =
    at[String](idx)

  def atOrThrow[V: ArgDecoder](idx: Int): V =
    orThrow(at(idx))

  def at[V: ArgDecoder](idx: Int): Either[ArgDecodingError, V] =
    if (isDefinedAt(idx))
      ArgDecoder[V].decode(value(idx))
    else
      Left(MissingArgAtIndex(idx))

  def hasNotFlags(flag1: String, flagN: String*): Boolean =
    !hasFlags(flag1, flagN*)

  def hasFlags(flag1: String, flagN: String*): Boolean =
    (flag1 +: flagN).forall(value.contains(_))

  def hasNotVar(name: String, separator: String = "="): Boolean =
    !hasVar(name, separator)

  def hasVar(name: String, separator: String = "="): Boolean =
    getStringVar(name, separator).isRight

  def getStringVar(name: String, separator: String = "="): Either[ArgDecodingError, String] =
    getVar[String](name, separator)

  def getVarOrThrow[V: ArgDecoder](name: String, separator: String = "="): V =
    orThrow(getVar(name, separator))

  def getVar[V: ArgDecoder](name: String, separator: String = "="): Either[ArgDecodingError, V] = {
    value.findLast(_.startsWith(s"$name$separator")).map(_.drop(name.length + separator.length)) match {
      case Some(value) => ArgDecoder[V].decode(value)
      case None        => Left(MissingVariable(name))
    }
  }

  def toMap(separator: String = "="): Map[String, String] =
    toTuples(separator).toMap

  def toTuples(separator: String = "="): List[(String, String)] =
    value.map(_.split(separator)).collect { case Array(key, value) =>
      (key, value)
    }

  def toList[V: ArgDecoder]: List[String] = value

  def isEmpty: Boolean = value.isEmpty

  def isDefinedAt(idx: Int): Boolean =
    value.isDefinedAt(idx)

  override def toString: String = s"AppArgs(${value.mkString(", ")})"

  private def orThrow[T](result: Either[ArgDecodingError, T]): T =
    result.fold(e => throw e.toException, identity)
}
object AppArgs {

  def fromList(args: List[String]): AppArgs =
    AppArgs(args)

  implicit val show: Show[AppArgs] = Show.fromToString
}

trait ArgDecoder[T] {
  def decode(value: String): Either[ArgDecodingError, T]
}
object ArgDecoder {

  def apply[T: ArgDecoder]: ArgDecoder[T] = implicitly[ArgDecoder[T]]

  def fromTry[T](t: String => Try[T]): ArgDecoder[T] =
    (value: String) => t(value).toEither.left.map(ArgDecodingException(_))

  sealed trait ArgDecodingError {
    def toException                     = new RuntimeException(toString)
    final override def toString: String = Show[ArgDecodingError].show(this)
  }
  object ArgDecodingError {
    implicit val show: Show[ArgDecodingError] = {
      case ArgDecodingException(cause) => s"ArgDecodingException(${cause.getMessage})"
      case MissingVariable(name)       => s"Missing variable $name"
      case MissingArgAtIndex(idx)      => s"Missing argument at index $idx"
    }
  }
  case class ArgDecodingException(cause: Throwable) extends ArgDecodingError
  case class MissingVariable(name: String) extends ArgDecodingError
  case class MissingArgAtIndex(idx: Int) extends ArgDecodingError

  implicit val stringDecoder: ArgDecoder[String]         = s => Right(s)
  implicit val charDecoder: ArgDecoder[Char]             = fromTry(s => Try(s.head))
  implicit val byteDecoder: ArgDecoder[Byte]             = fromTry(s => Try(s.toByte))
  implicit val shortDecoder: ArgDecoder[Short]           = fromTry(s => Try(s.toShort))
  implicit val intDecoder: ArgDecoder[Int]               = fromTry(s => Try(s.toInt))
  implicit val longDecoder: ArgDecoder[Long]             = fromTry(s => Try(s.toLong))
  implicit val floatDecoder: ArgDecoder[Float]           = fromTry(s => Try(s.toFloat))
  implicit val doubleDecoder: ArgDecoder[Double]         = fromTry(s => Try(s.toDouble))
  implicit val booleanDecoder: ArgDecoder[Boolean]       = fromTry(s => Try(s.toBoolean))
  implicit val bigIntDecoder: ArgDecoder[BigInt]         = fromTry(s => Try(BigInt(s)))
  implicit val bigDecimalDecoder: ArgDecoder[BigDecimal] = fromTry(s => Try(BigDecimal(s)))
}
