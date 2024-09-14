package com.geirolz.app.toolkit.failure

import cats.data.NonEmptyList
import cats.syntax.all.*
import cats.{Applicative, Functor, Monad, ~>}
import com.geirolz.app.toolkit.failure.FailureHandler.OnFailureBehaviour
import com.geirolz.app.toolkit.logger.Logger
import com.geirolz.app.toolkit.{AppMessages, \/}

case class FailureHandler[F[_], FAILURE](
  onFailureF: FAILURE => F[OnFailureBehaviour],
  handleFailureWithF: FAILURE => F[FAILURE \/ Unit]
):
  $this =>

  inline def onFailure(f: FAILURE => F[OnFailureBehaviour]): FailureHandler[F, FAILURE] =
    copy(onFailureF = f)

  inline def handleFailureWith(f: FAILURE => F[FAILURE \/ Unit]): FailureHandler[F, FAILURE] =
    copy(handleFailureWithF = f)

  def mapK[G[_]](f: F ~> G): FailureHandler[G, FAILURE] =
    FailureHandler[G, FAILURE](
      onFailureF         = (e: FAILURE) => f($this.onFailureF(e)),
      handleFailureWithF = (e: FAILURE) => f($this.handleFailureWithF(e))
    )

  def widen[EE <: FAILURE]: FailureHandler[F, EE] =
    this.asInstanceOf[FailureHandler[F, EE]]

  def widenNel[EE](using FAILURE =:= NonEmptyList[EE]): FailureHandler[F, NonEmptyList[EE]] =
    this.asInstanceOf[FailureHandler[F, NonEmptyList[EE]]]

object FailureHandler extends FailureHandlerSyntax:

  inline def apply[F[_], E](using ev: FailureHandler[F, E]): FailureHandler[F, E] = ev

  def logAndCancelAll[F[_]: Monad, FAILURE](appMessages: AppMessages, logger: Logger[F]): FailureHandler[F, FAILURE] =
    doNothing[F, FAILURE]().onFailure(failure => logger.failure(s"${appMessages.appAFailureOccurred} $failure").as(OnFailureBehaviour.CancelAll))

  def cancelAll[F[_]: Applicative, FAILURE]: FailureHandler[F, FAILURE] =
    doNothing[F, FAILURE]().onFailure(_ => OnFailureBehaviour.CancelAll.pure[F])

  def doNothing[F[_]: Applicative, FAILURE](): FailureHandler[F, FAILURE] =
    FailureHandler[F, FAILURE](
      onFailureF         = (_: FAILURE) => Applicative[F].pure(OnFailureBehaviour.DoNothing),
      handleFailureWithF = (e: FAILURE) => Applicative[F].pure(Left(e))
    )

  sealed trait OnFailureBehaviour derives CanEqual
  object OnFailureBehaviour:
    case object CancelAll extends OnFailureBehaviour
    case object DoNothing extends OnFailureBehaviour

sealed transparent trait FailureHandlerSyntax:

  import cats.syntax.all.*

  extension [F[+_], FAILURE](fh: FailureHandler[F, FAILURE])
    def liftNonEmptyList(using Applicative[F]): FailureHandler[F, NonEmptyList[FAILURE]] =
      FailureHandler[F, NonEmptyList[FAILURE]](
        onFailureF = (failures: NonEmptyList[FAILURE]) =>
          failures
            .traverse(fh.onFailureF(_))
            .map(
              _.collectFirst { case OnFailureBehaviour.CancelAll =>
                OnFailureBehaviour.CancelAll
              }.getOrElse(OnFailureBehaviour.DoNothing)
            ),
        handleFailureWithF = (failures: NonEmptyList[FAILURE]) =>
          failures.toList
            .traverse(fh.handleFailureWithF(_))
            .map(_.partitionEither(identity)._1.toNel)
            .map {
              case None       => ().asRight[NonEmptyList[FAILURE]]
              case Some(nelE) => nelE.asLeft[Unit]
            }
      )

  extension [F[+_], FAILURE](fh: FailureHandler[F, NonEmptyList[FAILURE]])
    def single(using Functor[F]): FailureHandler[F, FAILURE] =
      FailureHandler[F, FAILURE](
        onFailureF         = (e: FAILURE) => fh.onFailureF(NonEmptyList.one(e)),
        handleFailureWithF = (e: FAILURE) => fh.handleFailureWithF(NonEmptyList.one(e)).map(_.leftMap(_.head))
      )
