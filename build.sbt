import scoverage._
import sbt._
import Keys._

lazy val monocleVersion = "1.4.0"
lazy val scalazVersion  = "7.2.20"
lazy val spireVersion   = "0.14.1"

// publishTo in ThisBuild := {
  // val nexus = "https://oss.sonatype.org/"
  // if (isSnapshot.value)
    // Some("snapshots" at nexus + "content/repositories/snapshots")
  // else
    // Some("releases" at nexus + "service/local/staging/deploy/maven2")
// }

isSnapshot in ThisBuild := {
  true
}

lazy val sonataCredentials = for {
  username <- sys.env.get("SONATYPE_USERNAME")
  password <- sys.env.get("SONATYPE_PASSWORD")
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)

credentials in ThisBuild ++= sonataCredentials.toSeq

lazy val standardSettings = Seq(
  logBuffered in Compile := false,
  logBuffered in Test := false,
  updateOptions := updateOptions.value.withCachedResolution(true),
  exportJars := true,
  organization := "testz",
  organizationName := "testz",
  startYear := Some(2018),
  ScoverageKeys.coverageHighlighting := true,
  scalacOptions ++= Seq(
    "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8",                // Specify character encoding used by source files.
    "-explaintypes",                     // Explain type errors in more detail.
    "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
    "-language:higherKinds",             // Allow higher-kinded types
    "-language:implicitConversions",     // Allow definition of implicit functions called views
    "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
    "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
    "-Xfuture",                          // Turn on future language features.
    "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
    "-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
    "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
    "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
    "-Xlint:option-implicit",            // Option.apply used implicit view.
    "-Xlint:package-object-classes",     // Class or object defined in package object.
    "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
    "-Xlint:unsound-match",              // Pattern match may not be typesafe.
    "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
    "-Ypartial-unification",             // Enable partial unification in type constructor inference
    "-Ywarn-dead-code",                  // Warn when dead code is identified.
    "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
    "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen",              // Warn when numerics are widened.
    "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",              // Warn if a local definition is unused.
    "-Ywarn-unused:params",              // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates",            // Warn if a private member is unused.
    "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
  ),
  scalacOptions in (Compile, doc) ++= Seq("-groups", "-implicits"),
  wartremoverWarnings in (Compile, compile) --= Seq(
    Wart.PublicInference,    // TODO: enable incrementally — currently results in many errors
    Wart.ImplicitParameter), // see wartremover/wartremover#350 & #351

  licenses += ("BSD New" -> new URL("https://opensource.org/licenses/BSD-3-Clause")),
  headerLicense := Some(HeaderLicense.BSD3Clause("2018", "Edmund Noble")),
  resolvers += Resolver.sonatypeRepo("releases"),

  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4"),

  libraryDependencies ++= Seq(
    "com.lihaoyi"                %% "sourcecode"        % "0.1.4",
  ))

lazy val publishSettings = Seq(
  organizationHomepage := None,
  homepage := Some(url("https://github.com/edmundnoble/testz")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/edmundnoble/testz"),
      "scm:git@github.com:edmundnoble/testz.git")))

lazy val root = Project("root", file("."))
  .settings(name := "testz")
  .settings(standardSettings)
  .settings(skip in publish := true)
  .settings(console := (console in repl).value)
  .aggregate(
    core, `property-scalaz`, tests,
    base, benchmarks,
    runner,
    scalatest, specs2,
    scalaz,
    docs)
  .enablePlugins(AutomateHeaderPlugin)

lazy val core = project.in(file("core"))
  .settings(name := "testz-core")
  .settings(standardSettings ++ publishSettings: _*)
  .settings(
    connectInput in run := true,
    outputStrategy := Some(StdoutOutput)
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val base = project.in(file("base"))
  .settings(name := "testz-base")
  .aggregate(stdlib, core, runner)
  .settings(standardSettings ++ publishSettings: _*)
  .enablePlugins(AutomateHeaderPlugin)

lazy val `property-scalaz` = project.in(file("property-scalaz"))
  .dependsOn(core, scalaz)
  .settings(name := "testz-property-scalaz")
  .settings(standardSettings ++ publishSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalaz"    %% "scalaz-core"       % scalazVersion  % "compile, test",
      "org.typelevel" %% "spire"             % spireVersion
    )
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val benchmarks = project.in(file("benchmarks"))
  .dependsOn(core, runner, scalatest, scalaz, stdlib, specs2)
  .settings(name := "testz-benchmarks")
  .settings(skip in publish := true)
  .settings(standardSettings)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(JmhPlugin)

lazy val runner = project.in(file("runner"))
  .dependsOn(core, suite)
  .settings(name := "testz-runner")
  .settings(standardSettings ++ publishSettings: _*)
  .enablePlugins(AutomateHeaderPlugin)

lazy val scalatest = project.in(file("scalatest"))
  .dependsOn(core)
  .settings(name := "testz-scalatest")
  .settings(standardSettings ++ publishSettings: _*)
  .enablePlugins(AutomateHeaderPlugin)

lazy val scalaz = project.in(file("scalaz"))
  .dependsOn(core, suite)
  .settings(name := "testz-scalaz")
  .settings(standardSettings ++ publishSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalaz" %% "scalaz-concurrent" % scalazVersion  % "compile, test",
      "org.scalaz" %% "scalaz-core"       % scalazVersion  % "compile, test"
    )
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val specs2 = project.in(file("specs2"))
  .dependsOn(core)
  .settings(name := "testz-specs2")
  .settings(standardSettings ++ publishSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "4.0.2"
    )
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val stdlib = project.in(file("stdlib"))
  .dependsOn(core, suite)
  .settings(name := "testz-stdlib")
  .settings(standardSettings ++ publishSettings: _*)
  .enablePlugins(AutomateHeaderPlugin)

lazy val suite = project.in(file("suite"))
  .dependsOn(core)
  .settings(name := "testz-suite")
  .settings(standardSettings ++ publishSettings: _*)
  .enablePlugins(AutomateHeaderPlugin)

lazy val tests = project.in(file("tests"))
  .settings(name := "testz-tests")
  .dependsOn(core, runner, `property-scalaz`, scalatest, scalaz, specs2, stdlib)
  .settings(standardSettings)
  .settings(libraryDependencies ++= Seq(
    "com.github.julien-truffaut" %% "monocle-law"   % monocleVersion % Test))
  .enablePlugins(AutomateHeaderPlugin)

lazy val docs = project
  .settings(name := "testz-docs")
  .dependsOn(core, runner, `property-scalaz`, scalatest, scalaz, specs2, stdlib)
  .settings(standardSettings)
  .settings(skip in publish := true)
  .enablePlugins(MicrositesPlugin)
  .settings(
    // TIL, tut is run in a real REPL
    scalacOptions --= Seq("-Yno-imports", "-Ywarn-unused:imports", "-Xfatal-warnings")
  )
  .settings(
    micrositeName             := "testz",
    micrositeDescription      := "Purely functional testing library for Scala",
    micrositeAuthor           := "Edmund Noble",
    micrositeGithubOwner      := "edmundnoble",
    micrositeGithubRepo       := "testz",
    micrositeBaseUrl          := "/testz",
    micrositeDocumentationUrl := "/testz/docs/01-Getting-Started.html",
    micrositeHighlightTheme   := "color-brewer")

/** A project just for the console.
  * Applies only the settings necessary for that purpose.
  */
lazy val repl = project
  .dependsOn(tests % "compile->test")
  .dependsOn(benchmarks)
  .settings(standardSettings)
  .settings(
    console := (console in Test).value,
    scalacOptions --= Seq("-Yno-imports", "-Ywarn-unused:imports", "-Xfatal-warnings"),
    initialCommands in console += """
      |import testz._, testz.runner._, testz.property._, testz.z._
      |import testz.benchmarks._
      |import scalaz._, scalaz.Scalaz._
    """.stripMargin.trim
  )
