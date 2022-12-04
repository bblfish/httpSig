package run.cosy.http.messages

import bobcats.Verifier.SigningString
import munit.CatsEffectSuite
import run.cosy.http.{Http, HttpOps}
import run.cosy.http.auth.{MessageSignature, ParsingExc}
import run.cosy.http.headers.ReqSigInput
import run.cosy.http.headers.Rfc8941.SfInt
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.headers.SigIn.*
import run.cosy.http.messages.TestHttpMsgInterpreter

import scala.collection.immutable.ListSet

/** This test suite looks at statically built SigInput requests using functions. This is useful for
  * clients writing signatures.
  */
open class StaticSigInputReqSuite[F[_], H <: Http](
 hsel: ReqSelectors[F, H], // we don't pass it implicitly, because we need to add some headers.
)(using
  hops: HttpOps[H],
  interpret: TestHttpMsgInterpreter[F, H]
) extends CatsEffectSuite:
   val hds = HeaderIds
   
   import hsel.*
   import hsel.RequestHd.*
   import run.cosy.http.utils.StringUtils.*
   import HeaderSelectors.*

   val msgSigfns: MessageSignature[F, H] = new MessageSignature[F, H]
   import msgSigfns.*
   
   val DB = HttpMessageDB
   
   val `x-ows-header` = hsel.onRequest(HeaderId("x-ows-header").toTry.get)
   val `x-obs-fold-header` = hsel.onRequest(HeaderId("x-obs-fold-header").toTry.get)
   val `example-dict` = hsel.onRequest(HeaderId.dict("example-dict").toTry.get)
   val `example-header` = hsel.onRequest(HeaderId("example-header").toTry.get)
   val `x-empty-header` = hsel.onRequest(HeaderId("x-empty-header").toTry.get)
   // one should prefer the `@...` versions over these two
   val date = hsel.onRequest(hds.Response.`date`)
   val host = hsel.onRequest(hds.retrofit.`host`)
   
   List(DB.`2.4_Req_Ex`, DB.`2.4_Req_v2`).zipWithIndex.foreach { (msg, i) =>
      test("apply ReqSigInput selector on ex from 2.4 v." + i) {
         val req = interpret.asRequest(msg)
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
            ListSet(Created(1618884473L).toTry.get, KeyId(sf"test-key-rsa-pss"))
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
   
   case class TestSig(
    doc: String,
    reqStr: DB.RequestStr,
    sigIn: ReqSigInput[F, H],
    baseResult: String
   )
   
   def signatureParams(str: String) = """"@signature-params": """ + str
   
   List(
      TestSig("empty SigInput", DB.`§2.1_HeaderField`, ReqSigInput()(), signatureParams("()")),
      TestSig(
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
      TestSig(
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
      TestSig(
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
      TestSig(
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
      TestSig(
         "SigInput from §2.1.3, lots of commas",
         DB.`§2.1_HeaderField_2`,
         ReqSigInput(
            `example-header`(LS), `example-header`(BS)
         )(),
         """"example-header": value, with, lots, of, commas
            |"example-header";bs: :dmFsdWUsIHdpdGgsIGxvdHM=:, :b2YsIGNvbW1hcw==:
            |""".rfc8792single + signatureParams("""("example-header" "example-header";bs)""")
      ),
      TestSig(
         "SigInput from §2.3, covering @ and header selectors",
         DB.`§2.1_HeaderField_2`,
         ReqSigInput(`@target-uri`, `@authority`, `date`(LS), `cache-control`(LS))(KeyId(sf"test-key-rsa-pss"), Alg(SigAlg.`rsa-pss-sha512`),
            Created(SfInt(1618884475)),Expires(SfInt(1618884775L))
         ),
         """"@target-uri": https://www.example.com/xyz
            |"@authority": www.example.com
            |"date": Tue, 20 Apr 2021 02:07:56 GMT
            |"cache-control": must-revalidate, max-age=60
            |""".rfc8792single + signatureParams(
              """("@target-uri" "@authority" "date" "cache-control")\
                |   ;keyid="test-key-rsa-pss";alg="rsa-pss-sha512";\
                |   created=1618884475;expires=1618884775""".rfc8792single )
      )
   ).zipWithIndex.foreach { (testSig, i) =>
      test(s"test ReqSigInput $i: ${testSig.doc}") {
         val req = interpret.asRequest(testSig.reqStr)
         val x: Either[ParsingExc, SigningString] = req.sigBase(testSig.sigIn)
         assertEquals(
            x.flatMap(s => s.decodeAscii),
            Right(testSig.baseResult),
            clue = testSig
         )
      }
   }
