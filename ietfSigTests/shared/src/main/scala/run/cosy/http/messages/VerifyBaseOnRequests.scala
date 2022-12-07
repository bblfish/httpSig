package run.cosy.http.messages

import bobcats.Verifier
import bobcats.Verifier.SigningString
import cats.MonadError
import cats.effect.IO
import cats.effect.syntax.all.*
import cats.effect.testkit.TestControl
import munit.CatsEffectSuite
import run.cosy.http.auth.*
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.headers.Rfc8941.{SfDict, SfInt, SfString}
import run.cosy.http.headers.SigIn.*
import run.cosy.http.headers.{HttpSig, ReqSigInput, Rfc8941}
import run.cosy.http.messages.HttpMessageDB.RequestStr
import run.cosy.http.messages.TestHttpMsgInterpreter
import run.cosy.http.{Http, HttpOps}

import scala.collection.immutable.ListSet

/** This test suite looks at statically built SigInput requests using functions. This is useful for
  * clients writing signatures.
  */
open class VerifyBaseOnRequests[F[_], H <: Http](
    hsel: ReqSelectors[F, H] // we don't pass it implicitly, because we need to add some headers.
)(using
    hops: HttpOps[H],
    interpret: TestHttpMsgInterpreter[F, H],
    // needed for testing signatures
//    ME: MonadError[F, Throwable],
//    V: bobcats.Verifier[F],
) extends CatsEffectSuite:
   val hds = HeaderIds

   import HeaderSelectors.*
   import hsel.*
   import hsel.RequestHd.*
   import run.cosy.http.utils.StringUtils.*

   val msgSigfns: MessageSignature[F, H] = new MessageSignature[F, H]
   import msgSigfns.*

   val DB = HttpMessageDB
   
   object testIds:
      val `x-ows-header` = HeaderId("x-ows-header").toTry.get
      val `x-obs-fold-header` = HeaderId("x-obs-fold-header").toTry.get
      val `example-dict` = HeaderId.dict("example-dict").toTry.get
      val `example-header` = HeaderId("example-header").toTry.get
      val `x-empty-header` = HeaderId("x-empty-header").toTry.get
      val all = Seq(`x-ows-header`, `x-obs-fold-header`, `example-dict`, `example-header`, `x-empty-header`)
      
   val `x-ows-header`      = hsel.onRequest(testIds.`x-ows-header`)
   val `x-obs-fold-header` = hsel.onRequest(testIds.`x-obs-fold-header`)
   val `example-dict`      = hsel.onRequest(testIds.`example-dict`)
   val `example-header`    = hsel.onRequest(testIds.`example-header`)
   val `x-empty-header`    = hsel.onRequest(testIds.`x-empty-header`)
   // one should prefer the `@...` versions over these two
   val date = hsel.onRequest(hds.Response.`date`)
   val host = hsel.onRequest(hds.retrofit.`host`)
  
   //todo: it may be useful if hsel had a list of all the headers it used
   // so that there was not a danger of being out of sync with the verifier in ReqComponentDB...
   // we did not duplicate the headerIds
   //needed to verify signatures
   given selectorDB: ReqComponentDB[F, H] = ReqComponentDB(hsel, HeaderIds.all ++ testIds.all)
   
   List(DB.`2.4_Req_Ex`, DB.`2.4_Req_v2`).zipWithIndex.foreach { (msg, i) =>
     val req = interpret.asRequest(msg)
     test("apply ReqSigInput selector on ex from 2.4 v." + i) {
       val rsel: List[RequestSelector[F, H]] =
         List(
           `@method`,
           `@authority`,
           `@path`,
           `content-digest`(LS),
           `content-length`(LS),
           `content-type`(LS)
         )
       val rsi = new ReqSigInput(
         rsel,
         ListSet(Created(1618884473L), KeyId(sf"test-key-rsa-pss"))
       )
       val x: Either[ParsingExc, SigningString] = req.sigBase(rsi)
       assertEquals(
         x.flatMap(s => s.decodeAscii),
         Right(
           """"@method": POST
              |"@authority": example.com
              |"@path": /foo
              |"content-digest": sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX\
              |  +TaPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
              |"content-length": 18
              |"content-type": application/json
              |"@signature-params": ("@method" "@authority" "@path" \
              |  "content-digest" "content-length" "content-type")\
              |  ;created=1618884473;keyid="test-key-rsa-pss"""".rfc8792single
         )
       )
     }
   }

   case class TestBase(
     doc: String,
     reqStr: DB.RequestStr,
     sigIn: ReqSigInput[F, H],
     baseResult: String,
     success: Boolean = true
   )

   def signatureParams(str: String) = """"@signature-params": """ + str

   {
     List(
       TestBase("empty SigInput", DB.`§2.1_HeaderField`, ReqSigInput()(), signatureParams("()")),
       TestBase(
         "SigInput from in §2.1 with old request",
         DB.`§2.1_HeaderField`,
         new ReqSigInput(List(
           host(LS),
           date(LS),
           `x-ows-header`(LS),
           `x-obs-fold-header`(LS),
           `cache-control`(LS),
           `example-dict`(LS)
         )),
         """"host": www.example.com
            |"date": Sat, 07 Jun 2014 20:51:35 GMT
            |"x-ows-header": Leading and trailing whitespace.
            |"x-obs-fold-header": Obsolete line folding.
            |"cache-control": max-age=60, must-revalidate
            |"example-dict": a=1,   b=2;x=1;y=2,   c=(a   b   c), d
            |""".rfc8792single + signatureParams(
           """("host" "date" "x-ows-header" "x-obs-fold-header" "cache-control" "example-dict")"""
         )
       ),
       TestBase(
         "SigInput from §2.1 with new request",
         DB.`§2.1_HeaderField_2`,
         //       """("host" "date" "x-ows-header" "x-obs-fold-header" "cache-control" "example-dict")""",
         ReqSigInput(
           host(LS),
           date(LS),
           `x-ows-header`(LS),
           `x-obs-fold-header`(LS),
           `cache-control`(LS),
           `example-dict`(LS)
         )(),
         """"host": www.example.com
            |"date": Tue, 20 Apr 2021 02:07:56 GMT
            |"x-ows-header": Leading and trailing whitespace.
            |"x-obs-fold-header": Obsolete line folding.
            |"cache-control": must-revalidate, max-age=60
            |"example-dict": a=1,    b=2;x=1;y=2,   c=(a   b   c), d
            |""".rfc8792single + signatureParams(
           """("host" "date" "x-ows-header" "x-obs-fold-header" "cache-control" "example-dict")"""
         )
       ),
       TestBase(
         "SigInput with ;key and ;sf and ;bs selectors",
         DB.`§2.1_HeaderField_2`,
         ReqSigInput(
           host(LS),
           date(LS),
           `x-empty-header`(LS),
           `x-ows-header`(BS),
           `cache-control`(Dict(sf"max-age")),
           `example-dict`(SF)
         )(),
         """"host": www.example.com
            |"date": Tue, 20 Apr 2021 02:07:56 GMT
            |"x-empty-header": \
            |
            |"x-ows-header";bs: :TGVhZGluZyBhbmQgdHJhaWxpbmcgd2hpdGVzcGFjZS4=:
            |"cache-control";key="max-age": 60
            |"example-dict";sf: a=1, b=2;x=1;y=2, c=(a b c), d
            |""".rfc8792single + signatureParams(
           """("host" "date" "x-empty-header" "x-ows-header";bs "cache-control";key="max-age" "example-dict";sf)"""
         )
       ),
       TestBase(
         "SigInput with example-dict selectors",
         DB.`§2.1_HeaderField_2`,
         ReqSigInput(
           `example-dict`(Dict(sf"a")),
           `example-dict`(Dict(sf"d")),
           `example-dict`(Dict(sf"b")),
           `example-dict`(Dict(sf"c"))
         )(),
         """"example-dict";key="a": 1
            |"example-dict";key="d": ?1
            |"example-dict";key="b": 2;x=1;y=2
            |"example-dict";key="c": (a b c)
            |""".rfc8792single + signatureParams(
           """("example-dict";key="a" "example-dict";key="d" "example-dict";key="b" "example-dict";key="c")"""
         )
       ),
       TestBase(
         "SigInput from §2.1.3, lots of commas",
         DB.`§2.1_HeaderField_2`,
         ReqSigInput(
           `example-header`(LS),
           `example-header`(BS)
         )(),
         """"example-header": value, with, lots, of, commas
            |"example-header";bs: :dmFsdWUsIHdpdGgsIGxvdHM=:, :b2YsIGNvbW1hcw==:
            |""".rfc8792single + signatureParams("""("example-header" "example-header";bs)""")
       ),
       TestBase(
         "SigInput from §2.3, covering @ and header selectors",
         DB.`§2.1_HeaderField_2`,
         ReqSigInput(`@target-uri`, `@authority`, `date`(LS), `cache-control`(LS))(
           KeyId(sf"test-key-rsa-pss"),
           Alg(SigAlg.`rsa-pss-sha512`),
           Created(1618884475),
           Expires(1618884775L)
         ),
         """"@target-uri": https://www.example.com/xyz
            |"@authority": www.example.com
            |"date": Tue, 20 Apr 2021 02:07:56 GMT
            |"cache-control": must-revalidate, max-age=60
            |""".rfc8792single + signatureParams(
           """("@target-uri" "@authority" "date" "cache-control")\
              |   ;keyid="test-key-rsa-pss";alg="rsa-pss-sha512";\
              |   created=1618884475;expires=1618884775""".rfc8792single
         )
       ),
       TestBase(
         "SigBase with attributes, example from §2.5",
         DB.`2.5_POST_req`,
         ReqSigInput(
           `@method`,
           `@authority`,
           `@path`,
           `content-digest`(LS),
           `content-length`(LS),
           `content-type`(LS)
         )(
           Created(SfInt(1618884473L)),
           KeyId(sf"test-key-rsa-pss")
         ),
         """"@method": POST
            |"@authority": example.com
            |"@path": /foo
            |"content-digest": sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX\
            |  +TaPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
            |"content-length": 18
            |"content-type": application/json
            |"@signature-params": ("@method" "@authority" "@path" \
            |  "content-digest" "content-length" "content-type")\
            |  ;created=1618884473;keyid="test-key-rsa-pss"""".rfc8792single
       ),
       TestBase(
         "Selective covered components with tag",
         DB.`B.2_Request`,
         ReqSigInput(`@authority`, `content-digest`(LS), `@query-param`(SfString("Pet")))(
           Created(1618884473L),
           KeyId(SfString("test-key-rsa-pss")),
           Tag(SfString("header-example"))
         ),
         """"@authority": example.com
            |"content-digest": sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX\
            |  +TaPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
            |"@query-param";name="Pet": dog
            |"@signature-params": ("@authority" "content-digest" \
            |  "@query-param";name="Pet")\
            |  ;created=1618884473;keyid="test-key-rsa-pss"\
            |  ;tag="header-example"""".rfc8792single
       ),
       TestBase(
         "B.2.3 full coverage",
         DB.`B.2_Request`,
         ReqSigInput(
           `date`(LS),
           `@method`,
           `@path`,
           `@query`,
           `@authority`,
           `content-type`(LS),
           `content-digest`(LS),
           `content-length`(LS)
         )(
           Created(1618884473L),
           KeyId(sf"test-key-rsa-pss")
         ),
         """"date": Tue, 20 Apr 2021 02:07:55 GMT
            |"@method": POST
            |"@path": /foo
            |"@query": ?param=Value&Pet=dog
            |"@authority": example.com
            |"content-type": application/json
            |"content-digest": sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX\
            |  +TaPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
            |"content-length": 18
            |"@signature-params": ("date" "@method" "@path" "@query" \
            |  "@authority" "content-type" "content-digest" "content-length")\
            |  ;created=1618884473;keyid="test-key-rsa-pss"""".rfc8792single
       ),
       TestBase(
         "B.2.5 test base",
         DB.`B.2_Request`,
         ReqSigInput(`date`(LS), `@authority`, `content-type`(LS))(
           Created(1618884473L),
           KeyId(sf"test-shared-secret")
         ),
         """"date": Tue, 20 Apr 2021 02:07:55 GMT
            |"@authority": example.com
            |"content-type": application/json
            |"@signature-params": ("date" "@authority" "content-type")\
            |  ;created=1618884473;keyid="test-shared-secret"""".rfc8792single
       ),
       TestBase(
         "B.2.6 test base",
         DB.`B.2_Request`,
         ReqSigInput(
           `date`(LS),
           `@method`,
           `@path`,
           `@authority`,
           `content-type`(LS),
           `content-length`(LS)
         )(
           Created(1618884473L),
           KeyId(sf"test-key-ed25519")
         ),
         """"date": Tue, 20 Apr 2021 02:07:55 GMT
            |"@method": POST
            |"@path": /foo
            |"@authority": example.com
            |"content-type": application/json
            |"content-length": 18
            |"@signature-params": ("date" "@method" "@path" "@authority" \
            |  "content-type" "content-length");created=1618884473\
            |  ;keyid="test-key-ed25519"""".rfc8792single
       ),
       TestBase(
         "B.3 Proxy example",
         DB.`B.3.Proxy_enhanced`,
         ReqSigInput(`@path`, `@query`, `@method`, `@authority`, `client-cert`(LS))(
           Created(1618884473L),
           KeyId(sf"test-key-ecc-p256")
         ),
         """"@path": /foo
            |"@query": ?param=Value&Pet=dog
            |"@method": POST
            |"@authority": service.internal.example
            |"client-cert": :MIIBqDCCAU6gAwIBAgIBBzAKBggqhkjOPQQDAjA6MRswGQYDVQQ\
            |  KDBJMZXQncyBBdXRoZW50aWNhdGUxGzAZBgNVBAMMEkxBIEludGVybWVkaWF0ZSBD\
            |  QTAeFw0yMDAxMTQyMjU1MzNaFw0yMTAxMjMyMjU1MzNaMA0xCzAJBgNVBAMMAkJDM\
            |  FkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8YnXXfaUgmnMtOXU/IncWalRhebrXm\
            |  ckC8vdgJ1p5Be5F/3YC8OthxM4+k1M6aEAEFcGzkJiNy6J84y7uzo9M6NyMHAwCQY\
            |  DVR0TBAIwADAfBgNVHSMEGDAWgBRm3WjLa38lbEYCuiCPct0ZaSED2DAOBgNVHQ8B\
            |  Af8EBAMCBsAwEwYDVR0lBAwwCgYIKwYBBQUHAwIwHQYDVR0RAQH/BBMwEYEPYmRjQ\
            |  GV4YW1wbGUuY29tMAoGCCqGSM49BAMCA0gAMEUCIBHda/r1vaL6G3VliL4/Di6YK0\
            |  Q6bMjeSkC3dFCOOB8TAiEAx/kHSB4urmiZ0NX5r5XarmPk0wmuydBVoU4hBVZ1yhk=:
            |"@signature-params": ("@path" "@query" "@method" "@authority" \
            |  "client-cert");created=1618884473;keyid="test-key-ecc-p256"""".rfc8792single
       )
     ) ::: {
       List(
         DB.`B.4_Req` -> true,
         DB.`B.4_Transform_1` -> true,
         DB.`B.4_Transform_2` -> true,
         DB.`B.4_Transform_3` -> true,
         DB.`B.4_Transform_4` -> false,
         DB.`B.4_Transform_5` -> false
       ).map(req =>
         TestBase(
           "B.4 reordering",
           req._1,
           ReqSigInput(`@method`, `@path`, `@authority`, accept(LS))(
             Created(1618884473L),
             KeyId(sf"test-key-ed25519")
           ),
           """"@method": GET
              |"@path": /demo
              |"@authority": example.org
              |"accept": application/json, */*
              |"@signature-params": ("@method" "@path" "@authority" "accept")\
              |  ;created=1618884473;keyid="test-key-ed25519"""".rfc8792single,
           req._2
         )
       )
     }
   }.zipWithIndex.foreach { (testSig, i) =>
     test(s"test ReqSigInput $i: ${testSig.doc}") {
       val req = interpret.asRequest(testSig.reqStr)
       val x: Either[ParsingExc, SigningString] = req.sigBase(testSig.sigIn)
       if testSig.success then
         assertEquals(
           x.flatMap(s => s.decodeAscii),
           Right(testSig.baseResult),
           clue = testSig
         )
       else
         assertNotEquals(
           x.flatMap(s => s.decodeAscii),
           Right[ParsingExc, String](testSig.baseResult).toTry.toEither,
           clue = testSig
         )
     }
   }

//  case class TestSig(
//       req: RequestStr,
//       signature: String
//  )