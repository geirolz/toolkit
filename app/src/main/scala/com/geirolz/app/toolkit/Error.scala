package com.geirolz.app.toolkit

object ErrorSyntax {
  implicit class RuntimeExpressionStringCtx(ctx: StringContext) {
    def error(args: Any*): RuntimeException = new RuntimeException(ctx.s(args*))
  }
}
