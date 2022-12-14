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




trait VerifySignatureTests[H <: Http](
    interpret: TestHttpMsgInterpreter[H]
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
       sigs: List[SignatureTest],
       verify: SigVerifier[F, KeyIdentified, H]
   )(using ME: MonadError[F, Throwable]): Unit =
      import scala.util.control.NonLocalReturns.*
      sigs.zipWithIndex.foreach { (testSig, i) =>
        if testSig.unsupported.contains(thisPlatform) then
           test(s"test sig $i ${testSig.msg} cannot be run on $thisPlatform".ignore) {}
        else
           val req: Request[H] = interpret.asRequest(testSig.reqStr)

           test(s"test sig $i before time on ${testSig.msg} fails") {

             verify(
               req,
               FiniteDuration(testSig.created - 10, duration.SECONDS),
               HttpSig(testSig.sigName)
             ).redeemWith(
               t => ME.catchNonFatal(assert(true, t)),
               k => ME.catchNonFatal(assert(false, s"should not have received answer >$k<"))
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
