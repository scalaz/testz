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

lazy val sonataCredentials = for {
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
  ),

  fork in run := true,
  javaOptions := Seq(
    // we need discipline here.
    "-Xms1G",
    "-Xmx1G",

    // testz tests being single-threaded and careful about liveset size
    // makes concurrent GC an incredibly good choice.
    "-XX:+UseConcMarkSweepGC",
    "-XX:MaxInlineLevel=35"
  ))

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

lazy val core = crossProject(JSPlatform, JVMPlatform).in(file("core"))
  .settings(name := "testz-core")
  .settings(standardSettings ++ publishSettings)
  .settings(
    connectInput in run := true,
    outputStrategy := Some(StdoutOutput)
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val coreJVM = core.jvm
lazy val coreJS = core.js

lazy val benchmarks = project.in(file("benchmarks"))
  .dependsOn(coreJVM, runnerJVM, scalatestJVM, scalazJVM, stdlibJVM, specs2JVM)
  .settings(name := "testz-benchmarks")
  .settings(skip in publish := true)
  .settings(standardSettings)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(JmhPlugin)

lazy val runner = crossProject(JSPlatform, JVMPlatform).in(file("runner"))
  .dependsOn(core, util)
  .settings(name := "testz-runner")
  .settings(standardSettings ++ publishSettings)
  .enablePlugins(AutomateHeaderPlugin)

lazy val runnerJVM = runner.jvm
lazy val runnerJS = runner.js

lazy val scalatest = crossProject(JSPlatform, JVMPlatform).in(file("scalatest"))
  .dependsOn(core)
  .settings(name := "testz-scalatest")
  .settings(standardSettings ++ publishSettings)
  .enablePlugins(AutomateHeaderPlugin)

lazy val scalatestJVM = scalatest.jvm
lazy val scalatestJS = scalatest.js

lazy val scalaz = crossProject(JSPlatform, JVMPlatform).in(file("scalaz"))
  .dependsOn(core)
  .settings(name := "testz-scalaz")
  .settings(standardSettings ++ publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalaz" %% "scalaz-concurrent" % scalazVersion  % "compile, test",
      "org.scalaz" %% "scalaz-core"       % scalazVersion  % "compile, test"
    )
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val scalazJVM = scalaz.jvm
lazy val scalazJS = scalaz.js

lazy val specs2 = crossProject(JSPlatform, JVMPlatform).in(file("specs2"))
  .dependsOn(core)
  .settings(name := "testz-specs2")
  .settings(standardSettings ++ publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "4.0.2"
    )
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val specs2JVM = specs2.jvm
lazy val specs2JS = specs2.js

lazy val stdlib = crossProject(JSPlatform, JVMPlatform).in(file("stdlib"))
  .dependsOn(core, util)
  .settings(name := "testz-stdlib")
  .settings(standardSettings ++ publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.1"),
      "com.github.ghik" %% "silencer-lib" % "1.1" % Provided
    )
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val stdlibJVM = stdlib.jvm
lazy val stdlibJS = stdlib.js

lazy val tests = project.in(file("tests"))
  .settings(name := "testz-tests")
  .dependsOn(coreJVM, runnerJVM, scalatestJVM, scalazJVM, specs2JVM, stdlibJVM)
  .settings(standardSettings ++ publishSettings)
  .settings(libraryDependencies ++= Seq(
    "com.github.julien-truffaut" %% "monocle-law"   % monocleVersion % Test))
  .enablePlugins(AutomateHeaderPlugin)

lazy val util = crossProject(JSPlatform, JVMPlatform).in(file("util"))
  .settings(name := "testz-util")
  .settings(standardSettings ++ publishSettings)
  .enablePlugins(AutomateHeaderPlugin)

lazy val utilJVM = util.jvm
lazy val utilJS = util.js

lazy val docs = project
  .settings(name := "testz-docs")
  .dependsOn(coreJVM, runnerJVM, scalatestJVM, scalazJVM, specs2JVM, stdlibJVM)
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
  .settings(skip in publish := true)
  .settings((compile in ThisBuild) := sbt.internal.inc.Analysis.Empty) // sbt.inc.Analysis.empty)
  .settings(
    console := (console in Test).value,
    scalacOptions --= Seq("-Yno-imports", "-Ywarn-unused:imports", "-Xfatal-warnings"),
    initialCommands in console += """
      |import testz._, testz.runner._, testz.z._, testz.z.streaming._
      |import testz.benchmarks._
      |import scalaz._, scalaz.Scalaz._
    """.stripMargin.trim
  )

lazy val root = Project("root", file("."))
  .settings(name := "testz")
  .settings(standardSettings)
  .settings(skip in publish := true)
  .settings(console := (console in repl).value)
  .aggregate(
    benchmarks,
    coreJVM, coreJS,
    docs,
    repl,
    runnerJVM, runnerJS,
    scalatestJVM, scalatestJS,
    scalazJVM, scalazJS,
    specs2JVM, specs2JS,
    stdlibJVM, stdlibJS,
    tests,
    utilJVM, utilJS
  )
  .enablePlugins(AutomateHeaderPlugin)
