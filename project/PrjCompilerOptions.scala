import org.typelevel.scalacoptions.{ScalacOption, ScalacOptions}

object PrjCompilerOptions {

  val common: Set[ScalacOption] = Set(
    ScalacOptions.encoding("UTF-8"),
    ScalacOptions.other("-indent"),
    ScalacOptions.sourceFuture,
    ScalacOptions.deprecation,
    ScalacOptions.feature,
    ScalacOptions.unchecked,
    ScalacOptions.advancedKindProjector,

    // language
    ScalacOptions.languageStrictEquality,
    ScalacOptions.languageHigherKinds,
    ScalacOptions.languageExistentials,

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
