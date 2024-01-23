package com.geirolz.app.toolkit

import cats.{Applicative, MonadThrow}
import cats.data.NonEmptyList
import cats.effect.Resource
import com.geirolz.app.toolkit.novalues.NoFailure

sealed trait AppLogicInterpreter[F[_], R[_], FAILURE]:
  def interpret[T](appLogic: Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ T]]): Resource[F, F[R[T]]]
  def isSuccess[T](value: R[T]): Boolean

object AppLogicInterpreter:

  import cats.syntax.all.*

  def apply[F[_], R[_], FAILURE](using
    i: AppLogicInterpreter[F, R, FAILURE]
  ): AppLogicInterpreter[F, R, FAILURE] = i

  given [F[_]: MonadThrow]: AppLogicInterpreter[F, [X] =>> X, NoFailure] =
    new AppLogicInterpreter[F, [X] =>> X, NoFailure]:
      override def isSuccess[T](value: T): Boolean = true
      override def interpret[T](appLogic: Resource[F, NoFailure \/ F[NonEmptyList[NoFailure] \/ T]]): Resource[F, F[T]] =
        appLogic.map {
          case Left(_) =>
            MonadThrow[F].raiseError(new RuntimeException("Unreachable point."))
          case Right(value: F[NonEmptyList[NoFailure] \/ T]) =>
            value.flatMap {
              case Left(_) =>
                MonadThrow[F].raiseError(new RuntimeException("Unreachable point."))
              case Right(value) =>
                value.pure[F]
            }
        }

  given [F[_]: Applicative, FAILURE]: AppLogicInterpreter[F, Either[NonEmptyList[FAILURE], *], FAILURE] =
    new AppLogicInterpreter[F, Either[NonEmptyList[FAILURE], *], FAILURE]:
      override def isSuccess[T](value: NonEmptyList[FAILURE] \/ T): Boolean = value.isRight
      override def interpret[T](
        appLogic: Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ T]]
      ): Resource[F, F[NonEmptyList[FAILURE] \/ T]] = appLogic.map {
        case Left(failure) => Left(NonEmptyList.one(failure)).pure[F]
        case Right(value)  => value
      }
