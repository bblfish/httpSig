package run.cosy.http.auth

import bobcats.Verifier.SigningString
import cats.effect.syntax.all.*
import cats.effect.testkit.TestControl
import cats.effect.{IO, Outcome, SyncIO}
import cats.{Id, MonadError}
import munit.CatsEffectSuite
import run.cosy.http.Http.Request
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.headers.SigIn.{Created, KeyId}
import run.cosy.http.headers.{HttpSig, ReqSigInput}
import run.cosy.http.messages.*
import run.cosy.http.messages.HttpMessageDB.RequestStr
import run.cosy.http.{Http, HttpOps}

import scala.collection.immutable.ListSet
import scala.concurrent.duration
import scala.concurrent.duration.{FiniteDuration, TimeUnit}

//todo: we assume IO to start.
//  later look at how bobcats abstracts between AsyncIO needed for Java and IO for JS
trait VerifySignatureTests[FH[_], H <: Http](
    // we don't pass it implicitly, because we need to add some headers
    hsel: ReqSelectors[FH, H]
)(using
    hops: HttpOps[H],
    interpret: TestHttpMsgInterpreter[FH, H],
    // needed for testing signatures
    ME: cats.effect.Sync[SyncIO],
    V: bobcats.Verifier[SyncIO]
) extends CatsEffectSuite:

   val hds = HeaderIds

   val signaturesDB = new SigSuiteHelpers[SyncIO]

   import HeaderSelectors.*
   import hsel.*
   import hsel.RequestHd.*
   import run.cosy.http.utils.StringUtils.*

   val msgSigfns: MessageSignature[FH, H] = new MessageSignature[FH, H]
   import msgSigfns.*

   val DB = HttpMessageDB

   object testIds:
      val `x-ows-header`      = HeaderId("x-ows-header").toTry.get
      val `x-obs-fold-header` = HeaderId("x-obs-fold-header").toTry.get
      val `example-dict`      = HeaderId.dict("example-dict").toTry.get
      val `example-header`    = HeaderId("example-header").toTry.get
      val `x-empty-header`    = HeaderId("x-empty-header").toTry.get
      val all =
        Seq(`x-ows-header`, `x-obs-fold-header`, `example-dict`, `example-header`, `x-empty-header`)

   val `x-ows-header`      = hsel.onRequest(testIds.`x-ows-header`)
   val `x-obs-fold-header` = hsel.onRequest(testIds.`x-obs-fold-header`)
   val `example-dict`      = hsel.onRequest(testIds.`example-dict`)
   val `example-header`    = hsel.onRequest(testIds.`example-header`)
   val `x-empty-header`    = hsel.onRequest(testIds.`x-empty-header`)
   // one should prefer the `@...` versions over these two
   val date = hsel.onRequest(hds.Response.`date`)
   val host = hsel.onRequest(hds.retrofit.`host`)

   // todo: it may be useful if hsel had a list of all the headers it used
   // so that there was not a danger of being out of sync with the verifier in ReqComponentDB...
   // we did not duplicate the headerIds
   // needed to verify signatures
   given selectorDB: ReqComponentDB[FH, H] = ReqComponentDB(hsel, HeaderIds.all ++ testIds.all)

   def doAt[A](start: FiniteDuration, act: IO[A]): IO[Option[Outcome[Id, Throwable, A]]] =
     TestControl.execute(act.to[IO]).flatMap { ctrl =>
       for
          _ <- ctrl.results.assertEquals(None)
          _ <- ctrl.advance(start)
          _ <- ctrl.tick
          x <- ctrl.results
       yield x
     }

   List(DB.`2.4_Req_Ex`, DB.`2.4_Req_v2`).zipWithIndex.foreach { (msg, i) =>
      val req: Request[FH, H] = interpret.asRequest(msg)
      test("apply ReqSigInput selector on ex from 2.4 v." + i) {
        val rsel: List[RequestSelector[FH, H]] =
          List(
            `@method`,
            `@authority`,
            `@path`,
            `content-digest`(LS),
            `content-length`(LS),
            `content-type`(LS)
          )
        val rsi: ReqSigInput[FH, H] = new ReqSigInput(
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

      test("we verify the signature on on ex from 2.4 v." + i) {
        // now test the signature
        // this is where ME and Clock are needed.
        val auth: HttpSig => SyncIO[KeyIdentified] = req.signatureAuthN[SyncIO, KeyIdentified](signaturesDB.keyidFetcher)
        val res: SyncIO[KeyIdentified]             = auth(HttpSig("sig1"))

        doAt(FiniteDuration(1618884472, duration.SECONDS), res.to[IO]).map {
          case Some(Outcome.Errored(e: InvalidSigException)) => true
          case _                                             => false
        }.assertEquals(true) >> doAt(
          FiniteDuration(1618884473, duration.SECONDS),
          res.to[IO]
        )
          .assertEquals(Option(Outcome.succeeded(Id(PureKeyId("test-key-rsa-pss")))))

      }
   }

   case class TestSignature(
       msg: String,
       reqStr: RequestStr,
       sigName: String,
       time: Long,
       keyId: String,
       succeed: Boolean = true
   )

   {
     List(
       TestSignature(
         "sig using attributes, from §2.5",
         DB.`2.4_Req_Ex`,
         "sig1",
         1618884473L,
         "test-key-rsa-pss"
       ),
       TestSignature(
         "sig using attributes, from §2.5",
         DB.`2.4_Req_v2`,
         "sig1",
         1618884473L,
         "test-key-rsa-pss"
       ),
       TestSignature(
         "sig on POST, example from §3.2",
         DB.`3.2_POST_Signed`,
         "sig1",
         1618884473L,
         "test-key-rsa-pss"
       ),
       TestSignature(
         "Sig on post, example from §4.3",
         DB.`4.3_POST_Sig1`,
         "sig1",
         1618884475L,
         "test-key-rsa-pss"
       ),
// see https://github.com/httpwg/http-extensions/issues/2347
//       TestSignature(
//         "Sig on request §4.3 enhanced by proxy. Test original",
//         DB.`4.3_POST_With_Proxy`,
//         "sig1",
//         1618884475L,
//         "test-key-rsa-pss"
//       ),
       TestSignature(
         "Sig on request §4.3 enhanced by proxy with later valid date. Test proxy's sig.",
         DB.`4.3_POST_With_Proxy`,
         "proxy_sig",
         1618884480L,
         "test-key-rsa"
       ),
       TestSignature(
         "test sign B.2.1 req, with nonce, signed with rsa-pss-sha512",
         DB.`B.2_Req_sig_b21`,
         "sig-b21",
         1618884473L,
         "test-key-rsa-pss"
       ),
       TestSignature(
         "test sign B.2.2 req, with tag, signed with rsa-pss-sha512",
         DB.`B.2_Req_sig_b22`,
         "sig-b22",
         1618884473L,
         "test-key-rsa-pss"
       ),
       TestSignature(
         "test sign B.2.3 req, full signed with rsa-pss-sha512",
         DB.`B.2_Req_sig_b23`,
         "sig-b23",
         1618884473L,
         "test-key-rsa-pss"
       ),
       TestSignature(
         "B.2.5 signing with hmac-sha256",
         DB.`B.2_Req_sig_b25`,
         "sig-b25",
         1618884473L,
         "test-shared-secret"
       ),
       TestSignature(
         "B.2.5 signing with ed25519",
         DB.`B.2_Req_sig_b26`,
         "sig-b26",
         1618884473L,
         "test-key-ed25519"
       ),
       TestSignature(
         "B.3 Proxy example",
         DB.`B.3.Proxy_Signed`,
         "ttrp",
         1618884473L,
         "test-key-ecc-p256"
       )
     ) ::: {
       List(
         DB.`B.4_Req`         -> true,
         DB.`B.4_Transform_1` -> true,
         DB.`B.4_Transform_2` -> true,
         DB.`B.4_Transform_3` -> true,
         DB.`B.4_Transform_4` -> false,
         DB.`B.4_Transform_5` -> false
       ).map(req =>
         TestSignature(
           "B.4 reordering",
           req._1,
           "transform",
           1618884473L,
           "test-key-ed25519",
           req._2
         )
       )
     }
   }.zipWithIndex.foreach { (testSig, i) =>
     test(s"test sig $i: ${testSig.msg} for ${testSig.keyId}") {

       val req: Request[FH, H] = interpret.asRequest(testSig.reqStr)
       // now test the signature
       // this is where ME and Clock are needed.
       val auth: HttpSig => SyncIO[KeyIdentified] = req.signatureAuthN[SyncIO, KeyIdentified](signaturesDB.keyidFetcher)
       val res: SyncIO[KeyIdentified]             = auth(HttpSig(testSig.sigName))

       val done = doAt(FiniteDuration(testSig.time - 10, duration.SECONDS), res.to[IO]).map {
         case Some(Outcome.Errored(e: InvalidSigException)) => true
         case _                                             => false
       }.assertEquals(true) >> doAt(
         FiniteDuration(testSig.time + 1, duration.SECONDS),
         res.to[IO]
       )
       if testSig.succeed then
          done.assertEquals(Option(Outcome.succeeded(Id(PureKeyId(testSig.keyId)))), testSig)
       else
          done.map(v =>
            v match
               case Some(Outcome.Errored(x)) => true
               case _ => throw Exception(s"the verification of $req should not have succeeded.")
          )
     }

   }
