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

import run.cosy.http.Http
import run.cosy.http.headers.Rfc8941
import cats.data.NonEmptyList

import scala.util.{Failure, Success, Try}
import scala.collection.immutable.ListMap
import run.cosy.http.auth.{HTTPHeaderParseException, ParsingExc, SelectorException}
import run.cosy.http.headers.Rfc8941.Params
import run.cosy.http.messages.HeaderId.SfHeaderId
import scodec.bits.ByteVector
import run.cosy.http.headers.Rfc8941.Serialise.given
import cats.syntax.all.*

object HeaderSelectors:
   import Parameters.*

   def render(
       id: HeaderId,
       tp: id.AllowedCollation
   )(headerValues: NonEmptyList[String]): Either[ParsingExc, String] =
     tp match
        case LS        => raw(headerValues)
        case SF        => sfValue(headerValues, id.asInstanceOf[SfHeaderId].format)
        case BS        => bin(headerValues)
        case Dict(key) => sfDictNameSelector(key, headerValues)
   end render

   def sfValue(headers: NonEmptyList[String], interp: SelFormat): Either[ParsingExc, String] =
      val combinedHeaders = headers.toList.mkString(", ")
      // sfParse(combinedHeaders).map{
      interp match
         case SelFormat.Item       => sfParseItem(combinedHeaders).map(_.canon)
         case SelFormat.Dictionary => sfParseDict(combinedHeaders).map(_.canon)
         case SelFormat.List       => sfParseItem(combinedHeaders).map(_.canon)

   def sfParseItem(
       headerValue: String
   ): Either[HTTPHeaderParseException, Rfc8941.PItem[?]] =
     Rfc8941.Parser.sfItem.parseAll(headerValue).leftMap(err =>
       HTTPHeaderParseException(err, headerValue)
     )

   def sfParseDict(
       headerValue: String
   ): Either[HTTPHeaderParseException, Rfc8941.SfDict] =
     Rfc8941.Parser.sfDictionary.parseAll(headerValue).leftMap(err =>
       HTTPHeaderParseException(err, headerValue)
     )

   /** Dict selector with name param */
   def sfDictNameSelector(
       nameSelector: Rfc8941.SfString,
       headers: NonEmptyList[String]
   ): Either[ParsingExc, String] =
      val combinedHeaders = headers.toList.mkString(", ")
      for
         dict <- sfParseDict(combinedHeaders)
         name <- Rfc8941.Parser.sfToken.parseAll(nameSelector.asciiStr).leftMap(p =>
           HTTPHeaderParseException(p, s"could not convert >$nameSelector< to token")
         )
         value <- dict.get(name)
           .toRight(SelectorException(
             s"No dictionary element >$nameSelector< in with value >$combinedHeaders"
           ))
      yield value.canon

   def raw(headers: NonEmptyList[String]): Either[ParsingExc, String] =
     Right(headers.map(_.trim).toList.mkString(", "))

   def bin(headers: NonEmptyList[String]): Either[ParsingExc, String] = Right {
     headers.map(h =>
       ByteVector.view(h.trim.nn.getBytes(java.nio.charset.StandardCharsets.US_ASCII).nn).canon
     ).toList.mkString(", ")
   }

   def sfParseList(
       headerValue: String
   ): Either[HTTPHeaderParseException, Rfc8941.SfList] =
     Rfc8941.Parser.sfList.parseAll(headerValue).leftMap(err =>
       HTTPHeaderParseException(err, headerValue)
     )

   sealed trait CollationTp:
      def toParam: Params = this match
         case LS        => Params()
         case SF        => Params(sfTk -> true)
         case BS        => Params(bsTk -> true)
         case Dict(key) => Params(keyTk -> key)

   /** interpret the field as a dict, and select an element of it */
   case class Dict(key: Rfc8941.SfString) extends CollationTp

   /** Byte Sequence or ;bs in the spec */
   object BS extends CollationTp

   /** This is the default. It takes list of headers, trims them then combines them by separating
     * them with `, `. Since intermediaries can collate the headers with various number of spaces
     * between them, it is recommended that a) if multiple headers are used they be comgined on the
     * sending side, or b) that these multiple values be parsed out and recombined... (That means
     * that there is knowledge required for which headers this can work with.
     */
   object LS extends CollationTp

   /** Structured Fields or ;sf in the spec: interpret the headers as RFC8941 constructs. Which ones
     * must be agreed upon individually for each header.
     */
   object SF extends CollationTp

   /** Strict Format types, can be one of three */
   enum SelFormat:
      case List
      case Item // a parameterized item
      case Dictionary
