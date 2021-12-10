// dom lib for nodejs as per https://www.scala-js.org/doc/tutorial/basic/
//libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0"
// but we try the  fork from https://github.com/exoego/scala-js-env-jsdom-nodejs
libraryDependencies += "net.exoego" %% "scalajs-env-jsdom-nodejs" % "2.1.0" cross CrossVersion.for3Use2_13

//fix bug https://github.com/scala-js/scala-js-js-envs/issues/12
//this line should be removed with scalajs 1.8.0
libraryDependencies += "org.scala-js" %% "scalajs-env-nodejs" % "1.2.1"

/**
 * ScalaJS cross project sbt plugin
 * @see http://www.scala-js.org/doc/project/cross-build.html
 * @see https://github.com/portable-scala/sbt-crossproject
 */
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0")

addSbtPlugin("org.scala-js"              % "sbt-scalajs"              % "1.7.1")

//https://search.maven.org/search?q=a:sbt-scalajs-bundler
//https://scalacenter.github.io/scalajs-bundler/
addSbtPlugin("ch.epfl.scala"             % "sbt-scalajs-bundler"      % "0.21.0-RC1")

// https://github.com/DavidGregory084/sbt-tpolecat
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"             % "0.1.20")

// https://scalablytyped.org/docs/plugin
addSbtPlugin("org.scalablytyped.converter" % "sbt-converter" % "1.0.0-beta36")
