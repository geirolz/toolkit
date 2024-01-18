package com.geirolz.app.toolkit

import cats.{~>, Applicative, Functor}
import cats.data.NonEmptyList
import com.geirolz.app.toolkit.FailureHandler.OnFailureBehaviour

case class FailureHandler[F[_], FAILURE](
  onFailureF: FAILURE => F[OnFailureBehaviour],
  handleFailureWithF: FAILURE => F[FAILURE \/ Unit]
) { $this =>

  def onFailure(f: FAILURE => F[OnFailureBehaviour]): FailureHandler[F, FAILURE] =
    copy(onFailureF = f)

  def handleFailureWith(f: FAILURE => F[FAILURE \/ Unit]): FailureHandler[F, FAILURE] =
    copy(handleFailureWithF = f)

  def mapK[G[_]](f: F ~> G): FailureHandler[G, FAILURE] =
    FailureHandler[G, FAILURE](
      onFailureF         = (e: FAILURE) => f($this.onFailureF(e)),
      handleFailureWithF = (e: FAILURE) => f($this.handleFailureWithF(e))
    )

  def widen[EE <: FAILURE]: FailureHandler[F, EE] =
    this.asInstanceOf[FailureHandler[F, EE]]

  def widenNel[EE](implicit
    env: FAILURE =:= NonEmptyList[EE]
  ): FailureHandler[F, NonEmptyList[EE]] =
    this.asInstanceOf[FailureHandler[F, NonEmptyList[EE]]]
}
object FailureHandler extends FailureHandlerSyntax:

  def summon[F[_], E](implicit ev: FailureHandler[F, E]): FailureHandler[F, E] = ev

  def cancelAll[F[_]: Applicative, FAILURE]: FailureHandler[F, FAILURE] =
    FailureHandler[F, FAILURE](
      onFailureF         = (_: FAILURE) => Applicative[F].pure(OnFailureBehaviour.CancelAll),
      handleFailureWithF = (e: FAILURE) => Applicative[F].pure(Left(e))
    )

  sealed trait OnFailureBehaviour
  object OnFailureBehaviour:
    case object CancelAll extends OnFailureBehaviour
    case object DoNothing extends OnFailureBehaviour

sealed trait FailureHandlerSyntax {

  import cats.syntax.all.*

  implicit class FailureHandlerOps[F[+_], FAILURE]($this: FailureHandler[F, FAILURE]) {
    final def liftNonEmptyList(implicit
      F: Applicative[F]
    ): FailureHandler[F, NonEmptyList[FAILURE]] =
      FailureHandler[F, NonEmptyList[FAILURE]](
        onFailureF = (failures: NonEmptyList[FAILURE]) =>
          failures
            .traverse($this.onFailureF(_))
            .map(
              _.collectFirst { case OnFailureBehaviour.CancelAll =>
                OnFailureBehaviour.CancelAll
              }.getOrElse(OnFailureBehaviour.DoNothing)
            ),
        handleFailureWithF = (failures: NonEmptyList[FAILURE]) =>
          failures.toList
            .traverse($this.handleFailureWithF(_))
            .map(_.partitionEither(identity)._1.toNel)
            .map {
              case None       => ().asRight[NonEmptyList[FAILURE]]
              case Some(nelE) => nelE.asLeft[Unit]
            }
      )
  }

  implicit class FailureHandlerNelOps[F[+_], FAILURE](
    $this: FailureHandler[F, NonEmptyList[FAILURE]]
  ) {
    final def single(implicit F: Functor[F]): FailureHandler[F, FAILURE] =
      FailureHandler[F, FAILURE](
        onFailureF         = (e: FAILURE) => $this.onFailureF(NonEmptyList.one(e)),
        handleFailureWithF = (e: FAILURE) => $this.handleFailureWithF(NonEmptyList.one(e)).map(_.leftMap(_.head))
      )
  }
}
