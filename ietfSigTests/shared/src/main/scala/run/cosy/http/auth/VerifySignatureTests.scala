package run.cosy.http.auth

import bobcats.{Signer, Verifier}
import bobcats.Verifier.SigningString
import cats.effect.syntax.all.*
import cats.effect.testkit.TestControl
import cats.effect.{IO, Outcome, Sync, SyncIO}
import cats.{Id, MonadError}
import munit.CatsEffectSuite
import run.cosy.http.Http.Request
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.headers.SigIn.{Created, KeyId}
import run.cosy.http.headers.{HttpSig, ReqSigInput}
import run.cosy.http.messages.*
import run.cosy.http.messages.HttpMessageDB.RequestStr
import run.cosy.http.{Http, HttpOps, auth}

import scala.collection.immutable.ListSet
import scala.concurrent.duration
import scala.concurrent.duration.{FiniteDuration, TimeUnit}

case class TestSignature(
    msg: String,
    reqStr: RequestStr,
    sigName: String,
    time: Long,
    keyId: String,
    succeed: Boolean = true
)

object TestSignatures:

   val DB = HttpMessageDB

   val specRequestSigs: List[TestSignature] =
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
end TestSignatures


trait VerifySignatureTests[FH[_], H <: Http](
    // we don't pass it implicitly, because we need to add some headers
    hsel: ReqSelectors[FH, H]
)(using
    hops: HttpOps[H],
    interpret: TestHttpMsgInterpreter[FH, H],
    ME: cats.effect.Sync[IO],
    V: bobcats.Verifier[IO]
) extends CatsEffectSuite:

   val hds = HeaderIds

   import HeaderSelectors.*
   import run.cosy.http.utils.StringUtils.*

   val msgSigfns: MessageSignature[FH, H] = new MessageSignature[FH, H]
   import msgSigfns.*

   val signaturesDB = new SigSuiteHelpers[IO]
   // todo: it may be useful if hsel had a list of all the headers it used
   // so that there was not a danger of being out of sync with the verifier in ReqComponentDB...
   // we did not duplicate the headerIds
   // needed to verify signatures
   given selectorDB: ReqComponentDB[FH, H] = ReqComponentDB(hsel, HeaderIds.all) // ++ testIds.all)

   def doAt[A](start: FiniteDuration, act: IO[A]): IO[Option[Outcome[Id, Throwable, A]]] =
     TestControl.execute(act.to[IO]).flatMap { ctrl =>
       for
          _ <- ctrl.results.assertEquals(None)
          _ <- ctrl.advance(start)
          _ <- ctrl.tick
          x <- ctrl.results
       yield x
     }

   def testSignatures(sigs: List[TestSignature]): Unit =

      sigs.zipWithIndex.foreach { (testSig, i) =>
        test(s"test sig $i: ${testSig.msg} for ${testSig.keyId}") {

          val req: Request[FH, H] = interpret.asRequest(testSig.reqStr)
          // now test the signature
          // this is where ME and Clock are needed.
          val auth: HttpSig => IO[KeyIdentified] =
            req.signatureAuthN[IO, KeyIdentified](signaturesDB.keyidFetcher)
          val res: IO[KeyIdentified] = auth(HttpSig(testSig.sigName))
          
          val done = doAt(FiniteDuration(testSig.time - 10, duration.SECONDS), res).map {
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
                  case _ => throw Exception(s"the verification of $req should not have errored. Instead we got "+v)
             )
        }
      }

end VerifySignatureTests
