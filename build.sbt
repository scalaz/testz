import scoverage._
import sbt._
import Keys._

// shadow sbt-scalajs' definition
import sbtcrossproject.CrossPlugin.autoImport.crossProject

val monocleVersion = "1.4.0"
val scalazVersion  = "7.2.20"
val spireVersion   = "0.14.1"

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

val sonataCredentials = for {
  username <- sys.env.get("SONATYPE_USERNAME")
  password <- sys.env.get("SONATYPE_PASSWORD")
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)

credentials in ThisBuild ++= sonataCredentials.toSeq

val standardSettings = Seq(
  logBuffered in Compile := false,
  logBuffered in Test := false,
  updateOptions := updateOptions.value.withCachedResolution(true),
  exportJars := true,
  organization := "org.scalaz",
  organizationName := "Scalaz",
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
    "-Ywarn-value-discard",              // Warn when non-Unit expression results are unused.
    "-opt-warnings:_",                   // Warn if a method call marked @inline cannot be inlined
    "-opt:l:inline",                     // Enable the optimizer
    "-opt-inline-from:<sources>",
    "-Yopt-inline-heuristics:at-inline-annotated",
  ),
  scalacOptions in (Compile, doc) ++= Seq("-groups", "-implicits"),
  wartremoverWarnings in (Compile, compile) --= Seq(
    Wart.PublicInference,    // TODO: enable incrementally — currently results in many errors
    Wart.ImplicitParameter), // see wartremover/wartremover#350 & #351

  licenses += ("BSD New" -> new URL("https://opensource.org/licenses/BSD-3-Clause")),
  headerLicense := Some(HeaderLicense.BSD3Clause("2018", "Edmund Noble")),
  resolvers += Resolver.sonatypeRepo("releases"),

  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4"))

val publishSettings = Seq(
  organizationHomepage := None,
  homepage := Some(url("https://github.com/scalaz/testz")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/scalaz/testz"),
      "scm:git@github.com:scalaz/testz.git")),
  developers := List(
    sbt.Developer(
      id = "edmundnoble",
      name = "Edmund Noble",
      email = "edmundnoble@gmail.com",
      url = url("https://github.com/edmundnoble")
    )
  ))

val core = crossProject(JSPlatform, JVMPlatform).in(file("core"))
  .settings(name := "testz-core")
  .settings(standardSettings ++ publishSettings)
  .settings(
    connectInput in run := true,
    outputStrategy := Some(StdoutOutput)
  )
  .enablePlugins(AutomateHeaderPlugin)

val coreJVM = core.jvm
val coreJS = core.js

val util = crossProject(JSPlatform, JVMPlatform).in(file("util"))
  .settings(name := "testz-util")
  .settings(standardSettings ++ publishSettings)
  .enablePlugins(AutomateHeaderPlugin)

val utilJVM = util.jvm
val utilJS = util.js

val resource = crossProject(JSPlatform, JVMPlatform).in(file("resource"))
  .dependsOn(core, util)
  .settings(name := "testz-resource")
  .settings(standardSettings ++ publishSettings)

val resourceJVM = resource.jvm
val resourceJS = resource.js

val runner = crossProject(JSPlatform, JVMPlatform).in(file("runner"))
  .dependsOn(core, util)
  .settings(name := "testz-runner")
  .settings(standardSettings ++ publishSettings)
  .enablePlugins(AutomateHeaderPlugin)

val runnerJVM = runner.jvm
val runnerJS = runner.js

val stdlib = crossProject(JSPlatform, JVMPlatform).in(file("stdlib"))
  .dependsOn(core, resource, runner, util)
  .settings(name := "testz-stdlib")
  .settings(standardSettings ++ publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.1"),
      "com.github.ghik" %% "silencer-lib" % "1.1" % Provided
    )
  )
  .enablePlugins(AutomateHeaderPlugin)

val stdlibJVM = stdlib.jvm
val stdlibJS = stdlib.js

val scalatest = crossProject(JSPlatform, JVMPlatform).in(file("scalatest"))
  .dependsOn(core)
  .settings(name := "testz-scalatest")
  .settings(standardSettings ++ publishSettings)
  .enablePlugins(AutomateHeaderPlugin)

val scalatestJVM = scalatest.jvm
val scalatestJS = scalatest.js

val scalaz = project.in(file("scalaz"))
  .dependsOn(coreJVM, resourceJVM, runnerJVM)
  .settings(name := "testz-scalaz")
  .settings(standardSettings ++ publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalaz" %%% "scalaz-concurrent" % scalazVersion  % "compile, test",
      "org.scalaz" %%% "scalaz-core"       % scalazVersion  % "compile, test"
    )
  )
  .enablePlugins(AutomateHeaderPlugin)

val specs2 = crossProject(JSPlatform, JVMPlatform).in(file("specs2"))
  .dependsOn(core)
  .settings(name := "testz-specs2")
  .settings(standardSettings ++ publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.specs2" %%% "specs2-core" % "4.0.2"
    )
  )
  .enablePlugins(AutomateHeaderPlugin)

val specs2JVM = specs2.jvm
val specs2JS = specs2.js

val extras = crossProject(JSPlatform, JVMPlatform).in(file("extras"))
  .dependsOn(core, resource)
  .settings(name := "testz-extras")
  .settings(standardSettings ++ publishSettings)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(JmhPlugin)

val extrasJVM = extras.jvm
val extrasJS = extras.js

val tests = project.in(file("tests"))
  .settings(name := "testz-tests")
  .dependsOn(coreJVM, extrasJVM, resourceJVM, runnerJVM, scalatestJVM, scalaz, specs2JVM, stdlibJVM)
  .settings(standardSettings ++ publishSettings)
  .settings(libraryDependencies ++= Seq(
    "com.github.julien-truffaut" %% "monocle-law"   % monocleVersion % Test))
  .enablePlugins(AutomateHeaderPlugin)

val benchmarks = project.in(file("benchmarks"))
  .dependsOn(coreJVM, runnerJVM, scalatestJVM, scalaz, stdlibJVM, specs2JVM)
  .settings(name := "testz-benchmarks")
  .settings(skip in publish := true)
  .settings(standardSettings)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(JmhPlugin)

/** A project just for the console.
  * Applies only the settings necessary for that purpose.
  */
val repl = project
  .dependsOn(tests % "compile->test")
  .dependsOn(benchmarks)
  .settings(standardSettings)
  .settings(skip in publish := true)
  .settings((compile in ThisBuild) := sbt.internal.inc.Analysis.Empty) // sbt.inc.Analysis.empty)
  .settings(
    console := (console in Test).value,
    scalacOptions --= Seq("-Yno-imports", "-Ywarn-unused:imports", "-Xfatal-warnings"),
    initialCommands in console += """
      |import testz._
      |import testz.benchmarks._
      |import testz.extras._
      |import testz.runner._
      |import testz.z._, z.streaming._
      |import scalaz._, scalaz.Scalaz._
    """.stripMargin.trim
  )

val docs = project
  .settings(name := "testz-docs")
  .dependsOn(
    benchmarks, coreJVM, extrasJVM, runnerJVM, scalatestJVM, scalaz, specs2JVM, stdlibJVM, utilJVM
  )
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
    micrositeGithubOwner      := "scalaz",
    micrositeGithubRepo       := "testz",
    micrositeBaseUrl          := "/testz",
    micrositeDocumentationUrl := "/testz/docs/01-first-example.html",
    micrositeHighlightTheme   := "color-brewer",
    micrositePushSiteWith     := GitHub4s,
    micrositeGithubToken      := Some(sys.env("GITHUB_TOKEN")))

val root = Project("root", file("."))
  .settings(name := "testz")
  .settings(standardSettings)
  .settings(skip in publish := true)
  .settings(console := (console in repl).value)
  .aggregate(
    benchmarks,
    coreJVM, coreJS,
    docs,
    extrasJVM, extrasJS,
    repl,
    resourceJVM, resourceJS,
    runnerJVM, runnerJS,
    scalatestJVM, scalatestJS,
    scalaz,
    specs2JVM, specs2JS,
    stdlibJVM, stdlibJS,
    tests,
    utilJVM, utilJS
  )
  .enablePlugins(AutomateHeaderPlugin)
