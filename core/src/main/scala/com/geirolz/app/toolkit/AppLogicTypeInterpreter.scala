package com.geirolz.app.toolkit

import cats.{Applicative, Functor, MonadThrow}
import cats.data.NonEmptyList
import cats.effect.Resource

trait AppLogicTypeInterpreter[F[_], FAILURE]:
  type Result[T]
  def interpret[T](appLogic: Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ T]]): Resource[F, F[Result[T]]]
  def isSuccess[T](value: Result[T]): Boolean

object AppLogicTypeInterpreter:

  import cats.syntax.all.*

  def apply[F[_], FAILURE](using i: AppLogicTypeInterpreter[F, FAILURE]): AppLogicTypeInterpreter[F, FAILURE] = i

  given [F[_]: Applicative, FAILURE: NotNothing]: AppLogicTypeInterpreter[F, FAILURE] =
    new AppLogicTypeInterpreter[F, FAILURE]:
      override type Result[T] = NonEmptyList[FAILURE] \/ T
        override def isSuccess[T](value: NonEmptyList[FAILURE] \/ T): Boolean = value.isRight
      override def interpret[T](
        appLogic: Resource[F, FAILURE \/ F[NonEmptyList[FAILURE] \/ T]]
      ): Resource[F, F[NonEmptyList[FAILURE] \/ T]] = appLogic.map {
        case Left(failure) => Left(NonEmptyList.one(failure)).pure[F]
        case Right(value)  => value
      }

  given [F[_]: MonadThrow]: AppLogicTypeInterpreter[F, Nothing] =
    new AppLogicTypeInterpreter[F, Nothing]:
      override type Result[T] = T
      override def isSuccess[T](value: T): Boolean = true
      override def interpret[T](appLogic: Resource[F, Nothing \/ F[NonEmptyList[Nothing] \/ T]]): Resource[F, F[T]] =
        appLogic.map {
          case Left(_) =>
            MonadThrow[F].raiseError(new RuntimeException("Unreachable point."))
          case Right(value: F[NonEmptyList[Nothing] \/ T]) =>
            value.flatMap {
              case Left(_) =>
                MonadThrow[F].raiseError(new RuntimeException("Unreachable point."))
              case Right(value) =>
                value.pure[F]
            }
        }
