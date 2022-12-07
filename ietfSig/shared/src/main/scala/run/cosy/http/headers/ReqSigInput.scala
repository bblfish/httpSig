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

package run.cosy.http.headers

import run.cosy.http.Http
import run.cosy.http.headers.Rfc8941.{SfInt, SfString, Token}
import run.cosy.http.headers.SigInput.{algTk, createdTk, expiresTk, keyidTk, nonceTk, tagTk}
import run.cosy.http.messages.RequestSelector

import java.time.Instant
import scala.collection.immutable.ListSet

/** ReqSigInput is useful for building a Signature-Input structure in a typesafe manner for a
  * request. Compare with SigInput which is used to parse incoming requests where the order of of
  * the parameters is important.
  *
  * @tparam FH[_]
  *   a functor related to the Http Type.
  */
class ReqSigInput[FH[_], H <: Http](
    val selectors: List[RequestSelector[FH, H]] = List(),
    val params: ListSet[SigIn.Param] = ListSet()
):
   import Rfc8941.Serialise.given
   import SigIn.*
   def siginputStr: String =
     selectors.map(sel => sel.identifier).mkString("(", " ", ")")
   def paramStr: String =
      import Rfc8941.Serialise.*
      params.map(_.canon).mkString("")

   override def toString() = """"@signature-params": """ + siginputStr + paramStr

end ReqSigInput

object ReqSigInput:
   def apply[F[_], H <: Http](sels: RequestSelector[F, H]*)(params: SigIn.Param*) =
     new ReqSigInput[F, H](sels.toList, ListSet(params*))

object SigIn:
   import Rfc8941.Serialise.given
   sealed trait Param:
      def toRfcParam: Rfc8941.Param
      def canon: String = toRfcParam.canon

   case class KeyId(value: SfString) extends Param:
      override def toRfcParam: Rfc8941.Param = (keyidTk, value)
   case class Created(value: SfInt) extends Param:
      override def toRfcParam: Rfc8941.Param = (createdTk, value)
   case class Expires(value: SfInt) extends Param:
      override def toRfcParam: Rfc8941.Param = (expiresTk, value)
   case class Nonce(value: SfString) extends Param:
      override def toRfcParam: Rfc8941.Param = (nonceTk, value)
   case class Tag(value: SfString) extends Param:
      override def toRfcParam: Rfc8941.Param = (tagTk, value)
   case class Alg(value: SigAlg) extends Param:
      override def toRfcParam: Rfc8941.Param = (algTk, SfString(value.toString))

   object Created:
      @throws[NumberOutOfBoundsException]
      def apply(time: Long): Created =
        Created(SfInt(time))
      @throws[NumberOutOfBoundsException]
      def apply(time: Instant): Created =
        apply(time.getEpochSecond.nn)

   object Expires:
      @throws[NumberOutOfBoundsException]
      def apply(time: Long): Expires =
        Expires(SfInt(time))

      @throws[NumberOutOfBoundsException]
      def apply(time: Instant): Expires =
        apply(time.getEpochSecond.nn)

   enum SigAlg:
      case `rsa-pss-sha512`, `rsa-v1_5-sha256`, `hmac-sha256`, `ecdsa-p256-sha256`,
        `ecdsa-p384-sha384`, `ed25519`
