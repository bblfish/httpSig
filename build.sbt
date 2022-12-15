import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxOptions
import org.scalajs.jsenv.selenium.SeleniumJSEnv
import org.openqa.selenium.safari.SafariOptions
import Dependencies.*
// this import is needed to be able to run `set useJSEnv := JSEnv.Firefox` in sbt
import JSEnv.*

name := "httpSig"

ThisBuild / tlBaseVersion          := "0.4"
ThisBuild / tlUntaggedAreSnapshots := true

ThisBuild / developers := List(
  tlGitHubDev("bblfish", "Henry Story")
)
ThisBuild / startYear        := Some(2021)
ThisBuild / organization     := "net.bblfish.crypto"
ThisBuild / organizationName := "Henry Story"
ThisBuild / homepage         := Some(url("https://github.com/bblfish/httpSig"))
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/bblfish/httpSig"), "git@github.com:bblfish/httpSig.git")
)

ThisBuild / tlCiReleaseBranches := Seq()
ThisBuild / tlCiReleaseTags     := false // don't publish artifacts on github
//ThisBuild / tlSonatypeUseLegacyHost := false // TODO remove

ThisBuild / crossScalaVersions := Seq("3.2.1")
// check https://dotty.epfl.ch/docs/reference/experimental/canthrow.html
//ThisBuild / scalaVersion := "3.3.0-RC1-bin-20221130-72c4ffd-NIGHTLY"

ThisBuild / githubWorkflowBuildPreamble ++= Seq(
  WorkflowStep.Use(
    UseRef.Public("actions", "setup-node", "v2.4.0"),
    name = Some("Setup NodeJS v14 LTS"),
    params = Map("node-version" -> "14"),
    cond = Some("matrix.project == 'rootJS' && matrix.jsenv == 'NodeJS'")
  )
)

lazy val jsenvs = List(JSEnv.NodeJS, JSEnv.Chrome, JSEnv.Firefox).map(_.toString)
ThisBuild / githubWorkflowBuildMatrixAdditions += "jsenv" -> jsenvs
ThisBuild / githubWorkflowBuildSbtStepPreamble += s"set Global / useJSEnv := JSEnv.$${{ matrix.jsenv }}"
ThisBuild / githubWorkflowBuildMatrixExclusions += MatrixExclude(
  Map("project" -> "rootJS", "jsenv" -> JSEnv.NodeJS.toString)
)

ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  for {
    jsenv <- jsenvs.tail
  } yield MatrixExclude(Map("project" -> "rootJVM", "jsenv" -> jsenv))
}

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / resolvers += sonatypeSNAPSHOT

lazy val useJSEnv =
  settingKey[JSEnv]("Browser for running Scala.js tests")

Global / useJSEnv := JSEnv.NodeJS //what should go in its place?

ThisBuild / Test / jsEnv := {
  val old = (Test / jsEnv).value

  useJSEnv.value match {
    case JSEnv.NodeJS => old
    case JSEnv.Firefox =>
      val options = new FirefoxOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options)
    case JSEnv.Chrome =>
      val options = new ChromeOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options)
    /* Safari is very limited, allowing only one session at a time, and no headless mode.
     * but we leave this here, to keep track of evolution
     * https://developer.apple.com/documentation/webkit/about_webdriver_for_safari  */
    case JSEnv.Safari =>
      val o = new SafariOptions()
      new SeleniumJSEnv(o)
  }
}

lazy val root = tlCrossRootProject.aggregate(rfc8941, ietfSigHttp, http4sSig, akkaSig)

lazy val commonSettings = Seq(
  name        := "HttpSig Library",
  description := "Set of libraries implementing IETF `Signing Http Messages` RFC",
  startYear   := Some(2021),
  updateOptions := updateOptions.value.withCachedResolution(
    true
  ) // to speed up dependency resolution
)

lazy val rfc8941 = crossProject(JVMPlatform, JSPlatform)
  .in(file("rfc8941"))
  .settings(commonSettings*)
  .settings(
    name        := "rfc8941",
    description := "RFC8941 Structured Field Values parser",
    libraryDependencies += cats.parse.value,
    libraryDependencies += scodec.bits.value,
    libraryDependencies += tests.munit.value % Test
  ).jsSettings(
    scalacOptions ++= scala3jsOptions // ++= is really important. Do NOT use `:=` - that will block testing
  ).jvmSettings(
    scalacOptions := scala3Options
  )

lazy val ietfSigHttp = crossProject(JVMPlatform, JSPlatform)
  .in(file("ietfSig"))
  .settings(commonSettings*)
  .settings(
    name        := "Signing Http Messages Core",
    description := "Generic implementation of the IETF `Signing Http Messages` RFC",
    libraryDependencies += cats.bobcats.value
  )
  .jsSettings(
    scalacOptions ++= scala3jsOptions // ++= is really important. Do NOT use `:=` - that will block testing
  )
  .jvmSettings(
    scalacOptions := scala3Options,
    libraryDependencies ++= java.bouncy
  )
  .dependsOn(rfc8941)

lazy val testUtils = crossProject(JVMPlatform, JSPlatform)
  .in(file("test"))
  .settings(commonSettings*)
  .settings(
    name        := "test utils",
    description := "Test Utilities"
  )

lazy val ietfSigHttpTests = crossProject(JVMPlatform, JSPlatform)
  .in(file("ietfSigTests"))
  .settings(commonSettings*)
  .settings(
    name        := "IETF Http Signature Tests",
    description := "Generic tests for generic implementation of IETF `Signing Http Messages`",
    libraryDependencies ++= Seq(
      tests.munitEffect.value,
      cats.caseInsensitive.value,
      tests.munit.value % Test,
      tests.catsEffectTestKit.value,
      cats.bobcats.value classifier ("tests"),        // bobcats test examples,
      cats.bobcats.value classifier ("tests-sources") // bobcats test examples
    )
  )
  .jsSettings(
    scalacOptions ++= scala3jsOptions // ++= is really important. Do NOT use `:=` - that will block testing
  )
  .jvmSettings(
    scalacOptions := scala3Options,
    libraryDependencies ++= java.bouncy
  )
  .dependsOn(ietfSigHttp)

// we only use Java akka here (doing akka-js would be a whole project by itself)
lazy val akkaSig = project
  .in(file("akka"))
  .settings(commonSettings*)
  .settings(
    name          := "Akka Http Signature",
    description   := "Signing HTTP Messages parser for Akka HTTP Messages",
    scalacOptions := scala3Options,
    libraryDependencies ++= Seq(
      akka.http.value,
      akka.stream.value,
      akka.typed.value,
      cats.catsEffect.value
      //			java.nimbusDS
    ),
    libraryDependencies ++= java.bouncy
  ).dependsOn(ietfSigHttp.jvm, ietfSigHttpTests.jvm % Test)

lazy val http4sSig = crossProject(JVMPlatform, JSPlatform)
  .in(file("http4s"))
  .settings(commonSettings*)
  .settings(
    name        := "http4s Http Signature",
    description := "Signing HTTP Messages parser for http4s headers library",
    libraryDependencies ++= Seq(
      http4s.client.value,
      http4s.theDsl.value
    )
  )
  .jsSettings(
    scalacOptions ++= scala3jsOptions, // ++= is really important. Do NOT use `:=` - that will block testing
    libraryDependencies ++= Seq(
      cats.bobcats.value     % Test,
      tests.scalaCheck.value % Test,
      tests.discipline.value % Test,
      tests.laws.value       % Test
    )
  )
  .jvmSettings(
    scalacOptions := scala3Options,
    libraryDependencies ++= java.bouncy
  )
  .dependsOn(ietfSigHttp, ietfSigHttpTests % Test, testUtils % Test)

lazy val scala3Options = Seq(
//    "-language:experimental.saferExceptions",
  // "-classpath", "foo:bar:...",         // Add to the classpath.
  // "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-unchecked",   // Enable additional warnings where generated code depends on assumptions.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
//  "-explain", // Explain errors in more detail.
  // "-explain-types",                    // Explain type errors in more detail.
  "-indent", // Together with -rewrite, remove {...} syntax when possible due to significant indentation.
  // "-no-indent",                        // Require classical {...} syntax, indentation is not significant.
  "-new-syntax", // Require `then` and `do` in control expressions.
  // "-old-syntax",                       // Require `(...)` around conditions.
  // "-language:Scala2",                  // Compile Scala 2 code, highlight what needs updating
  // "-language:strictEquality",          // Require +derives Eql+ for using == or != comparisons
  // "-rewrite",                          // Attempt to fix code automatically. Use with -indent and ...-migration.
  // "-scalajs",                          // Compile in Scala.js mode (requires scalajs-library.jar on the classpath).
  "-source:future", // Choices: future and future-migration. I use this to force future deprecation warnings, etc.
  // "-Xfatal-warnings",                  // Fail on warnings, not just errors
  // "-Xmigration",                       // Warn about constructs whose behavior may have changed since version.
  // "-Ysafe-init",                       // Warn on field access before initialization
  "-Yexplicit-nulls" // For explicit nulls behavior.
)
lazy val scala3jsOptions = Seq(
  //  "-language:experimental.saferExceptions",
  // "-classpath", "foo:bar:...",         // Add to the classpath.
  "-indent", // Together with -rewrite, remove {...} syntax when possible due to significant indentation.
  "-new-syntax", // Require `then` and `do` in control expressions.
  "-source:future", // Choices: future and future-migration. I use this to force future deprecation warnings, etc.
  "-Yexplicit-nulls" // For explicit nulls behavior.
  // if the following are set we get an error using ++= stating they are already set
  //	"-deprecation", "-unchecked", "-feature",
)
