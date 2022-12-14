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
import _root_.run.cosy.http.messages.{ReqComponentDB, RequestSelector, `@signature-params`}
import _root_.run.cosy.http.{Http, HttpOps}
import cats.MonadError
import cats.data.NonEmptyList
import cats.effect.kernel.{Clock, MonadCancel}
import cats.syntax.all.*
import run.cosy.http.auth.MessageSignature.SignatureVerifier
import scodec.bits.ByteVector

import java.nio.charset.StandardCharsets
import java.security.{PrivateKey, PublicKey, SignatureException}
import java.util.Locale
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

object MessageSignature:

   import bobcats.Verifier.{Signature, SigningString}

   type SignatureVerifier[F[_], A] = (SigningString, Signature) => F[A]
   type SigningF[F[_]]             = SigningString => F[Signature]

   /** return the sigbase given the request selectors and the corresponding @signature-params
     * string.
     */
   protected def sigBaseFn[H <: Http](
       req: Http.Request[H],
       selectors: List[RequestSelector[H]],
       sigParamStr: String
   ): Either[ParsingExc, List[String]] =
     selectors.foldM(List(sigParamStr)) { (lst, selector) =>
       selector.signingStr(req).map(_ :: lst)
     }

   def sigBase[H <: Http](
       req: Http.Request[H],
       sigIn: ReqSigInput[H]
   ): Either[ParsingExc, SigningString] =
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
   def getSignature[H <: Http](req: Http.Request[H], name: Rfc8941.Token)(
       using hops: HttpOps[H]
   ): Option[(SigInput, ByteVector)] =
      import hops.{`Signature-Input`, Signature}
      req.headerSeq.collectFirst {
        case `Signature-Input`(inputs) if inputs.si.contains(name) =>
          inputs.si(name)
      }.flatMap { siginput =>
        req.headerSeq.collectFirst {
          case Signature(sigs) if sigs.sigmap.contains(name) =>
            (siginput, sigs.sigmap(name).item)
        }
      }

   /** Generate a function to create a new HttpRequest with the given Signature-Input header. Called
     * by the client that is building the message.
     *
     * @param name
     *   the name of the signature todo: this should probably be done by the code, as it will depend
     *   on other signatures present in the header
     * @param sin
     *   header describing the headers to sign as per "Signing Http Messages" RFC
     * @return
     *   a function to create a new HttpRequest when given signing function wrapped in a F the F can
     *   capture an IllegalArgumentException if the required headers are not present in the request
     *
     * todo: why does it require meF to be a MonadError of a Throwable? MonadError[F, Exception]
     * does not compile...
     */
   def withSigInput[F[_], H <: Http](
       req: Http.Request[H],
       name: Rfc8941.Token,
       sin: ReqSigInput[H],
       signerF: SigningF[F]
   )(using
       meF: MonadError[F, Throwable],
       hOps: HttpOps[H]
   ): F[Http.Request[H]] =
      import hOps.{`Signature-Input`, Signature}
      for
         toSignBytes <- meF.fromEither(sigBase(req, sin))
         signature   <- signerF(toSignBytes)
      yield req.addHeaders(Seq(
        `Signature-Input`(name, SigInput(sin)),
        Signature(Signatures(name, signature))
      ))
   end withSigInput

   /** Generate the signature string, given the `signature-input` header. Note, that the headers to
     * be signed, always contains the `signature-input` header itself.
     *
     * @param sigInput
     *   the sigInput header specifying the
     * @return
     *   signing String for given Signature Input on this http message. This string will either need
     *   to be verified with a public key against the given one, or will need to be signed to be
     *   added to the Request. In the latter case use the withSigInput method. todo: it may be more
     *   correct if the result is a byte array, rather than a Unicode String.
     */
   def signatureBase[H <: Http](
       req: Http.Request[H],
       sigInput: SigInput,
       selectorDB: ReqComponentDB[H]
   ): Either[ParsingExc, SigningString] =
      val xl: Either[ParsingExc, List[RequestSelector[H]]] = sigInput.headerItems
        .foldLeftM(List[RequestSelector[H]]()) { (lst, pih) =>
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

/** Adds extensions methods to sign HttpMessage-s - be they requests or responses.
  *
  * Note!! We have a number of closely related but independent functors. In order to help
  * distinguish them I will use the following naming conventions:
  *
  *   - The `F[_]` tied inside `Http`` type (this may be important for reading header trailers that
  *     appear at the end of a header)
  *     - Http4s requires `FH : Async`` ( I think)
  *     - Akka the type is actually `akka.stream.scaladsl.Source`
  *   - `F[_]` for fetching key data info (this really has to be Async)
  *     - `IO`` for http4s
  *     - `Future`` for Akka
  *   - `FB[_]` for Bobcats crypto
  *     - `AsyncIO`` for java (it's all done in a thread)
  *     - `IO`` for JS
  *
  * I will use those names to distinguish the types of functors we are using.
  */
class MessageSignature[H <: Http](using ops: HttpOps[H]):

   import Http.*
   import MessageSignature.*
   import bobcats.Verifier.{Signature, SigningString}
   import ops.*

   val urlStrRegex = "<(.*)>".r

   extension (req: Http.Request[H])(using selectorDB: ReqComponentDB[H])
     def signatureBase(sigInput: SigInput): Either[ParsingExc, SigningString] =
       MessageSignature.signatureBase(req, sigInput, selectorDB)

   extension (req: Http.Request[H])

      inline def sigBase(sigIn: ReqSigInput[H]): Either[ParsingExc, SigningString] =
        MessageSignature.sigBase(req, sigIn)

      inline def getSignature(name: Rfc8941.Token): Option[(SigInput, ByteVector)] =
        MessageSignature.getSignature(req, name)

      inline def withSigInput[F[_]](
          name: Rfc8941.Token,
          sin: ReqSigInput[H],
          signerF: SigningF[F]
      )(using meF: MonadError[F, Throwable]): F[Http.Request[H]] =
        MessageSignature.withSigInput(req, name, sin, signerF)

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
