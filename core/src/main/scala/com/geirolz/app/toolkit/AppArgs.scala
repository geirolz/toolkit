package com.geirolz.app.toolkit

import cats.Show
import com.geirolz.app.toolkit.ArgDecoder.{ArgDecodingError, MissingArgAtIndex, MissingVariable}

import scala.util.Try

final case class AppArgs(private val value: List[String]) extends AnyVal:

  inline def exists(p: AppArgs => Boolean, pN: AppArgs => Boolean*): Boolean =
    (p +: pN).forall(_.apply(this))

  inline def stringAtOrThrow(idx: Int): String =
    atOrThrow[String](idx)

  inline def stringAt(idx: Int): Either[ArgDecodingError, String] =
    at[String](idx)

  inline def atOrThrow[V: ArgDecoder](idx: Int): V =
    orThrow(at(idx))

  inline def at[V: ArgDecoder](idx: Int): Either[ArgDecodingError, V] =
    if (isDefinedAt(idx))
      ArgDecoder[V].decode(value(idx))
    else
      Left(MissingArgAtIndex(idx))

  inline def hasNotFlags(flag1: String, flagN: String*): Boolean =
    !hasFlags(flag1, flagN*)

  inline def hasFlags(flag1: String, flagN: String*): Boolean =
    (flag1 +: flagN).forall(value.contains(_))

  inline def hasNotVar(name: String, separator: String = "="): Boolean =
    !hasVar(name, separator)

  inline def hasVar(name: String, separator: String = "="): Boolean =
    getStringVar(name, separator).isRight

  inline def getStringVar(name: String, separator: String = "="): Either[ArgDecodingError, String] =
    getVar[String](name, separator)

  inline def getVarOrThrow[V: ArgDecoder](name: String, separator: String = "="): V =
    orThrow(getVar(name, separator))

  def getVar[V: ArgDecoder](name: String, separator: String = "="): Either[ArgDecodingError, V] = {
    value.findLast(_.startsWith(s"$name$separator")).map(_.drop(name.length + separator.length)) match {
      case Some(value) => ArgDecoder[V].decode(value)
      case None        => Left(MissingVariable(name))
    }
  }

  inline def toMap(separator: String = "="): Map[String, String] =
    toTuples(separator).toMap

  def toTuples(separator: String = "="): List[(String, String)] =
    value.map(_.split(separator)).collect { case Array(key, value) =>
      (key, value)
    }

  inline def toList: List[String] =
    value

  inline def isEmpty: Boolean =
    value.isEmpty

  inline def isDefinedAt(idx: Int): Boolean =
    value.isDefinedAt(idx)

  override def toString: String = s"AppArgs(${value.mkString(", ")})"

  private inline def orThrow[T](result: Either[ArgDecodingError, T]): T =
    result.fold(e => throw e.toException, identity)

object AppArgs:
  inline def fromList(args: List[String]): AppArgs = AppArgs(args)
  given Show[AppArgs]                              = Show.fromToString

// ---------------------------------
trait ArgDecoder[T]:
  def decode(value: String): Either[ArgDecodingError, T]

object ArgDecoder:

  inline def apply[T: ArgDecoder]: ArgDecoder[T] =
    summon[ArgDecoder[T]]

  inline def fromTry[T](t: String => Try[T]): ArgDecoder[T] =
    (value: String) => t(value).toEither.left.map(ArgDecodingException(_))

  sealed trait ArgDecodingError:
    inline def toException              = new RuntimeException(toString)
    final override def toString: String = Show[ArgDecodingError].show(this)

  object ArgDecodingError:
    given Show[ArgDecodingError] =
      case ArgDecodingException(cause) => s"ArgDecodingException(${cause.getMessage})"
      case MissingVariable(name)       => s"Missing variable $name"
      case MissingArgAtIndex(idx)      => s"Missing argument at index $idx"

  case class ArgDecodingException(cause: Throwable) extends ArgDecodingError
  case class MissingVariable(name: String) extends ArgDecodingError
  case class MissingArgAtIndex(idx: Int) extends ArgDecodingError

  given ArgDecoder[String]     = s => Right(s)
  given ArgDecoder[Char]       = fromTry(s => Try(s.head))
  given ArgDecoder[Byte]       = fromTry(s => Try(s.toByte))
  given ArgDecoder[Short]      = fromTry(s => Try(s.toShort))
  given ArgDecoder[Int]        = fromTry(s => Try(s.toInt))
  given ArgDecoder[Long]       = fromTry(s => Try(s.toLong))
  given ArgDecoder[Float]      = fromTry(s => Try(s.toFloat))
  given ArgDecoder[Double]     = fromTry(s => Try(s.toDouble))
  given ArgDecoder[Boolean]    = fromTry(s => Try(s.toBoolean))
  given ArgDecoder[BigInt]     = fromTry(s => Try(BigInt(s)))
  given ArgDecoder[BigDecimal] = fromTry(s => Try(BigDecimal(s)))
