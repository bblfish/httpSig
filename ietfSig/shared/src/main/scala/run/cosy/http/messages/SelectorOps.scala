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

package run.cosy.http.messages

import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.SigInput
import scala.util.{Try, Success, Failure}
import cats.data.NonEmptyList
import run.cosy.http.headers.SigInput
import run.cosy.http.auth.InvalidSigException

object `@signature-params`:
   def onlyForRequests: Boolean               = false
   val lowercaseName                          = "@signature-params"
   val pitem                                  = Rfc8941.PItem(Rfc8941.SfString(lowercaseName))
   def signingStr(sigInput: SigInput): String = s""""@signature-params": ${sigInput.canon}"""

/** @param selectorDB
  *   a map from selector names (eg. `@status` or `date`) to the selector for those messages
  * @tparam HM
  *   The Type of the HttpMessage for the platform
  */
//case class SelectorOps[HM] private (selectorDB: Map[Rfc8941.SfString, MessageSelector[HM]]):
//  import scala.language.implicitConversions
//  import Rfc8941.Serialise.given
//
//  /** add new selectors to this one */
//  def append(selectors: MessageSelector[HM]*): SelectorOps[HM] =
//    val pairs = for (selector <- selectors)
//      yield (Rfc8941.SfString(selector.lowercaseName) -> selector)
//    SelectorOps(selectorDB ++ pairs.toSeq)
//
//  //	/**
//  //	 * Is the selector valid? I.e. do we have a selector and if it has parameters are they allowed?
//  //	 * @param selector e.g. `date`, `@query;name="q"` or `"x-dictionary";key="a"`
//  //	 * @return true if valid
//  //	 * todo: this does not seem to be used! check why? Remove
//  //	 */
//  //	def valid(selector: Rfc8941.PItem[Rfc8941.SfString]): Boolean =
//  //		selectorDB.get(selector.item).map(messageSelector =>
//  //			messageSelector.valid(selector.params)
//  //		).getOrElse(false)
//
//  def select(msg: HM, selectorInfo: Rfc8941.PItem[Rfc8941.SfString]): Try[String] =
//    for
//      selector <- get(selectorInfo.item)
//      str      <- selector.signingString(msg, selectorInfo.params)
//    yield str
//  end select
//
//  def get(selectorName: Rfc8941.SfString): Try[MessageSelector[HM]] =
//    selectorDB.get(selectorName.item)
//      .toRight(
//        InvalidSigException(s"Header ${selectorName.item} is not supported")
//      ).toTry
//
//end SelectorOps
//
//object SelectorOps:
//  def apply[Msg](msgSelectors: MessageSelector[Msg]*): SelectorOps[Msg] =
//    val pairs = for (selector <- msgSelectors)
//      yield Rfc8941.SfString(selector.lowercaseName) -> selector
//    new SelectorOps(Map(pairs*))
//
//  def collate(values: NonEmptyList[String]): String =
//    values.map( // remove obsolete line folding and trim
//      _.split('\n').map(_.trim).mkString(" ")).toList.mkString(", ")
//
