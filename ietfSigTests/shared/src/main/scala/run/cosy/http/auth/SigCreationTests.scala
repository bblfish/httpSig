package run.cosy.http.auth

import bobcats.{Hmac, Signer, Verifier}
import cats.MonadError
import cats.effect.IO
import munit.CatsEffectSuite
import run.cosy.http.Http.Request
import run.cosy.http.auth.MessageSignature.SigningF
import run.cosy.http.auth.SignatureTest
import run.cosy.http.headers.Rfc8941.SfString
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.headers.SigIn.{Created, Expires, KeyId}
import run.cosy.http.headers.{HttpSig, ReqSigInput, Rfc8941, SigInputs}
import run.cosy.http.messages.HeaderSelectors.LS
import run.cosy.http.messages.HttpMessageDB.RequestStr
import run.cosy.http.messages.*
import run.cosy.http.{Http, HttpOps}
import scodec.bits.ByteVector

import java.net.http.HttpRequest
import java.nio.charset.CharacterCodingException
import scala.concurrent.duration.{FiniteDuration, SECONDS}

case class SigningTest[H <: Http](
    msg: String,
    reqStr: RequestStr,
    sigName: String,
    sigIn: ReqSigInput[H],
    shouldSucceed: Boolean = true,
    unsupported: List[RunPlatform] = Nil,
    signature: String = "" // if empty then we don't know the signature and need to test
):
   def keyId: String = sigIn.params.collectFirst { case KeyId(id) => id.asciiStr }.getOrElse("")
   def now: Long     = sigIn.params.collectFirst { case Created(value) => value.long }.getOrElse(0)

/** this is the full test suite. To test signature creation for Requests, we need to verify that the
  * signature matches the one specified, but when that is not possible (because the algorithm
  * generates new signatures every time) we need to test it ourselves.
  */
trait SigCreationTest[H <: Http](
    interpret: TestHttpMsgInterpreter[H],
    hsel: ReqSelectors[H]
) extends CatsEffectSuite:

//   def removeSig(req: Request[FH,H], sigName: String): (Option[String], Request[FH, H]) =
//     val sigIns: Seq[SigInputs] = req.headers.collect{ case `Signature-Input`(inputs) => inputs }
//     ???

   val thisPlatform: RunPlatform
   import cats.syntax.functor.{*, given}
   import cats.syntax.monadError.{*, given}

   val DB = HttpMessageDB
   import hsel.*
   import hsel.RequestHd.*
   import run.cosy.http.headers.SigIn.*
   import run.cosy.http.utils.StringUtils.*
   val keyIdRsaPss  = KeyId(sf"test-key-rsa-pss")
   val created1     = Created(1618884473L)
   val keyIdRSA     = KeyId(sf"test-key-rsa")
   val keyIdEccP256 = KeyId(sf"test-key-ecc-p256")
   val keyIdEd25519 = KeyId(sf"test-key-ed25519")

   val keyAlgos = Map(
     keyIdRsaPss  -> Alg(SigAlg.`rsa-pss-sha512`),
     keyIdRSA     -> Alg(SigAlg.`rsa-v1_5-sha256`),
     keyIdEd25519 -> Alg(SigAlg.`ed25519`)
//     keyIdEccP256 -> Alg(SigAlg.`)
   )

   val date = hsel.onRequest(HeaderIds.Response.`date`)
   val host = hsel.onRequest(HeaderIds.retrofit.`host`)

   val signingTests = List(
     keyAlgos.toList.map { (kid, algo) =>
       SigningTest(
         s"§2.1 ex signed with ${kid.value.asciiStr} using ${algo.value}",
         DB.`§2.1_HeaderField_2`,
         "sig1",
         ReqSigInput(`@target-uri`, `@authority`, `date`(LS), `cache-control`(LS))(
           kid,
           algo,
           Created(1618884475L),
           Expires(1618884775L)
         )
       )
     },
     keyAlgos.toList.map { (kid, algo) =>
       SigningTest(
         s"§2.4 req ${kid.value.asciiStr} using ${algo.value}",
         DB.`2.4_Req_Ex_pre`,
         "sig1",
         ReqSigInput(
           `@method`,
           `@authority`,
           `@path`,
           `content-digest`(LS),
           `content-length`(LS),
           `content-type`(LS)
         )(created1, kid)
       )
     },
     keyAlgos.toList.map { (kid, algo) =>
       SigningTest(
         s"§B.2 req ${kid.value.asciiStr} using ${algo.value}",
         DB.`B.2_Request`,
         "sig1",
         ReqSigInput(`@authority`, `content-digest`(LS), `@query-param`(SfString("Pet")))(
           created1,
           kid,
           Tag(SfString("header-example"))
         )
       )
     },
     keyAlgos.toList.map { (kid, algo) =>
       SigningTest(
         s"§B.2.3 full coverage ${kid.value.asciiStr} using ${algo.value}",
         DB.`B.2_Request`,
         "sig1",
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
           created1,
           kid
         )
       )
     }
   ).flatten

   def signaturesDB[F[_]](
       using
       ME: MonadError[F, Throwable],
       Sig: Signer[F],
       V: Verifier[F],
       Hmac: Hmac[F]
   ): SigSuiteHelpers[F] = new SigSuiteHelpers[F]

   def testSignatures[F[_]](
       sigs: List[SigningTest[H]],
       verify: SigVerifier[F, KeyIdentified, H]
   )(using
       ME: MonadError[F, Throwable],
       Sig: Signer[F],
       V: Verifier[F],
       hOps: HttpOps[H],
       Hmac: Hmac[F]
   ): Unit =
      import cats.syntax.all.{*, given}
      import hOps.{*, given}

      val fsigs =
        if thisPlatform == RunPlatform.BrowserJS then
           sigs.filterNot(_.keyId == "test-key-ed25519")
        else sigs

      import scala.util.control.NonLocalReturns.*
      fsigs.zipWithIndex.foreach { (testSig, i) =>
        if testSig.unsupported.contains(thisPlatform) then
           test(s"test sig $i ${testSig.msg} cannot be run on $thisPlatform".ignore) {}
        else
           val req: Request[H]        = interpret.asRequest(testSig.reqStr)
           val sigFio: F[SigningF[F]] = signaturesDB.signerFor(testSig.keyId)
           val newReqIO: F[Request[H]] = sigFio.flatMap { sigFn =>
             MessageSignature.withSigInput[F, H](
               req,
               Rfc8941.Token(testSig.sigName),
               testSig.sigIn,
               sigFn
             )
           }
           val sigName: Rfc8941.Token = Rfc8941.Token(testSig.sigName)
           if testSig.signature != "" then
              test(s"$i verify ${testSig.msg} signature statically") {
                newReqIO.map { req =>
                   val x: Option[Either[CharacterCodingException, String]] =
                     MessageSignature.getSignature(req, sigName).map(pair => pair._2.decodeAscii)
                   assertEquals(x, Some(Right(testSig.signature)), req)
                }
              }
           else
              test(s"$i verify ${testSig.sigName} was created in ${testSig.msg}") {
                newReqIO.map { req =>
                  assert(MessageSignature.getSignature(req, sigName).isDefined, req)
                }
              }
           test(s"$i verify generated request in ${testSig.msg}") {
             for
                req   <- newReqIO
                keyId <- verify(req, FiniteDuration(testSig.now, SECONDS), HttpSig(sigName))
             yield assertEquals(keyId, PureKeyId(testSig.keyId), req)
           }
      }
