package run.cosy.http.messages

import bobcats.Verifier.SigningString
import munit.CatsEffectSuite
import run.cosy.http.Http
import run.cosy.http.auth.{MessageSignature, ParsingExc}
import run.cosy.http.headers.ReqSigInput
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.headers.SigIn.*
import run.cosy.http.messages.Selectors.*
import run.cosy.http.messages.TestHttpMsgInterpreter

import scala.collection.immutable.ListSet

/** This test suite looks at statically built SigInput requests using functions. This is useful for
  * clients writing signatures.
  */
open class StaticSigInputReqSuite[F[_], H <: Http](
    msgSig: MessageSignature[F, H],         // needed for methods on req
    hsel: Selectors[F, H],                  // todo: pass only request selectors
    interpret: TestHttpMsgInterpreter[F, H] // to abstract from http implementation
) extends CatsEffectSuite:
   val hds = HeaderIds
   import hsel.*
   import hsel.RequestHd.*
   import msgSig.*
   import run.cosy.http.utils.StringUtils.*
   val DB = HttpMessageDB

   val `x-ows-header`      = hsel.onRequest(HeaderId("x-ows-header").toTry.get)
   val `x-obs-fold-header` = hsel.onRequest(HeaderId("x-obs-fold-header").toTry.get)
   val `example-dict`      = hsel.onRequest(HeaderId.dict("example-dict").toTry.get)
   val `example-header`    = hsel.onRequest(HeaderId("example-header").toTry.get)
   val `x-empty-header`    = hsel.onRequest(HeaderId("x-empty-header").toTry.get)
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
           `content-digest`(Raw),
           `content-length`(Raw),
           `content-type`(Raw)
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
         host(Raw),
         date(Raw),
         `x-ows-header`(Raw),
         `x-obs-fold-header`(Raw),
         `cache-control`(Raw),
         `example-dict`(Raw)
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
         host(Raw),
         date(Raw),
         `x-ows-header`(Raw),
         `x-obs-fold-header`(Raw),
         `cache-control`(Raw),
         `example-dict`(Raw)
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
         host(Raw),
         date(Raw),
         `x-empty-header`(Raw),
         `x-ows-header`(Bin),
         `cache-control`(DictSel(sf"max-age")),
         `example-dict`(Strict)
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
//       """("example-dict";key="a" "example-dict";key="d" "example-dict";key="b" "example-dict";key="c")""",
       ReqSigInput(
         `example-dict`(DictSel(sf"a")),
         `example-dict`(DictSel(sf"d")),
         `example-dict`(DictSel(sf"b")),
         `example-dict`(DictSel(sf"c"))
       )(),
       """"example-dict";key="a": 1
          |"example-dict";key="d": ?1
          |"example-dict";key="b": 2;x=1;y=2
          |"example-dict";key="c": (a b c)
          |""".rfc8792single + signatureParams(
         """("example-dict";key="a" "example-dict";key="d" "example-dict";key="b" "example-dict";key="c")"""
       )
     )
   ).zipWithIndex.foreach { (testSig, i) =>
     test(s"test ReqSigInput $i: ${testSig.doc}") {
       val req                                  = interpret.asRequest(testSig.reqStr)
       val x: Either[ParsingExc, SigningString] = req.sigBase(testSig.sigIn)
       assertEquals(
         x.flatMap(s => s.decodeAscii),
         Right(testSig.baseResult),
         clue = testSig
       )
     }
   }
