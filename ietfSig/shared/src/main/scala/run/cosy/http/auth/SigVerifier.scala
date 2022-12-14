/*
 * Copyright 2021 Henry Story
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package run.cosy.http.auth

import cats.MonadError
import cats.syntax.all.toFunctorOps
import cats.syntax.flatMap.*
import run.cosy.http.auth.MessageSignature.SignatureVerifier
import run.cosy.http.headers.{HttpSig, Rfc8941, SigInput}
import run.cosy.http.messages.ReqComponentDB
import run.cosy.http.{Http, HttpOps}
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration

/** Signature Verifier builds a function to verify a given signature on a request. It takes
  *   - a database of message and header selectors,
  *   - and a function that from a keyId can fetch the verification function (built out of public
  *     and private key info) return an Authenticated Agent of type A
  *
  * In a server setup, the function to fetch the keyId is going to be very stable, and related to
  * the particular protocol defined, so it makes more sense to have an object that encompasses that
  * function, can be named, and re-used. After that the HttpSig (id) really depends on the request
  * (it should be taken from the particular request) and the time depends on the event of the
  * httprequest arriving, so they will be variable.
  *
  * Todo: signature verification should be a class by itself, as one may want to use it without
  * generating signatures.
  *
  * @param fetchKeyId
  *   function taking a keyId and returning a Verifier function that can verify a signature using
  *   the public or symmetric key info from that keyId.
  * @tparam F
  *   The context to fetch the key. Something like Future or IO
  * @tparam A
  *   The type of agent returned by the successful verification. (placing this inside context F
  *   should allow the agent A to be constructed only on verification of key material)
  */
case class SigVerifier[F[_], A, H <: Http](
    selectorDB: ReqComponentDB[H],
    fetchKeyId: Rfc8941.SfString => F[SignatureVerifier[F, A]]
)(using
    hOps: HttpOps[H],
    ME: MonadError[F, Throwable] // <- todo: can one be more specific?
):

   /** Verify that the signature Id in the request is valid
     *
     * @param req
     *   The request on which to check the function
     * @param now
     *   the current time to check the signature validity at
     * @param sigId
     *   the id of the signature to find in the request (taken from the Authorization header for
     *   example)
     * @return
     *   a verified Agent Id `A` after
     *   1. find the signature input data in the req (no need to continue if that is not there)
     *   1. verify the signature is still valid given the time
     *   1. build the signature base from the request
     *   1. if all that is good, use `fetchKeyId` to construct the needed verifier from the sigId
     *      (fetch public key info)
     *   1. Verify this Request message, and if verified return the Agent object A
     * @return
     */
   def apply(req: Http.Request[H], now: FiniteDuration, sigId: HttpSig): F[A] =
     for
        (si: SigInput, sig: ByteVector) <- ME.fromOption(
          MessageSignature.getSignature[H](req, sigId.proofName),
          InvalidSigException(
            s"could not find Signature-Input and Signature for Sig name '${sigId.proofName}'"
          )
        )
        sigStr <-
          if si.isValidAt(now)
          then ME.fromEither(MessageSignature.signatureBase(req, si, selectorDB))
          else
             ME.fromEither(
               Left(InvalidSigException(s"Signature no longer valid at $now"))
             ) // todo exception tuning
        keyId <- ME.fromOption(si.keyid, KeyIdException("keyId missing or badly formatted"))
        signatureVerificiationFn <- fetchKeyId(keyId)
        agent                    <- signatureVerificiationFn(sigStr, sig)
     yield agent

end SigVerifier
