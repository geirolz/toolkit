import org.typelevel.scalacoptions.{ScalaVersion, ScalacOption, ScalacOptions}

object PrjCompilerOptions {

  val common: Set[ScalacOption] = Set(
    ScalacOptions.encoding("UTF-8"),
    ScalacOptions.other("-indent"),
    ScalacOptions.sourceFuture,
    ScalacOptions.deprecation,
    ScalacOptions.feature,
    ScalacOptions.unchecked,
    ScalacOptions.other("-Ykind-projector"),

    // language
    ScalacOptions.languageStrictEquality,
    ScalacOptions.languageHigherKinds,
    ScalacOptions.languageExistentials,
    ScalacOptions.other("-language:dynamics"),

    // warns
    ScalacOptions.fatalWarnings,
    ScalacOptions.warnValueDiscard,
    ScalacOptions.warnNonUnitStatement,
    ScalacOptions.warnExtraImplicit,
    ScalacOptions.warnUnusedImplicits,
    ScalacOptions.warnUnusedImports,
    ScalacOptions.warnUnusedLocals,
    ScalacOptions.warnUnusedParams,
    ScalacOptions.warnUnusedPrivates,
    ScalacOptions.warnUnusedPatVars
  )
}
