import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.firefox.{FirefoxOptions, FirefoxProfile}
import org.openqa.selenium.remote.server.{DriverFactory, DriverProvider}
import org.scalajs.jsenv.selenium.SeleniumJSEnv
import Dependencies._

import JSEnv._

name := "httpSig"

ThisBuild / tlBaseVersion  := "0.2"
ThisBuild / tlUntaggedAreSnapshots := true

ThisBuild / organization := "net.bblfish"
ThisBuild / organizationName := "Henry Story"
ThisBuild / startYear := Some(2021)
ThisBuild / developers := List(
	tlGitHubDev("bblfish", "Henry Story")
)
enablePlugins(TypelevelCiReleasePlugin)
ThisBuild / tlCiReleaseBranches := Seq("main")
ThisBuild / tlSonatypeUseLegacyHost := false // TODO remove

ThisBuild / crossScalaVersions := Seq("3.1.0")

ThisBuild / homepage := Some(url("https://github.com/bblfish/httpSig"))
ThisBuild / scmInfo := Some(
	ScmInfo(url("https://github.com/bblfish/httpSig"), "git@github.com:bblfish/httpSig.git"))

tlReplaceCommandAlias("ciJS", CI.AllCIs.map(_.toString).mkString)
addCommandAlias("ciFirefox", CI.Firefox.toString)
addCommandAlias("ciChrome", CI.Chrome.toString)

addCommandAlias("prePR", "; root/clean; scalafmtSbt; +root/scalafmtAll; +root/headerCreate")

ThisBuild / resolvers += sonatypeSNAPSHOT

lazy val useJSEnv =
	settingKey[JSEnv]("Use Node.js or a headless browser for running Scala.js tests")

Global / useJSEnv := NodeJS

ThisBuild / Test / jsEnv := {
	val old = (Test / jsEnv).value

	useJSEnv.value match {
	case NodeJS => old
	case Firefox =>
		val options = new FirefoxOptions()
		options.setHeadless(true)
		new SeleniumJSEnv(options)
	case Chrome =>
		val options = new ChromeOptions()
		options.setHeadless(true)
		new SeleniumJSEnv(options)
	}
}

lazy val commonSettings = Seq(
	name := "HttpSig Library",
	description := "Solid App",
	startYear := Some(2021),
	updateOptions := updateOptions.value.withCachedResolution(true) //to speed up dependency resolution
)

lazy val rfc8941 = crossProject(JVMPlatform, JSPlatform)
	.in(file("rfc8941"))
	.settings(commonSettings: _*)
	.settings(
		name := "rfc8941",
		description := "RFC8941 (Structured Field Values) parser",
		libraryDependencies += cats.parse.value,
		libraryDependencies += tests.munit.value % Test
	).jsSettings(
		scalacOptions ++= scala3jsOptions, //++= is really important. Do NOT use `:=` - that will block testing
		Test / scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) } //required for munit to run
	).jvmSettings(
		scalacOptions := scala3Options
	)

// we only use Java akka here (doing akka-js would be a whole project by itself)
lazy val akkaSig = project
	.in(file("akka"))
	.settings(commonSettings: _*)
	.settings(
		name := "AkkaHttpSig",
		description := "Signing HTTP Messages parser for Akka headers",
		scalacOptions := scala3Options,
		libraryDependencies ++= Seq(
			akka.http.value, akka.stream.value, akka.typed.value
//			java.nimbusDS
		),
		libraryDependencies ++= java.bouncy
	).dependsOn(ietfSigHttp.jvm, ietfSigHttpTests.jvm % Test)


lazy val ietfSigHttpTests = crossProject(JVMPlatform, JSPlatform)
	.in(file("ietfSigTests"))
	.settings(commonSettings: _*)
	.settings(
		name := "ietfSigTests",
		description := "Generic tests for generic implementation of IETF `Signing Http Messages`",
		libraryDependencies ++= Seq(
			tests.munitEffect.value,
			cats.bobcats.value classifier( "tests" ), // bobcats test examples,
			cats.bobcats.value classifier( "tests-sources" ) // bobcats test examples
		)
	)
	.jsSettings(
		scalacOptions ++= scala3jsOptions //++= is really important. Do NOT use `:=` - that will block testing
	)
	.jvmSettings(
		scalacOptions := scala3Options,
		libraryDependencies ++= java.bouncy
	)
	.dependsOn(ietfSigHttp)

lazy val ietfSigHttp = crossProject(JVMPlatform, JSPlatform)
	.in(file("ietfSig"))
	.settings(commonSettings: _*)
	.settings(
		name := "ietfSig",
		description := "generic implementation of IETF `Signing Http Messages`",
		libraryDependencies += cats.bobcats.value
	)
	.jsSettings(
		scalacOptions ++= scala3jsOptions //++= is really important. Do NOT use `:=` - that will block testing
	)
	.jvmSettings(
		scalacOptions := scala3Options,
		libraryDependencies ++= java.bouncy
	)
	.dependsOn(rfc8941)

// we only use Java akka here (doing akka-js would be a whole project by itself)
lazy val http4sSig = crossProject(JVMPlatform, JSPlatform)
	.in(file("http4s"))
	.settings(commonSettings: _*)
	.settings(
		name := "http4s Sig",
		description := "Signing HTTP Messages parser for http4s headers library",
		libraryDependencies ++= Seq(
			http4s.client.value,
			http4s.theDsl.value
	))
	.jsSettings(
		scalacOptions ++= scala3jsOptions, //++= is really important. Do NOT use `:=` - that will block testing
		libraryDependencies ++= Seq(
			cats.bobcats.value % Test,
			tests.scalaCheck.value % Test,
			tests.discipline.value % Test,
			tests.laws.value % Test
	))
	.jvmSettings(
		scalacOptions := scala3Options,
		libraryDependencies ++= java.bouncy,
	)
	.dependsOn(ietfSigHttp, ietfSigHttpTests % Test, testUtils % Test)

lazy val testUtils = crossProject(JVMPlatform, JSPlatform)
	.in(file("test"))
	.settings(commonSettings: _*)
	.settings(
		name := "testUtils",
		description := "Test Utilities"
	)

val scala3Options = Seq(
	// "-classpath", "foo:bar:...",         // Add to the classpath.
	//"-encoding", "utf-8",                // Specify character encoding used by source files.
	"-deprecation", // Emit warning and location for usages of deprecated APIs.
	"-unchecked", // Enable additional warnings where generated code depends on assumptions.
	"-feature", // Emit warning and location for usages of features that should be imported explicitly.
	//	"-explain",                          // Explain errors in more detail.
	//"-explain-types",                    // Explain type errors in more detail.
	"-indent", // Together with -rewrite, remove {...} syntax when possible due to significant indentation.
	// "-no-indent",                        // Require classical {...} syntax, indentation is not significant.
	"-new-syntax", // Require `then` and `do` in control expressions.
	// "-old-syntax",                       // Require `(...)` around conditions.
	// "-language:Scala2",                  // Compile Scala 2 code, highlight what needs updating
	//"-language:strictEquality",          // Require +derives Eql+ for using == or != comparisons
	// "-rewrite",                          // Attempt to fix code automatically. Use with -indent and ...-migration.
	// "-scalajs",                          // Compile in Scala.js mode (requires scalajs-library.jar on the classpath).
	"-source:future", // Choices: future and future-migration. I use this to force future deprecation warnings, etc.
	// "-Xfatal-warnings",                  // Fail on warnings, not just errors
	// "-Xmigration",                       // Warn about constructs whose behavior may have changed since version.
	// "-Ysafe-init",                       // Warn on field access before initialization
	"-Yexplicit-nulls" // For explicit nulls behavior.
)
val scala3jsOptions = Seq(
	"-indent", // Together with -rewrite, remove {...} syntax when possible due to significant indentation.
	"-new-syntax", // Require `then` and `do` in control expressions.
	"-source:future", // Choices: future and future-migration. I use this to force future deprecation warnings, etc.
	"-Yexplicit-nulls" // For explicit nulls behavior.
	// if the following are set we get an error using ++= stating they are already set
	//	"-deprecation", "-unchecked", "-feature",
)

