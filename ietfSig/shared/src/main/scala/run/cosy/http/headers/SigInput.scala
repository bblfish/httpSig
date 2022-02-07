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

import run.cosy.http.auth.{AuthExc, InvalidSigException}
import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.{
  IList,
  Item,
  PItem,
  Parameterized,
  SfDict,
  SfInt,
  SfString,
  Token
}

import java.time.Instant
import scala.collection.immutable.ListMap
import scala.concurrent.duration.FiniteDuration

/** SigInputs are Maps from Signature Names to SigInput entries that this server understands.
  *
  * @param value
  * @return
  */
final case class SigInputs private (si: ListMap[Rfc8941.Token, SigInput]):
   def get(key: Rfc8941.Token): Option[SigInput] = si.get(key)
   def append(more: SigInputs): SigInputs        = new SigInputs(si ++ more.si)
   def append(key: Rfc8941.Token, sigInput: SigInput): SigInputs =
     new SigInputs(si + (key -> sigInput))

object SigInputs:
   /* create a SigInput with a single element */
   def apply(name: Rfc8941.Token, siginput: SigInput) =
     new SigInputs(ListMap(name -> siginput))

   def apply(lm: SfDict): Option[SigInputs] =
      val valid = filter(lm)
      if valid.isEmpty then None
      else Some(SigInputs(valid))

   /** Filter out the clearly invalid inputs.
     */
   def filter(lm: SfDict): ListMap[Rfc8941.Token, SigInput] =
     lm.collect {
       case (sigName, SigInput(sigInput)) => (sigName, sigInput)
     }
   end filter

/** A SigInput is a valid Signature-Input build on an Rfc8941 Internal List. restricted to those
  * this server can understand. For example in the following header the SigInput is the structure
  * following the attribute of the `sig1` dictionary entry <pre> Signature-Input: sig1=("@method"
  * "@target-uri" "host" "date" \ "cache-control" "x-empty-header" "x-example");created=1618884475\
  * ;keyid="test-key-rsa-pss" </pre> todo: An improved version would be more lenient, allowing
  * opt-in refinements.
  *
  * As a Validated data structure, we can keep all the data present in a header for a particular
  * signature, as that is needed to verify the signature itself. Indeed extra attributes will be
  * vital to verify a signature, since the data from this header is part of the signature
  *
  * @param il
  */
final case class SigInput private (val il: IList):

   import Rfc8941.Serialise.given
   import SigInput.*

   // todo: verify that collecting only SfStrings is ok
   def headers: Seq[String]              = il.items.collect { case PItem(SfString(str), _) => str }
   def headerItems: Seq[PItem[SfString]] = il.items.map(_.asInstanceOf[PItem[SfString]])

   def keyid: Option[Rfc8941.SfString] =
     il.params.get(keyidTk) match
        case Some(s: SfString) => Some(s)
        case _                 => None

   def alg: Option[String]   = il.params.get(algTk).collect { case SfString(str) => str }
   def nonce: Option[String] = il.params.get(nonceTk).collect { case SfString(str) => str }
   def isValidAt(i: FiniteDuration, shift: Long = 0): Boolean =
     created.map(_ - shift <= i.toSeconds).getOrElse(true) &&
       expires.map(_ + shift >= i.toSeconds).getOrElse(true)
   def created: Option[Long] = il.params.get(createdTk).collect { case SfInt(time) => time }
   def expires: Option[Long] = il.params.get(expiresTk).collect { case SfInt(time) => time }
   def canon: String         = il.canon

object SigInput:
   /** registered metadata parameters for Signature specifications as per
     * [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-initial-contents-2 §6.2.2 of 07 spec]].
     */
   val algTk     = Token("alg")
   val createdTk = Token("created")
   val expiresTk = Token("expires")
   val keyidTk   = Token("keyid")
   val nonceTk   = Token("nonce")

   val registeredParams = Seq(algTk, createdTk, expiresTk, keyidTk, nonceTk)

   val Empty = ListMap.empty[Token, Item]

   /** parse the string to a SigInput */
   def apply(innerListStr: String): Option[SigInput] =
     Rfc8941.Parser.innerList.parseAll(innerListStr).toOption.flatMap(SigInput.apply)

   def apply(il: IList): Option[SigInput] =
     if valid(il) then Some(new SigInput(il)) else None

   // this is really functioning as a constructor in pattern matching contexts
   def unapply(pzd: Parameterized): Option[SigInput] =
     pzd match
        case il: IList if valid(il) => Some(new SigInput(il))
        case _                      => None

   /** A Valid SigInput IList has an Internal list of parameterized SfStrings
     */
   def valid[H](il: IList): Boolean =
      def headersOk = il.items.forall { pit =>
        pit.item.isInstanceOf[SfString] // todo: one could check the parameters follow a pattern...
      }
      def paramsOk = il.params.forall {
        case (`keyidTk`, item: SfString)                       => true
        case (`createdTk`, _: SfInt) | (`expiresTk`, _: SfInt) => true
        case (`algTk`, _: SfString) | (`nonceTk`, _: SfString) => true
        // we are lenient on non-registered params
        case (attr, _) => !registeredParams.contains(attr)
      }
      headersOk && paramsOk
   end valid
