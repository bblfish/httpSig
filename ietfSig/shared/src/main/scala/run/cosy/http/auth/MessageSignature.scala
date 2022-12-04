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

import _root_.run.cosy.http.Http.*
import _root_.run.cosy.http.auth.Agent
import _root_.run.cosy.http.headers.*
import _root_.run.cosy.http.headers.Rfc8941.*
import _root_.run.cosy.http.messages.{RequestSelector, ReqComponentDB, `@signature-params`}
import _root_.run.cosy.http.{Http, HttpOps}
import cats.MonadError
import cats.data.NonEmptyList
import cats.effect.kernel.{Clock, MonadCancel}
import cats.syntax.all.*
import scodec.bits.ByteVector

import java.nio.charset.StandardCharsets
import java.security.{PrivateKey, PublicKey}
import java.util.Locale
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.util.{Failure, Success, Try}

object MessageSignature:

   import bobcats.Verifier.{Signature, SigningString}

   type SignatureVerifier[F[_], A] = (SigningString, Signature) => F[A]
   type SigningF[F[_]]             = SigningString => F[Signature]

/** Adds extensions methods to sign HttpMessage-s - be they requests or responses.
  */
class MessageSignature[F[_], H <: Http](using ops: HttpOps[H]):

   /** return the sigbase but without the attributes, which can be appended later */
   protected def sigBaseFn(
       req: Http.Request[F, H],
       selectors: List[RequestSelector[F, H]],
       sigParamStr: String
   ): Either[ParsingExc, List[String]] =
     selectors.foldM(List(sigParamStr)) { (lst, selector) =>
       selector.signingStr(req).map(_ :: lst)
     }

   import Http.*
   import MessageSignature.*
   import bobcats.Verifier.{Signature, SigningString}
   import ops.*

   val urlStrRegex = "<(.*)>".r

   // todo: is this the right place

   /** [[https://tools.ietf.org/html/draft-ietf-httpbis-message-signatures-03#section-4.1 Message Signatures]]
     * Note: the F[_] here is playing too many roles. In http4s it is the type of the content
     * itself, though in Akka that is ignored. The other role of F is as a type of wrapper to fetch
     * remote objects. This is ok, as for our uses we don't ever look at the content of either
     * messages, only at the headers. (but for http4s we do need to know the type of the content or
     * else we cannot correctly type the result of adding headers to a Message)
     */
   extension (req: Http.Request[F, H])(using selectorDB: ReqComponentDB[F, H])

      /** Generate a function to create a new HttpRequest with the given Signature-Input header.
        * Called by the client that is building the message.
        *
        * @param name
        *   the name of the signature todo: this should probably be done by the code, as it will
        *   depend on other signatures present in the header
        * @param sigInput
        *   header describing the headers to sign as per "Signing Http Messages" RFC
        * @return
        *   a function to create a new HttpRequest when given signing function wrapped in a F the F
        *   can capture an IllegalArgumentException if the required headers are not present in the
        *   request
        */
      //      def withSigInput(
      //          name: Rfc8941.Token,
      //          sigInput: SigInput,
      //          signerF: SigningF[F]
      //      )(using meF: MonadError[F, Throwable]): F[Http.Request[F,H]] =
      //        for
      //           toSignBytes <- meF.fromTry(signingStr(sigInput))
      //           signature   <- signerF(toSignBytes)
      //        yield req.addHeaders(Seq(
      //          `Signature-Input`(name, sigInput),
      //          Signature(Signatures(
      //            name,
      //            ArraySeq.unsafeWrapArray(signature.toArray)
      //          )) // todo: align the types everywhere if poss
      //        ))
      //      end withSigInput

      /** Generate the signature string, given the `signature-input` header. Note, that the headers
        * to be signed, always contains the `signature-input` header itself.
        *
        * @param sigInput
        *   the sigInput header specifying the
        * @return
        *   signing String for given Signature Input on this http message. This string will either
        *   need to be verified with a public key against the given one, or will need to be signed
        *   to be added to the Request. In the latter case use the withSigInput method. todo: it may
        *   be more correct if the result is a byte array, rather than a Unicode String.
        */
      def signatureBase(sigInput: SigInput): Either[ParsingExc, SigningString] =
         val xl: Either[ParsingExc, List[RequestSelector[F, H]]] = sigInput.headerItems
           .foldLeftM(List[RequestSelector[F, H]]()) { (lst, pih) =>
             selectorDB.get(pih.item.asciiStr, pih.params).map(_ :: lst)
           }
         for
            list <- xl
            sigParamStr =
              s""""@signature-params": (${list.reverse.map(s => s.identifier).mkString(" ")})"""
            baseList <- sigBaseFn(req, list, sigParamStr)
            bytes <- ByteVector.encodeAscii(
              baseList.mkString("\n") + `@signature-params`.paramStr(sigInput)
            ).leftMap(ce => CharacterCodingExc(ce.getMessage.nn))
         yield bytes
      end signatureBase

   extension (req: Http.Request[F, H])

      def sigBase(sigIn: ReqSigInput[F, H]): Either[ParsingExc, SigningString] =
        for
           lines <- sigBaseFn(req, sigIn.selectors.reverse, sigIn.toString)
           bytes <- ByteVector.encodeAscii(lines.mkString("\n"))
             .leftMap(ce =>
               CharacterCodingExc(
                 "should never happen when called using ReqSigInput" + ce.getMessage.nn
               )
             )
        yield bytes

      /** get the signature data for a given signature name eg `sig1` from the headers.
        *
        * <pre> Signature-Input: sig1=("@authority" "content-type")\
        * ;created=1618884475;keyid="test-key-rsa-pss" Signature:
        * sig1=:KuhJjsOKCiISnKHh2rln5ZNIrkRvu...: </pre>
        *
        * @return
        *   a pair of SigInput Data, and the signature bytes The SigInput Data tells us what the
        *   signature bytes are a signature of and how to interpret them, i.e. what the headers are
        *   that were signed, where the key is and what the signing algorithm used was
        */
      def getSignature(name: Rfc8941.Token): Option[(SigInput, ByteVector)] =
        req.headers.collectFirst {
          case `Signature-Input`(inputs) if inputs.si.contains(name) =>
            inputs.si(name)
        }.flatMap { siginput =>
          req.headers.collectFirst {
            case Signature(sigs) if sigs.sigmap.contains(name) =>
              (siginput, sigs.sigmap(name).item)
          }
        }

   /** take a function `fetchKeyId` which takes a keyId argument and fetches in context F that key's
     * data (public key, etc) allowing it to construct a signature verifier that can return an Agent
     * ID of type `A`.
     *
     * signatureAuthN lifts such a function, into a function which given a Signature name, will use
     * the information in the Http Message header to construct the signing string and verify the
     * signature using the verifier returned by fetchKeyId.
     *
     * return an Authenticated Agent of type A
     *
     * todo: specify Throwable more precisely in MonadError? todo: the function `fetchKeyId` returns
     * a F[SignatureVerifier] but the signature specification may also set constraints on the type
     * of signature allowed. So we are missing here a way to check that the constraints are
     * satisfied. Check what constraint verifications are needed!
     *
     * @param fetchKeyId
     *   function taking a keyId and returning a Verifier function using the public key info from
     *   that keyId.
     * @tparam F
     *   The context to fetch the key. Something like Future or IO.
     * @tparam A
     *   The type of agent returned by the successful verification. (placing this inside context F
     *   should allow the agent A to be constructed only on verification of key material)
     * @return
     *   a function which given a particular Http Signature name will return an Agent of type A in
     *   the context F. Calling this function for a Http Signature name (e.g. "sig1") will
     *   1. find the signature input data (to construct a signature) and the signature bytes
     *   1. verify the signature is still valid given the clock,
     *   1. use `fetchKeyId` to construct the needed verifier
     *   1. Verify the header, and if verified return the Agent object A
     */
   //      def signatureAuthN[A](
   //          fetchKeyId: Rfc8941.SfString => F[SignatureVerifier[F, A]]
   //      )(using
   //          ME: MonadError[F, Throwable],
   //          clock: Clock[F]
   //      ): HttpSig => F[A] = (httpSig) =>
   //        for
   //           (si: SigInput, sig: Bytes) <- ME.fromTry(req.getSignature(httpSig.proofName)
   //             .toRight(InvalidSigException(
   //               s"could not find Signature-Input and Signature for Sig name '${httpSig.proofName}' "
   //             )).toTry)
   //           now <- summon[Clock[F]].realTime
   //           sigStr <-
   //             if si.isValidAt(now)
   //             then ME.fromTry(req.signingStr(si))
   //             else
   //                ME.fromTry(
   //                  Failure(new Throwable(s"Signature no longer valid at $now"))
   //                ) // todo exception tuning
   //           keyId <- ME.fromOption(si.keyid, KeyIdException("keyId missing or badly formatted"))
   //           signatureVerificiationFn <- fetchKeyId(keyId)
   //           agent <- signatureVerificiationFn(sigStr, ByteVector(sig.toArray)) // todo: unify bytes
   //        yield agent

   //   /* needed for request-response dependencies */
   //   extension [F[_]](response: Response[F, H])(using
   //       requestSelDB: SelectorOps[Request[F, H]],
   //       responseSelDB: SelectorOps[Response[F, H]]
   //   )
   //
   //      def signingString(sigInput: SigInput, request: Request[F, H]): Try[SigningString] =
   //         import Rfc8941.Serialise.{*, given}
   //
   //         @tailrec
   //         def buildSigString(todo: Seq[Rfc8941.PItem[SfString]], onto: String): Try[String] =
   //           todo match
   //              case Seq() => Success(onto)
   //              case pih :: tail =>
   //                if (pih == `@signature-params`.pitem) then
   //                   val sigp = `@signature-params`.signingString(sigInput)
   //                   Success(if onto == "" then sigp else onto + "\n" + sigp)
   //                else
   //                   val x = responseSelDB.get(pih.item) match
   //                      case Success(selector) => selector.signingString(response, pih.params)
   //                      case Failure(x) => requestSelDB.get(pih.item).flatMap { selector =>
   //                          if selector.specialForRequests then
   //                             selector.signingString(request, pih.params)
   //                          else Failure(x)
   //                        }
   //                   x match
   //                      case Success(hdr) =>
   //                        buildSigString(todo.tail, if onto == "" then hdr else onto + "\n" + hdr)
   //                      case f => f
   //         end buildSigString
   //
   //         buildSigString(sigInput.headerItems.appended(`@signature-params`.pitem), "")
   //           .flatMap(string => ByteVector.encodeAscii(string).toTry)
   //      end signingString

end MessageSignature
