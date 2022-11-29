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

import cats.data.NonEmptyList
import cats.syntax.all.*
import run.cosy.http.Http
import run.cosy.http.Http.Request
import run.cosy.http.auth.{HTTPHeaderParseException, ParsingExc, SelectorException}
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.headers.Rfc8941.{Params, SfDict, SfList, SfString}
import run.cosy.http.headers.{ParsingException, Rfc8941}
import run.cosy.http.messages.HeaderId.SfHeaderId
import run.cosy.http.messages.Selectors.{CollationTp, SelFormat}
import run.cosy.http.messages.{AtSelectorFns, Parameters}
import scodec.bits.ByteVector

import scala.collection.immutable.{ArraySeq, HashMap, ListMap}
import scala.util.{Failure, Success, Try}

class Selectors[F[_], H <: Http](using SelectorFns[F, H])
    extends AtSelectors[F, H] with HeaderSelectors[F, H]

object Selectors:
   import Parameters.*

   def render(
       id: HeaderId,
       tp: id.AllowedCollation
   )(headerValues: NonEmptyList[String]): Either[ParsingExc, String] =
     tp match
        case Raw          => raw(headerValues)
        case Strict       => sfValue(headerValues, id.asInstanceOf[SfHeaderId].format)
        case Bin          => bin(headerValues)
        case DictSel(key) => sfDictNameSelector(key, headerValues)
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
         case Raw          => Params()
         case Strict       => Params(sfTk -> true)
         case Bin          => Params(bsTk -> true)
         case DictSel(key) => Params(keyTk -> key)

   case class DictSel(key: Rfc8941.SfString) extends CollationTp
   object Bin                                extends CollationTp
   object Raw                                extends CollationTp
   object Strict                             extends CollationTp

   /** Strict Format types, can be one of three */
   enum SelFormat:
      case List
      case Item // a parameterized item
      case Dictionary

//   import run.cosy.http.messages.Selectors.CollationTp as Ct

//
//     /** a list because sometimes multiple lengths are sent! */
//     "content-length" -> Ct.Strict,
//     "signature"      -> Ct.Strict,
//     "content-digest" -> Ct.Strict,
//
//     /** Defined in:
//       * [[https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-client-cert-field-03#section-2 httpbis-client-cert]]
//       */
//     "client-cert" -> Ct.Strict,
//     "client-cert" -> Ct.Strict,
//     "forwarded"   -> Ct.Raw
//   )
