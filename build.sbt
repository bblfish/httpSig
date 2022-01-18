import Dependencies.{cats, _}

organization := "cosy.run"
name := "httpSig"
version := "0.2-SNAPSHOT"
scalaVersion := Ver.scala

lazy val commonSettings = Seq(
	name := "HttpSig Library",
	version := "0.2-SNAPSHOT",
	description := "Solid App",
	startYear := Some(2021),
	scalaVersion := Ver.scala,
	updateOptions := updateOptions.value.withCachedResolution(true) //to speed up dependency resolution
)
lazy val rfc8941 = crossProject(JVMPlatform, JSPlatform)
	.crossType(CrossType.Full)
	.in(file("rfc8941"))
	.settings(commonSettings: _*)
//	.enablePlugins(ScalaJSBundlerPlugin)
	.settings(
		name := "rfc8941",
		description := "RFC8941 (Structured Field Values) parser",
//		scalacOptions := scala3Options,
		libraryDependencies += cats.parse.value,
		libraryDependencies += munit.value % Test
		//		// useYarn := true, // makes scalajs-bundler use yarn instead of npm
		// Test / requireJsDomEnv := true,
		// scalaJSUseMainModuleInitializer := true,
		// scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)), // configure Scala.js to emit a JavaScript module instead of a top-level script
		// ESModule cannot be used because we are using ScalaJSBundlerPlugin
		// scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },

		//		fastOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack.config.dev.js"),

		// https://github.com/rdfjs/N3.js/
		// do I also need to run `npm install n3` ?
		//		Compile / npmDependencies += NPM.n3,
		//		Test / npmDependencies += NPM.n3,
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
			akka.http.value, akka.stream.value, akka.typed.value,
			java.nimbusDS,
			cats.bobcats.value % Test classifier( "tests" ), // bobcats test examples,
			cats.bobcats.value % Test classifier( "tests-sources" ) // bobcats test examples
		),
		libraryDependencies ++= Seq(
			munit.value % Test,
			cats.munitEffect.value % Test
		) ++ java.bouncy
	).dependsOn(ietfSigHttp.jvm)

lazy val ietfSigHttp = crossProject(JVMPlatform, JSPlatform)
	.in(file("ietfSig"))
	.settings(commonSettings: _*)
	.settings(
		name := "ietfSig",
		description := "generic implementation of IETF `Signing Http Messages`",
		libraryDependencies ++= Seq(
			cats.bobcats.value,
			munit.value % Test,
			cats.munitEffect.value % Test
		)
	)
	.jsSettings(
		scalacOptions ++= scala3jsOptions, //++= is really important. Do NOT use `:=` - that will block testing
		Test / scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) } //required for munit to run
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
		),
		libraryDependencies ++= Seq(
			munit.value % Test,
			cats.munitEffect.value % Test,
			cats.bobcats.value % Test classifier( "tests" ), // bobcats test examples,
			cats.bobcats.value % Test classifier( "tests-sources" ) // bobcats test examples
		)
	)
	.jsSettings(
		scalacOptions ++= scala3jsOptions, //++= is really important. Do NOT use `:=` - that will block testing
		Test / scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) } //required for munit to run
	)
	.jvmSettings(
		scalacOptions := scala3Options,
		libraryDependencies ++= java.bouncy
	)
	.dependsOn(ietfSigHttp, testUtils % Test)

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

