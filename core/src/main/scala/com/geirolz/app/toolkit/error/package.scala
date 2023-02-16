package com.geirolz.app.toolkit

import cats.kernel.Semigroup

package object error {

  implicit class RuntimeExpressionStringCtx(ctx: StringContext) {
    def ex(args: Any*): RuntimeException =
      new RuntimeException(ctx.s(args*)).dropFirstStackTraceElement
  }

  implicit class ThrowableSyntax[T <: Throwable](ex: T) {
    def dropFirstStackTraceElement: T = {
      val stackTrace = ex.getStackTrace
      if (stackTrace != null && stackTrace.length > 1)
        ex.setStackTrace(stackTrace.tail)

      ex
    }
  }
  implicit val throwableSemigroup: Semigroup[Throwable] = (x: Throwable, y: Throwable) =>
    (x, y) match {
      case (m1: MultiException, m2: MultiException) => m1 + m2
      case (e1: Throwable, m2: MultiException)      => m2.prepend(e1)
      case (m1: MultiException, e2: Throwable)      => m1.append(e2)
      case (e1: Throwable, e2: Throwable)           => MultiException.of(e1, e2)
    }
}
