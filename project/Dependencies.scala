import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*
import sbt.{Def, *}

object Dependencies {
  lazy val scalajsDom   = Def.setting("org.scala-js" %%% "scalajs-dom" % "2.0.0")
  lazy val bananaRdfLib = Def.setting("net.bblfish.rdf" %%% "rdflibJS" % "0.9-SNAPSHOT")

  object tests {
    //	val utest = Def.setting("com.lihaoyi" %%% "utest" % "0.7.10")
    // https://github.com/scalameta/munit
    lazy val munit = Def.setting("org.scalameta" %%% "munit" % "1.0.0-M6")
    // https://github.com/typelevel/munit-cats-effect
    lazy val munitEffect = Def.setting("org.typelevel" %%% "munit-cats-effect" % "2.0.0-M3")

    lazy val discipline = Def.setting("org.typelevel" %%% "discipline-munit" % "1.0.9")
    lazy val laws       = Def.setting("org.typelevel" %%% "cats-laws" % "2.7.0")
    lazy val scalaCheck = Def.setting("org.scalameta" %%% "munit-scalacheck" % "0.7.29")
  }
  lazy val modelJS = Def.setting("net.bblfish.rdf" %%% "rdf-model-js" % "0.1a-SNAPSHOT")
  // needed for modelJS
  lazy val sonatypeSNAPSHOT: MavenRepository =
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    
  object Ver {
    val scala = "3.1.3"
  }
  
  // https://http4s.org/v1.0/client/
  object http4s {
    val Ver = "1.0.0-M37"

    lazy val core   = http4s("http4s-core")
    lazy val client = http4s("http4s-client")
    lazy val server = http4s("http4s-server")
    lazy val theDsl = http4s("http4s-dsl")

    // https://search.maven.org/artifact/org.http4s/http4s-dom_sjs1_3/1.0.0-M30/jar
    // https://github.com/http4s/http4s-dom
    lazy val Dom = http4s("http4s-dom")
    def apply(packg: String): Def.Initialize[sbt.ModuleID] =
      Def.setting("org.http4s" %%% packg % Ver)
  }

  object cats {
    // https://search.maven.org/artifact/org.typelevel/cats-parse_3/0.3.6/jar
    // https://search.maven.org/artifact/org.typelevel/cats-parse_3
    lazy val parse = Def.setting("org.typelevel" %%% "cats-parse" % "0.3-25-7b524da")
    // https://oss.sonatype.org/content/repositories/snapshots/net/bblfish/crypto/bobcats_3/
    lazy val bobcats =
      Def.setting("net.bblfish.crypto" %%% "bobcats" % "0.2-69106e6-SNAPSHOT")
      
    // https://index.scala-lang.org/typelevel/cats-effect/artifacts/cats-effect/3.3.14
    // todo: other libraries deped on cats effect, is this the right version?
    lazy val catsEffect = Def.setting("org.typelevel" %% "cats-effect" % "3.3.14")
  }
  
  object akka {
    lazy val typed       = akka("akka-actor-typed", CoreVersion)
    lazy val stream      = akka("akka-stream", CoreVersion)
    lazy val http        = akka("akka-http", HttpVersion)
    lazy val CoreVersion = "2.6.18"
    lazy val HttpVersion = "10.2.8"
    def apply(id: String, version: String): Def.Initialize[ModuleID] =
      Def.setting("com.typesafe.akka" %% id % version cross CrossVersion.for3Use2_13)
  }
  
  object java {

    /** Apache 2 License
      *
      * @see
      *   https://connect2id.com/products/nimbus-jose-jwt/examples/jwk-conversion
      */
    lazy val nimbusDS = "com.nimbusds" % "nimbus-jose-jwt" % "9.15.2"

    /** BouncyCastle (for parsing PEM encoded objects at present in test) MIT style License
      *
      * @see
      *   https://www.bouncycastle.org/latest_releases.html
      * @see
      *   https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15to18/
      */
    lazy val bouncy = Seq(
      // "org.bouncycastle" % "bcprov-jdk15to18" % bouncyVersion,
      // "org.bouncycastle" % "bctls-jdk15to18" % bouncyVersion,
      "org.bouncycastle" % "bcpkix-jdk15to18" % "1.69" % Test
    )
  }
  object NPM {
    lazy val n3    = "n3"    -> "1.11.2"
    lazy val jsDom = "jsdom" -> "18.1.1"
  }
}
