import Dependencies._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport.webpackConfigFile

organization := "cosy.run"
name := "httpSig"
version := "0.2-SNAPSHOT"
scalaVersion := Ver.scala

import org.scalajs.jsenv.nodejs.NodeJSEnv

lazy val commonSettings = Seq(
   name := "HttpSig Library",
   version := "0.2-SNAPSHOT",
   description := "Solid App",
   startYear := Some(2021),
   scalaVersion := Ver.scala,
   updateOptions := updateOptions.value.withCachedResolution(true) //to speed up dependency resolution
)

val scala3jsOptions =  Seq(
   // "-classpath", "foo:bar:...",         // Add to the classpath.
   //"-encoding", "utf-8",                // Specify character encoding used by source files.
   "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
   "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
   "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
   //"-explain",                          // Explain errors in more detail.
   //"-explain-types",                    // Explain type errors in more detail.
   "-indent",                           // Together with -rewrite, remove {...} syntax when possible due to significant indentation.
   // "-no-indent",                        // Require classical {...} syntax, indentation is not significant.
   "-new-syntax",                       // Require `then` and `do` in control expressions.
   // "-old-syntax",                       // Require `(...)` around conditions.
   // "-language:Scala2",                  // Compile Scala 2 code, highlight what needs updating
   //"-language:strictEquality",          // Require +derives Eql+ for using == or != comparisons
   // "-rewrite",                          // Attempt to fix code automatically. Use with -indent and ...-migration.
   // "-scalajs",                          // Compile in Scala.js mode (requires scalajs-library.jar on the classpath).
   "-source:future",                       // Choices: future and future-migration. I use this to force future deprecation warnings, etc.
   // "-Xfatal-warnings",                  // Fail on warnings, not just errors
   // "-Xmigration",                       // Warn about constructs whose behavior may have changed since version.
   // "-Ysafe-init",                       // Warn on field access before initialization
   "-Yexplicit-nulls"                  // For explicit nulls behavior.
)


lazy val httpSig = crossProject(JVMPlatform,JSPlatform)
	.crossType(CrossType.Full)
	.in(file("httpSig"))
	.settings(commonSettings:_*)
	.enablePlugins(ScalaJSPlugin)
	.enablePlugins(ScalaJSBundlerPlugin)
	.settings(
		name := "HttpSig",
		description := "Http Sig parsing library",
		// scalacOptions := scala3jsOptions,
		libraryDependencies ++= Seq(
			http4s.client.value,
			cats.parse.value
		),
		libraryDependencies ++= Seq(
			munit.value % Test
		)
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
	)

lazy val httpSigJVM = httpSig.jvm
lazy val httpSigJS =  httpSig.js

// we only use Java akka here (doing akka-js would be a whole project by itself)
lazy val akkaSig = project
	.in(file("akka"))
	.settings(commonSettings:_*)
	.settings(
		name := "HttpSigAkka",
		description := "Http Sig parsing lib for Akka headers",
		// scalacOptions := scala3jsOptions,
		libraryDependencies ++= Seq(
			akka.http.value, akka.stream.value, akka.typed.value
		),
		libraryDependencies ++= Seq(
			munit.value % Test,
			cats.munitEffect.value % Test
		)
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
//		Test / npmDependencies += NPM.n3
	).dependsOn(httpSig.jvm)
