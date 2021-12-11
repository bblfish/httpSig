import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.{Def, _}

object Dependencies {
	object Ver {
		val scala = "3.1.0"
		val http4s = "1.0.0-M29"
	}


	//https://http4s.org/v1.0/client/
	object http4s {
		def apply(packg: String): Def.Initialize[sbt.ModuleID] =
			Def.setting("org.http4s" %%% packg % Ver.http4s)
		lazy val core    = http4s("http4s-core")
		lazy val client  = http4s("http4s-client")
		lazy val server  = http4s("http4s-server")
		lazy val theDsl  = http4s("http4s-dsl")
		// https://github.com/http4s/http4s-dom
		//https://search.maven.org/artifact/org.http4s/http4s-dom_sjs1_3/1.0.0-M29/jar
		lazy val Dom = http4s("http4s-dom")
	}

	object cats {
		// https://github.com/typelevel/munit-cats-effect
		lazy val munitEffect = Def.setting("org.typelevel" %%% "munit-cats-effect-3" % "1.0.7")
		// https://search.maven.org/artifact/org.typelevel/cats-parse_3/0.3.6/jar
		lazy val parse =  Def.setting("org.typelevel" %%% "cats-parse" % "0.3.6")
	}

	object akka {
		val CoreVersion = "2.6.17"
		val HttpVersion = "10.2.7"
		def apply(id: String, version: String) =
			Def.setting("com.typesafe.akka" %% id % version cross CrossVersion.for3Use2_13)

		lazy val typed =	akka("akka-actor-typed", CoreVersion)
		lazy val stream = akka("akka-stream", CoreVersion)
		lazy val http = akka("akka-http", HttpVersion)
	}

	val scalajsDom = Def.setting("org.scala-js" %%% "scalajs-dom" % "2.0.0")
	val bananaRdfLib = Def.setting("net.bblfish.rdf" %%% "rdflibJS" % "0.9-SNAPSHOT")

	val munit = Def.setting("org.scalameta" %%% "munit" % "1.0.0-M1")
	val utest = Def.setting("com.lihaoyi" %%% "utest" % "0.7.10")

	val modelJS = Def.setting("net.bblfish.rdf" %%% "rdf-model-js" % "0.1a-SNAPSHOT")
	//needed for modelJS
	val sonatypeSNAPSHOT: MavenRepository = "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

	object NPM {
		val n3 = "n3" -> "1.11.2"
		val jsDom = "jsdom" -> "18.1.1"
	}
}
  

