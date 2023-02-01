package com.geirolz.app.toolkit

package object error {
  implicit class RuntimeExpressionStringCtx(ctx: StringContext) {
    def error(args: Any*): RuntimeException = new RuntimeException(ctx.s(args*))
  }
}
