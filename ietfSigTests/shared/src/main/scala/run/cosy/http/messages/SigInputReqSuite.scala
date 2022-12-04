package run.cosy.http.messages

import bobcats.Verifier.SigningString
import munit.CatsEffectSuite
import run.cosy.http.Http.Request
import run.cosy.http.auth.{MessageSignature, ParsingExc}
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http.headers.{Rfc8941, SigInput}
import run.cosy.http.utils.StringUtils.*
import run.cosy.http.{Http, HttpOps}

import scala.util.{Failure, Success, Try}

open class SigInputReqSuite[F[_], H <: Http](
    rdb: ReqComponentDB[F, H] // we don't pass it implicitly, as we need to add some dummy headers
)(using
    hOps: HttpOps[H],
    msgDB: TestHttpMsgInterpreter[F, H]
) extends CatsEffectSuite:

   val DB                                = HttpMessageDB
   val msgSigfns: MessageSignature[F, H] = new MessageSignature[F, H]
   import msgSigfns.*

   given ReqComponentDB[F, H] = rdb.addIds(
     HeaderId("x-ows-header").toTry.get,
     HeaderId("x-obs-fold-header").toTry.get,
     HeaderId.dict("example-dict").toTry.get,
     HeaderId("example-header").toTry.get,
     HeaderId("x-empty-header").toTry.get
   )

   List(DB.`2.4_Req_Ex`, DB.`2.4_Req_v2`).zipWithIndex.foreach { (msg, i) =>
     test(s"Test SigInput §2.4 round $i") {
       val req: Http.Request[F, H] = msgDB.asRequest(msg)
       val sigInput25: Option[SigInput] = SigInput(
         """("@method" "@authority" "@path" \
               |"content-digest" "content-length" "content-type")\
               |;created=1618884473;keyid="test-key-rsa-pss"""".rfc8792single
       )

       val x: Either[ParsingExc, SigningString] = req.signatureBase(sigInput25.get)
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

       val sigData = req.getSignature(Rfc8941.Token("sig1"))
       assertEquals(
         sigData.map(_._1),
         sigInput25
       )

       val signature = """sig1=:HIbjHC5rS0BYaa9v4QfD4193TORw7u9edguPh0AW3dMq9WImrl\
                            |FrCGUDih47vAxi4L2YRZ3XMJc1uOKk/J0ZmZ+wcta4nKIgBkKq0rM9hs3CQyxXGxH\
                            |LMCy8uqK488o+9jrptQ+xFPHK7a9sRL1IXNaagCNN3ZxJsYapFj+JXbmaI5rtAdSf\
                            |SvzPuBCh+ARHBmWuNo1UzVVdHXrl8ePL4cccqlazIJdC4QEjrF+Sn4IxBQzTZsL9y\
                            |9TP5FsZYzHvDqbInkTNigBcE9cKOYNFCn4D/WM7F6TNuZO9EgtzepLWcjTymlHzK7\
                            |aXq6Am6sfOrpIC49yXjj3ae6HRalVc/g==:""".rfc8792single

       assertEquals(
         sigData.map(_._2.canon),
         Some(signature.substring("sig1=".length).nn)
       )
     }
   }

   case class TestSig(doc: String, reqStr: DB.RequestStr, sigInputStr: String, baseResult: String)

   List(
     TestSig("empty SigInput", DB.`§2.1_HeaderField`, "()", """"""),
     TestSig(
       "SigInput from in §2.1 with old request",
       DB.`§2.1_HeaderField`,
       """("host" "date" "x-ows-header" "x-obs-fold-header" "cache-control" "example-dict")""",
       """"host": www.example.com
            |"date": Sat, 07 Jun 2014 20:51:35 GMT
            |"x-ows-header": Leading and trailing whitespace.
            |"x-obs-fold-header": Obsolete line folding.
            |"cache-control": max-age=60, must-revalidate
            |"example-dict": a=1,   b=2;x=1;y=2,   c=(a   b   c), d""".rfc8792single
     ),
     TestSig(
       "SigInput from §2.1 with new request",
       DB.`§2.1_HeaderField_2`,
       """("host" "date" "x-ows-header" "x-obs-fold-header" "cache-control" "example-dict")""",
       """"host": www.example.com
            |"date": Tue, 20 Apr 2021 02:07:56 GMT
            |"x-ows-header": Leading and trailing whitespace.
            |"x-obs-fold-header": Obsolete line folding.
            |"cache-control": must-revalidate, max-age=60
            |"example-dict": a=1,    b=2;x=1;y=2,   c=(a   b   c), d""".rfc8792single
     ),
     TestSig(
       "SigInput with ;key and ;sf and ;bs selectors",
       DB.`§2.1_HeaderField_2`,
       """("host" "date"  "x-empty-header" "x-ows-header";bs "cache-control";key="max-age" "example-dict";sf)""",
       """"host": www.example.com
            |"date": Tue, 20 Apr 2021 02:07:56 GMT
            |"x-empty-header": \
            |
            |"x-ows-header";bs: :TGVhZGluZyBhbmQgdHJhaWxpbmcgd2hpdGVzcGFjZS4=:
            |"cache-control";key="max-age": 60
            |"example-dict";sf: a=1, b=2;x=1;y=2, c=(a b c), d""".rfc8792single
     ),
     TestSig(
       "SigInput with example-dict selectors",
       DB.`§2.1_HeaderField_2`,
       """("example-dict";key="a" "example-dict";key="d" "example-dict";key="b" "example-dict";key="c")""",
       """"example-dict";key="a": 1
            |"example-dict";key="d": ?1
            |"example-dict";key="b": 2;x=1;y=2
            |"example-dict";key="c": (a b c)""".rfc8792single
     ),
     TestSig(
       "SigInput from §2.1.3, lots of commas",
       DB.`§2.1_HeaderField_2`,
       """( "example-header" "example-header";bs   )""",
       """"example-header": value, with, lots, of, commas
            |"example-header";bs: :dmFsdWUsIHdpdGgsIGxvdHM=:, :b2YsIGNvbW1hcw==:""".rfc8792single
     ),
     TestSig(
       "SigInput from §2.3, covering @ and header selectors",
       DB.`§2.1_HeaderField_2`,
       """("@target-uri" "@authority" "date" "cache-control")\
              ;keyid="test-key-rsa-pss";alg="rsa-pss-sha512";\
              created=1618884475;expires=1618884775""".rfc8792single,
       """"@target-uri": https://www.example.com/xyz
            |"@authority": www.example.com
            |"date": Tue, 20 Apr 2021 02:07:56 GMT
            |"cache-control": must-revalidate, max-age=60""".rfc8792single
     )
   ).zipWithIndex.foreach { (testSig, i) =>
     test(s"test req.signingStr $i: ${testSig.doc}") {
       val req                                  = msgDB.asRequest(testSig.reqStr)
       val sigIn: Option[SigInput]              = SigInput(testSig.sigInputStr)
       val x: Either[ParsingExc, SigningString] = req.signatureBase(sigIn.get)
       assertEquals(
         x.flatMap(s => s.decodeAscii),
         Right(
           ((if testSig.baseResult == "" then List() else List(testSig.baseResult)) ::: List(
             """"@signature-params": """ + sigIn.get.canon
           )).mkString("\n")
         ),
         clue = testSig
       )
     }
   }