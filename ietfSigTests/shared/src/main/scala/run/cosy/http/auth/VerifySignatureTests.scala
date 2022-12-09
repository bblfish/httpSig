package run.cosy.http.auth

import bobcats.Verifier.SigningString
import bobcats.{Signer, Verifier}
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
import scala.util.Try

enum RunPlatform:
   case BrowserJS, NodeJS, JVM

import run.cosy.http.auth.RunPlatform.*

case class TestSignature(
    msg: String,
    reqStr: RequestStr,
    sigName: String,
    created: Long,
    keyId: String,
    shouldSucceed: Boolean = true,
    unsupported: List[RunPlatform] = Nil,
    expires: Long = Long.MinValue
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
       // this has an expiration date to test too.
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
         "test-key-ed25519",
         unsupported = List(BrowserJS)
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
           req._2,
           unsupported = List(BrowserJS)
         )
       )
     }
end TestSignatures

trait VerifySignatureTests[FH[_], H <: Http](
    interpret: TestHttpMsgInterpreter[FH, H]
) extends CatsEffectSuite:
   val thisPlatform: RunPlatform
   import cats.syntax.functor.{*, given}
   import cats.syntax.monadError.{*, given}

   /*
   * note: this function does not require IO at all, so that we could test it without needing CatsEffectSuite.
   * the SigVerifier does take a functor F, but that could be cats.Id on java with a lookup, or
   * it could be a Future to test Akka. For JS it needs to a async.
   * */
   def testSignatures[F[_]](
       sigs: List[TestSignature],
       verify: MessageSignature[FH, H]#SigVerifier[F, KeyIdentified]
   )(using ME: MonadError[F, Throwable]): Unit =
      import scala.util.control.NonLocalReturns.*
      sigs.zipWithIndex.foreach { (testSig, i) =>
        if testSig.unsupported.contains(thisPlatform) then
           test(s"test sig $i ${testSig.msg} cannot be run on $thisPlatform".ignore) {}
        else
           val req: Request[FH, H] = interpret.asRequest(testSig.reqStr)

           test(s"test sig $i before time on ${testSig.msg} fails") {

             verify(
               req,
               FiniteDuration(testSig.created - 10, duration.SECONDS),
               HttpSig(testSig.sigName)
             ).redeemWith(
               t => ME.catchNonFatal(assert(true, t))),
               k => ME.catchNonFatal(assert(false, s"should not have received answer >$k<")))
             )
           }
           test(s"test sig $i at time on ${testSig.msg} succeeds") {
             val done: F[KeyIdentified] =
               verify(
                 req,
                 FiniteDuration(testSig.created, duration.SECONDS),
                 HttpSig(testSig.sigName)
               )
             if testSig.shouldSucceed then
                done.map(id => assertEquals(id, PureKeyId(testSig.keyId), testSig))
             else
                done.redeemWith(
                  t => ME.catchNonFatal(assert(true, t)),
                  k => ME.catchNonFatal(assert(false, s"should not have received answer >$k<"))
                )
           }
           if testSig.expires != Long.MinValue then
              test(s"test sig $i at time on ${testSig.msg} succeeds") {
                val done: F[KeyIdentified] =
                  verify(
                    req,
                    FiniteDuration(testSig.expires - 1, duration.SECONDS),
                    HttpSig(testSig.sigName)
                  )
                if testSig.shouldSucceed then
                   done.map(k => assertEquals(k, PureKeyId(testSig.keyId), testSig))
                else
                   done.redeemWith(
                     t => ME.catchNonFatal(assert(true, t)),
                     k => ME.catchNonFatal(assert(false, s"should not have received answer >$k<"))
                   )
              }
              test("sig after time...") {
                verify(
                  req,
                  FiniteDuration(testSig.expires + 1, duration.SECONDS),
                  HttpSig(testSig.sigName)
                ).redeemWith(
                  t => ME.catchNonFatal(assert(true, t)),
                  k => ME.catchNonFatal(assert(false, s"should not have received answer >$k<"))
                )
              }

      }

end VerifySignatureTests
