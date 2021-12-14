import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.{Def, _}

object Dependencies {
	val scalajsDom = Def.setting("org.scala-js" %%% "scalajs-dom" % "2.0.0")
	val bananaRdfLib = Def.setting("net.bblfish.rdf" %%% "rdflibJS" % "0.9-SNAPSHOT")
	// https://github.com/scalameta/munit
	val munit = Def.setting("org.scalameta" %%% "munit" % "1.0.0-M1")
	val utest = Def.setting("com.lihaoyi" %%% "utest" % "0.7.10")
	val modelJS = Def.setting("net.bblfish.rdf" %%% "rdf-model-js" % "0.1a-SNAPSHOT")
	//needed for modelJS
	val sonatypeSNAPSHOT: MavenRepository = "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
	object Ver {
		val scala = "3.1.0"
		val http4s = "1.0.0-M29"
	}
	//https://http4s.org/v1.0/client/
	object http4s {
		lazy val core = http4s("http4s-core")
		lazy val client = http4s("http4s-client")
		lazy val server = http4s("http4s-server")
		lazy val theDsl = http4s("http4s-dsl")
		// https://github.com/http4s/http4s-dom
		//https://search.maven.org/artifact/org.http4s/http4s-dom_sjs1_3/1.0.0-M29/jar
		lazy val Dom = http4s("http4s-dom")
		def apply(packg: String): Def.Initialize[sbt.ModuleID] =
			Def.setting("org.http4s" %%% packg % Ver.http4s)
	}
	object cats {
		// https://github.com/typelevel/munit-cats-effect
		lazy val munitEffect = Def.setting("org.typelevel" %%% "munit-cats-effect-3" % "1.0.7")
		// https://search.maven.org/artifact/org.typelevel/cats-parse_3/0.3.6/jar
		lazy val parse = Def.setting("org.typelevel" %%% "cats-parse" % "0.3.6")
	}
	object akka {
		lazy val typed = akka("akka-actor-typed", CoreVersion)
		lazy val stream = akka("akka-stream", CoreVersion)
		lazy val http = akka("akka-http", HttpVersion)
		val CoreVersion = "2.6.17"
		val HttpVersion = "10.2.7"
		def apply(id: String, version: String) =
			Def.setting("com.typesafe.akka" %% id % version cross CrossVersion.for3Use2_13)
	}
	object java {
		/**
		 * Apache 2 License
		 *
		 * @see https://connect2id.com/products/nimbus-jose-jwt/examples/jwk-conversion
		 */
		val nimbusDS = "com.nimbusds" % "nimbus-jose-jwt" % "9.15.2"
		/**
		 * BouncyCastle (for parsing PEM encoded objects at present in test)
		 * MIT style License
		 *
		 * @see https://www.bouncycastle.org/latest_releases.html
		 * @see https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15to18/
		 */
		val bouncy = Seq(
			//"org.bouncycastle" % "bcprov-jdk15to18" % bouncyVersion,
			//"org.bouncycastle" % "bctls-jdk15to18" % bouncyVersion,
			"org.bouncycastle" % "bcpkix-jdk15to18" % "1.69" % Test
		)
	}
	object NPM {
		val n3 = "n3" -> "1.11.2"
		val jsDom = "jsdom" -> "18.1.1"
	}
}
  

