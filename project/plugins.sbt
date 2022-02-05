
//selenium testing
libraryDependencies += "org.scala-js" %% "scalajs-env-selenium" % "1.1.1"

// see https://github.com/djspiewak/sbt-spiewak
addSbtPlugin("com.codecommit" % "sbt-spiewak-sonatype" % "0.23.0")
// see https://github.com/scalameta/sbt-scalafmt
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
/**
 * ScalaJS cross project sbt plugin
 *
 * @see http://www.scala-js.org/doc/project/cross-build.html
 * @see https://github.com/portable-scala/sbt-crossproject
 */
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.8.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")

