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
import run.cosy.http.Http
import run.cosy.http.Http.Request
import run.cosy.http.auth.{HTTPHeaderParseException, SelectorException}
import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.headers.Rfc8941.{Params, SfDict, SfList, SfString}
import run.cosy.http.messages.HeaderId.SfHeaderId
import run.cosy.http.messages.Selectors.{CollationTp, SelFormat}
import run.cosy.http.messages.{AtSelectorFns, Parameters}

import scala.collection.immutable.{ArraySeq, HashMap, ListMap}
import scala.util.{Failure, Success, Try}

class Selectors[F[_], H <: Http](using SelectorFns[F, H])
    extends AtSelectors[F, H] with HeaderSelectors[F, H]

object Selectors:
   import Parameters.*

   /** When a header is named sf this means it can be interpreted as a structured header. This map
     * specifies how to interpret them. for information on how existing headers can be understood as
     * working with rfc8941 see
     * [[https://greenbytes.de/tech/webdav/draft-ietf-httpbis-retrofit-latest.html Retrofit Structured Fields for HTTP]]
     * olso some good overview documentation on
     * [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers HTTP Headers]]
     */
   val knownHeaders: Map[String, Selectors.SelFormat] = HashMap(
     "accept"                           -> SelFormat.List,
     "accept-encoding"                  -> SelFormat.List,
     "accept-language"                  -> SelFormat.List,
     "accept-patch"                     -> SelFormat.List,
     "accept-post"                      -> SelFormat.List,
     "accept-ranges"                    -> SelFormat.List,
     "access-control-allow-credentials" -> SelFormat.Item,
     "access-control-allow-headers"     -> SelFormat.List,
     "access-control-allow-methods"     -> SelFormat.List,
     "access-control-allow-origin"      -> SelFormat.Item,
     "access-control-expose-headers"    -> SelFormat.List,
     "access-control-max-age"           -> SelFormat.Item,
     "access-control-request-headers"   -> SelFormat.List,
     "access-control-request-method"    -> SelFormat.Item,
     "age"                              -> SelFormat.Item,
     "allow"                            -> SelFormat.List,
     "alpn"                             -> SelFormat.List,
     "alt-svc"                          -> SelFormat.Dictionary,
     "alt-used"                         -> SelFormat.Item,
     "cache-control"                    -> SelFormat.Dictionary,
     "cdn-loop"                         -> SelFormat.List,
     "clear-site-data"                  -> SelFormat.List,
     "connection"                       -> SelFormat.List,
     "content-encoding"                 -> SelFormat.List,
     "content-language"                 -> SelFormat.List,
     "content-length"                   -> SelFormat.List,
     "content-type"                     -> SelFormat.Item,
     "cross-origin-resource-policy"     -> SelFormat.Item,
     "dnt"                              -> SelFormat.Item,
     "expect"                           -> SelFormat.Dictionary,
     "expect-ct"                        -> SelFormat.Dictionary,
     "host"                             -> SelFormat.Item,
     "keep-alive"                       -> SelFormat.Dictionary,
     "max-forwards"                     -> SelFormat.Item,
     "origin"                           -> SelFormat.Item,
     "pragma"                           -> SelFormat.Dictionary,
     "prefer"                           -> SelFormat.Dictionary,
     "preference-applied"               -> SelFormat.Dictionary,
     "retry-after"                      -> SelFormat.Item,
     "sec-websocket-extensions"         -> SelFormat.List,
     "sec-websocket-protocol"           -> SelFormat.List,
     "sec-websocket-version"            -> SelFormat.Item,
     "server-timing"                    -> SelFormat.List,
     "surrogate-control"                -> SelFormat.Dictionary,
     "te"                               -> SelFormat.List,
     "timing-allow-origin"              -> SelFormat.List,
     "trailer"                          -> SelFormat.List,
     "transfer-encoding"                -> SelFormat.List,
     "upgrade-insecure-requests"        -> SelFormat.Item,
     "vary"                             -> SelFormat.List,
     "x-content-type-options"           -> SelFormat.Item,
     "x-frame-options"                  -> SelFormat.Item,
     "x-xss-protection"                 -> SelFormat.List
   )

   def render(
       id: HeaderId,
       tp: id.AllowedCollation
   )(headerValues: NonEmptyList[String]): Try[String] =
     tp match
        case Raw          => raw(headerValues)
        case Strict       => sfValue(headerValues, id.asInstanceOf[SfHeaderId].format)
        case Bin          => bin(headerValues)
        case DictSel(key) => sfDictNameSelector(key, headerValues)
   end render

   def sfValue(headers: NonEmptyList[String], interp: SelFormat): Try[String] =
      val combinedHeaders = headers.toList.mkString(", ")
      // sfParse(combinedHeaders).map{
      interp match
         case SelFormat.Item       => sfParseItem(combinedHeaders).map(_.canon)
         case SelFormat.Dictionary => sfParseDict(combinedHeaders).map(_.canon)
         case SelFormat.List       => sfParseItem(combinedHeaders).map(_.canon)

   def sfParseItem(
       headerValue: String
   ): Try[Rfc8941.PItem[?]] =
     Rfc8941.Parser.sfItem.parseAll(headerValue) match
        case Left(err)   => Failure(HTTPHeaderParseException(err, headerValue))
        case Right(dict) => Success(dict)

   def sfParseDict(
       headerValue: String
   ): Try[Rfc8941.SfDict] =
     Rfc8941.Parser.sfDictionary.parseAll(headerValue) match
        case Left(err)   => Failure(HTTPHeaderParseException(err, headerValue))
        case Right(dict) => Success(dict)

   /** Dict selector with name param */
   def sfDictNameSelector(
       nameSelector: Rfc8941.SfString,
       headers: NonEmptyList[String]
   ): Try[String] =
      val combinedHeaders = headers.toList.mkString(", ")
      for
         dict <- sfParseDict(combinedHeaders)
         name <- Try(Rfc8941.Token(nameSelector.asciiStr))
         value <- dict.get(name)
           .toRight(SelectorException(
             s"No dictionary element >$nameSelector< in with value >$combinedHeaders"
           )).toTry
      yield value.canon

   def raw(headers: NonEmptyList[String]): Try[String] =
     Success(headers.map(_.trim).toList.mkString(", "))

   def bin(headers: NonEmptyList[String]): Try[String] = Try {
     headers.map(h =>
       ArraySeq.ofByte(h.trim.nn.getBytes(java.nio.charset.StandardCharsets.US_ASCII).nn).canon
     )
       .toList.mkString(", ")
   }

   def sfParseList(
       headerValue: String
   ): Try[Rfc8941.SfList] =
     Rfc8941.Parser.sfList.parseAll(headerValue) match
        case Left(err)   => Failure(HTTPHeaderParseException(err, headerValue))
        case Right(dict) => Success(dict)

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
