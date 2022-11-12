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

import cats.data.NonEmptyList
import _root_.run.cosy.http.auth.{
  HTTPHeaderParseException,
  InvalidSigException,
  SelectorException,
  UnableToCreateSigHeaderException
}
import _root_.run.cosy.http.headers.Rfc8941.Serialise.given
import _root_.run.cosy.http.headers.Rfc8941.SfDict

import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

/** Selectors for specifying properties of a header for Signing Http Messages, and how to create them
  *
  * @tparam HttpMsg
  *   the type of the Http Message (req or response) to look at when constructing the signing
  *   strings for that header
  */
trait MessageSelector[-HttpMessage]:
   lazy val sf               = Rfc8941.SfString(lowercaseName)
   lazy val special: Boolean = lowercaseName.startsWith("@")

   def lowercaseName: String
   /* Is this a special @message selector that must be used on requests even if signing a response?
    * By default no.*/
   def specialForRequests: Boolean = false

   /** @return
     *   a signing string for this header for a given HttpMessage Fail if the header does not exist
     *   or is malformed
     */
   def signingString(msg: HttpMessage, params: Rfc8941.Params = ListMap()): Try[String]
end MessageSelector

/** Selectors that don't look at headers, but at message properties such as Http Request, but that
  * don't require parameters
  */
trait BasicMessageSelector[HttpMessage] extends MessageSelector[HttpMessage]:
   /** @return
     *   a signing string for this header for a given HttpMessage Fail if the header does not exist
     *   or is malformed
     */
   def signingString(msg: HttpMessage, params: Rfc8941.Params = ListMap()): Try[String] =
     if params.isEmpty then signingStringValue(msg).map(fullSigStr)
     else
        Failure(
          SelectorException(s"selector $lowercaseName does not take parameters. Received " + params)
        )

   /** to implement in subclasses * */
   protected def signingStringValue(msg: HttpMessage): Try[String]
   protected def fullSigStr(str: String): String =
     "\"" + lowercaseName + "\": " + str
end BasicMessageSelector

/* types that specifically select in the headers only */
trait HeaderSelector[HM]:
   def lowercaseHeaderName: String
   def filterHeaders(msg: HM): Try[NonEmptyList[String]]
end HeaderSelector

/** trait for headers recognised as being capable of being interpreted as a dictionary. Unless the
  * right parameters are passed thought, it need not be.
  */
trait DictSelector[HM] extends MessageSelector[HM] with HeaderSelector[HM]:
   val keyParam = Rfc8941.Token("key")
   val sfParam  = Rfc8941.Token("sf")

   /** @param params
     *   the parameters passed to this request. Can be "sf", "key", "req" or others. All parameters
     *   passed must continue on to the output. todo: is that correct or should one fail on all non
     *   undertsaod parameters?
     * @return
     *   a signing string for the selected entry of the sf-dictionary encoded header. Fail if the
     *   header does not exist or is malformed
     */
   override def signingString(msg: HM, params: Rfc8941.Params = ListMap()): Try[String] =
      val strVal: Try[String] =
        params.collectFirst {
          case (`keyParam`, name: Rfc8941.SfString) => signingValueFor(msg, name)
        } getOrElse {
          if params.contains(sfParam) then sfDictParse(msg).map(_.canon)
          else plainSigningValueFor(msg)
        }
      strVal.map(v => headerName(params) + v)

   final def plainSigningValueFor(msg: HM): Try[String] = filterHeaders(msg).map { nonel =>
     nonel.toList.mkString(", ")
   }

   final def signingValueFor(msg: HM, key: Rfc8941.SfString): Try[String] =
     for
        dict  <- sfDictParse(msg)
        token <- Try(Rfc8941.Token(key.asciiStr))
        value <- dict.get(token).toRight(
          UnableToCreateSigHeaderException(s"could not find $key in header [$dict]")
        ).toTry
     yield value.canon

   final def sfDictParse(msg: HM): Try[SfDict] =
     for
        headerValues <- filterHeaders(msg)
        sfDict       <- parse(SelectorOps.collate(headerValues))
     yield sfDict

   /** the header name given the params. This function can be used everywhere actually note: the
     * reason the token must be surrounded by quotes `"` is because a Token may end with `:`
     */
   final def headerName(params: Rfc8941.Params): String =
      val attrs =
        if params.isEmpty then ""
        else
           params.toList
             .map[String]((key, value) =>
               if value.isInstanceOf[Boolean] then key.canon
               else key.canon + "=" + value.canon
             ).mkString(";", ";", "")
      s""""$lowercaseName"$attrs: """

   def parse(headerValue: String): Try[SfDict] =
     Rfc8941.Parser.sfDictionary.parseAll(headerValue) match
        case Left(err)   => Failure(HTTPHeaderParseException(err, headerValue))
        case Right(dict) => Success(dict)

end DictSelector

object `@signature-params`:
   def onlyForRequests: Boolean                  = false
   val lowercaseName                             = "@signature-params"
   val pitem                                     = Rfc8941.PItem(Rfc8941.SfString(lowercaseName))
   def signingString(sigInput: SigInput): String = s""""@signature-params": ${sigInput.canon}"""

/** @param selectorDB
  *   a map from selector names (eg. `@status` or `date`) to the selector for those messages
  * @tparam HM
  *   The Type of the HttpMessage for the platform
  */
case class SelectorOps[HM] private (selectorDB: Map[Rfc8941.SfString, MessageSelector[HM]]):
   import Rfc8941.Serialise.given

   import scala.language.implicitConversions

   /** add new selectors to this one */
   def append(selectors: MessageSelector[HM]*): SelectorOps[HM] =
      val pairs = for (selector <- selectors)
        yield (Rfc8941.SfString(selector.lowercaseName) -> selector)
      SelectorOps(selectorDB ++ pairs.toSeq)

//	/**
//	 * Is the selector valid? I.e. do we have a selector and if it has parameters are they allowed?
//	 * @param selector e.g. `date`, `@query;name="q"` or `"x-dictionary";key="a"`
//	 * @return true if valid
//	 * todo: this does not seem to be used! check why? Remove
//	 */
//	def valid(selector: Rfc8941.PItem[Rfc8941.SfString]): Boolean =
//		selectorDB.get(selector.item).map(messageSelector =>
//			messageSelector.valid(selector.params)
//		).getOrElse(false)

   def select(msg: HM, selectorInfo: Rfc8941.PItem[Rfc8941.SfString]): Try[String] =
     for
        selector <- get(selectorInfo.item)
        str      <- selector.signingString(msg, selectorInfo.params)
     yield str
   end select

   def get(selectorName: Rfc8941.SfString): Try[MessageSelector[HM]] =
     selectorDB.get(selectorName.item)
       .toRight(
         InvalidSigException(s"Header ${selectorName.item} is not supported")
       ).toTry

end SelectorOps

object SelectorOps:
   def apply[Msg](msgSelectors: MessageSelector[Msg]*): SelectorOps[Msg] =
      val pairs = for (selector <- msgSelectors)
        yield Rfc8941.SfString(selector.lowercaseName) -> selector
      new SelectorOps(Map(pairs*))

   def collate(values: NonEmptyList[String]): String =
     values.map( // remove obsolete line folding and trim
       _.split('\n').map(_.trim).mkString(" ")).toList.mkString(", ")
