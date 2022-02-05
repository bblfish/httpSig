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

package run.cosy.akka.http.headers

import _root_.akka.http.scaladsl.model.headers.{CustomHeader, RawHeader}
import _root_.akka.http.scaladsl.model.{HttpHeader, ParsingException, Uri}
import cats.parse.Parser
import run.cosy.akka.http.AkkaTp
import run.cosy.akka.http.headers.Encoding.UnicodeString
import run.cosy.akka.http.headers.{BetterCustomHeader, BetterCustomHeaderCompanion}
import run.cosy.http.auth.{HTTPHeaderParseException, InvalidSigException, SignatureInputMatcher}
import run.cosy.http.headers.*
import run.cosy.http.headers.Rfc8941.{
  IList,
  Item,
  PItem,
  Parameterized,
  Params,
  SfDict,
  SfInt,
  SfList,
  SfString,
  Token
}

import java.security.{PrivateKey, PublicKey, Signature}
import java.time.Instant
import scala.collection.immutable
import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

/** [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-the-signature-input-http-fi 4.1 The 'Signature-Input' HTTP header]]
  * defined in "Signing HTTP Messages" HttpBis RFC. Since version 03 signature algorithms have been
  * re-introduced, but we only implement "hs2019" for simplicity.
  *
  * @param text
  */
final case class `Signature-Input`(sig: SigInputs)
    extends BetterCustomHeader[`Signature-Input`]:
   override val companion         = `Signature-Input`
   override def renderInRequests  = true
   override def renderInResponses = true
   override def value: String =
      import Rfc8941.Serialise.given
      sig.si.map { (tk, si) => (tk, si.il) }.asInstanceOf[Rfc8941.SfDict].canon
end `Signature-Input`

object `Signature-Input`
    extends BetterCustomHeaderCompanion[`Signature-Input`]
    with SignatureInputMatcher[AkkaTp.type]:
   type SI = `Signature-Input`
   override val name = "Signature-Input"

   def apply(name: Rfc8941.Token, sigInput: SigInput): `Signature-Input` =
     `Signature-Input`(SigInputs(name, sigInput))
   def unapply(h: HttpHeader): Option[SigInputs] =
     h match
        case _: (RawHeader | CustomHeader) if h.lowercaseName == lowercaseName =>
          parse(h.value).toOption
        case _ => None

   def parse(value: String): Try[SigInputs] =
     Rfc8941.Parser.sfDictionary.parseAll(value) match
        case Left(e) => Failure(HTTPHeaderParseException(e, value))
        case Right(lm) => SigInputs(lm).toRight(
            InvalidSigException(
              "Signature-Input Header Parses but data structure is not appropriate"
            )
          ).toTry
end `Signature-Input`
