//selenium testing
libraryDependencies += "org.scala-js" %% "scalajs-env-selenium" % "1.1.1"

resolvers += Resolver.sonatypeRepo("snapshots")

// https://typelevel.org/sbt-typelevel/index.html
addSbtPlugin("org.typelevel" % "sbt-typelevel" % "0.4.5")
addSbtPlugin("org.scala-js"  % "sbt-scalajs"   % "1.9.0")
addSbtPlugin("com.eed3si9n"  % "sbt-buildinfo" % "0.11.0")
