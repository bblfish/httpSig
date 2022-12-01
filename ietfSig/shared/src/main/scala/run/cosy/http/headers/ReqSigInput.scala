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
import run.cosy.http.messages.RequestSelector

import java.time.Instant
import scala.collection.immutable.ListSet

/** ReqSigInput is useful for building a Signature-Input structure in a typesafe manner for a
  * request. Compare with SigInput which is used to parse incoming requests where the order of of
  * the parameters is important.
  */
class ReqSigInput[F[_], H <: Http](
    val selectors: List[RequestSelector[F, H]] = List(),
    val params: ListSet[SigIn.Param] = ListSet()
):
   import Rfc8941.Serialise.given
   import SigIn.*
   def siginputStr: String =
     selectors.map(sel => sel.identifier).mkString("(", " ", ")")
   def paramStr: String =
      import Rfc8941.Serialise.*
      if params.size == 0 then ""
      else
         params.map {
           case KeyId(v)   => "keyid=" + v.canon
           case Created(v) => "created=" + v.canon
           case Expires(v) => "expires=" + v.canon
           case Nonce(v)   => "nonce=" + v.canon
           case Tag(v)     => "tag=" + v.canon
           case Alg(v)     => s"""alg="$v""""
         }.mkString(";", ";", "")
   override def toString() = """"@signature-params": """ + siginputStr + paramStr

object ReqSigInput:
   def apply[F[_], H <: Http](sels: RequestSelector[F, H]*)(params: SigIn.Param*) =
     new ReqSigInput[F, H](sels.toList, ListSet(params*))

object SigIn:
   sealed trait Param

   case class KeyId(value: SfString) extends Param
   case class Created(value: SfInt)  extends Param
   case class Expires(value: SfInt)  extends Param
   case class Nonce(value: SfString) extends Param
   case class Tag(value: SfString)   extends Param
   case class Alg(value: SigAlg)     extends Param

   object Created:
      def apply(time: Long): Either[NumberOutOfBoundsException, Created] =
        try
           Right(Created(SfInt(time)))
        catch case e: NumberOutOfBoundsException => Left(e)
      def apply(time: Instant): Either[NumberOutOfBoundsException, Created] =
        apply(time.getEpochSecond.nn)

   object Expires:
      def apply(time: Long): Either[NumberOutOfBoundsException, Expires] =
        try
           Right(Expires(SfInt(time)))
        catch case e: NumberOutOfBoundsException => Left(e)

      def apply(time: Instant): Either[NumberOutOfBoundsException, Expires] =
        apply(time.getEpochSecond.nn)

   enum SigAlg:
      case `rsa-pss-sha512`, `rsa-v1_5-sha256`, `hmac-sha256`, `ecdsa-p256-sha256`,
        `ecdsa-p384-sha384`, `ed25519`
