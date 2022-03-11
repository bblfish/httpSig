//selenium testing
libraryDependencies += "org.scala-js" %% "scalajs-env-selenium" % "1.1.1"

resolvers += Resolver.sonatypeRepo("snapshots")

// https://scalameta.org/scalafmt/docs/installation.html
// see https://oss.sonatype.org/content/repositories/snapshots/org/scalameta/sbt-scalafmt_2.12_1.0/
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6+9-9da40876-SNAPSHOT")

// https://typelevel.org/sbt-typelevel/index.html
// https://search.maven.org/search?q=a:sbt-typelevel
addSbtPlugin("org.typelevel" % "sbt-typelevel" % "0.4.6")
addSbtPlugin("org.scala-js"  % "sbt-scalajs"   % "1.9.0")
addSbtPlugin("com.eed3si9n"  % "sbt-buildinfo" % "0.11.0")
