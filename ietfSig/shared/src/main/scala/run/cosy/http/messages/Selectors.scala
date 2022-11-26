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
import run.cosy.http.messages.Selectors.Sf
import run.cosy.http.messages.{AtSelectorFns, Parameters}

import scala.collection.immutable.{ArraySeq, ListMap, HashMap}
import scala.util.{Failure, Success, Try}

class Selectors[F[_], H <: Http](using SelectorFns[F, H])
    extends AtSelectors[F, H] with HeaderSelectors[F, H]

/** These selectors can all be used to build a Signature-Input */
open class RequestSel[F[_], H <: Http](
    val name: SfString,
    val selectorFn: SelectorFn[Http.Request[F, H]],
    override val params: Rfc8941.Params = ListMap()
) extends RequestSelector[F, H]

open class ResponseSel[F[_], H <: Http](
    val name: SfString,
    val selectorFn: SelectorFn[Http.Response[F, H]],
    override val params: Rfc8941.Params = ListMap()
) extends ResponseSelector[F, H]

trait AtSelectors[F[_], H <: Http](using sf: AtSelectorFns[F, H]):

   import Rfc8941.Syntax.*
   val `@method`: RequestSelector[F, H] =
     RequestSel[F, H](sf"@method", sf.method)

   val `@request-target`: RequestSelector[F, H] =
     RequestSel[F, H](sf"@request-target", sf.requestTarget)

   val `@target-uri`: RequestSelector[F, H] =
     RequestSel[F, H](sf"@target-uri", sf.targetUri)

   val `@authority`: RequestSelector[F, H] =
     RequestSel[F, H](sf"@authority", sf.authority)

   val `@scheme`: RequestSelector[F, H] =
     RequestSel[F, H](sf"@scheme", sf.scheme)

   val `@path`: RequestSelector[F, H] =
     RequestSel[F, H](sf"@path", sf.path)

   val `@query`: RequestSelector[F, H] =
     RequestSel[F, H](sf"@query", sf.query)

   /** todo: arguably the paramName should be an Rfc8941.Token, because it will be used as the key
     * in a dict, and that is a token. But then one has to be careful to render that token as a
     * string in the `"@query-param";key="q":` header
     */
   def `@query-param`(paramName: SfString): RequestSelector[F, H] =
     RequestSel[F, H](
       sf"@query-param",
       sf.queryParam(paramName),
       Params(Parameters.nameTk -> paramName)
     )

   val `@status`: ResponseSelector[F, H] =
     ResponseSel[F, H](sf"@status", sf.status)

end AtSelectors

trait HeaderSelectors[F[_], H <: Http](using sf: HeaderSelectorFns[F, H]):
   import Selectors.CollationTp

   /** todo: this does not quite work as a function for 2 reasons:
     * 1. `name`'s type is too general: the name could be illegal (and the response does not return a Try, so one
     * could create an illegal header.
     * 2. collTp could be illegal for the given header. Eg. CollTp sf for a header that is not known
     *    to be interpretable as an sfDict.
     */
   def requestHeader(
       name: Rfc8941.SfString,
       collTp: CollationTp = CollationTp.Raw
   ): RequestSelector[F, H] =
     new RequestSel[F, H](
       name,
       sf.requestHeaders(name),
       collTp.toParam
     ):
        override def renderNel(nel: NonEmptyList[String]): Try[String] =
          Selectors.render(collTp, lowercaseName)(nel).map(identifier + _)
   end requestHeader

   def responseHeader(
       name: Rfc8941.SfString,
       collTp: CollationTp = CollationTp.Raw
   ): ResponseSelector[F, H] =
     new ResponseSel[F, H](
       name,
       sf.responseHeaders(name),
       collTp.toParam
     ):
        override def renderNel(nel: NonEmptyList[String]): Try[String] =
          Selectors.render(collTp, lowercaseName)(nel).map(identifier + _)
   end responseHeader

   /** for information on how existing headers can be understood as working with rfc8941 see
     * [[https://greenbytes.de/tech/webdav/draft-ietf-httpbis-retrofit-latest.html Retrofit Structured Fields for HTTP]]
     * oslo some good overview documentation on
     * [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers HTTP Headers]]
     */
   object RequestHd:
      import Selectors.{Sf, CollationTp as Ct}
      lazy val accept = requestHeader(sf"accept", Ct.Strict(Sf.List))
      /* could not find authorization in retrofit. Could one send more than one? Yet, I think. */
      lazy val authorization   = requestHeader(sf"authorization", Ct.Bin)
      lazy val `cache-control` = requestHeader(sf"cache-control", Ct.Raw)
      lazy val `content-type`  = requestHeader(sf"content-type", Ct.Strict(Sf.Item))

      /** a list because sometimes multiple lengths are sent! */
      lazy val `content-length` = requestHeader(sf"content-length", Ct.Strict(Sf.List))
      lazy val signature        = requestHeader(sf"signature", Ct.Strict(Sf.Dictionary))
      lazy val `content-digest` = requestHeader(sf"content-digest", Ct.Strict(Sf.Dictionary))

      /** Defined in:
        * [[https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-client-cert-field-03#section-2 httpbis-client-cert]]
        */
      lazy val `client-cert`       = requestHeader(sf"client-cert", Ct.Strict(Sf.Item))
      lazy val `client-cert-chain` = requestHeader(sf"client-cert", Ct.Strict(Sf.List))
      lazy val forwarded           = requestHeader(sf"forwarded", Ct.Raw)
   end RequestHd

   object ResponseHd:
      import Selectors.{Sf, CollationTp as Ct}

      lazy val `accept-post`   = responseHeader(sf"accept-post", Ct.Strict(Sf.List))
      lazy val `cache-control` = responseHeader(sf"cache-control", Ct.Raw)

      /** Clients cannot send date headers via JS. Also not really useful as the time is available
        * in the Signature-Input in unix time. see
        * [[https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6 rfc9110]] for handling
        * requirements (from httpbis-retrofit)
        */
      lazy val date             = responseHeader(sf"date", Ct.Raw)
      lazy val `content-type`   = responseHeader(sf"content-type", Ct.Strict(Sf.Item))
      lazy val `content-length` = responseHeader(sf"content-length", Ct.Strict(Sf.List))

      /** One could create a new parameter to convert to SF-ETAG specified by httpbis-retrofit */
      lazy val etag      = responseHeader(sf"etag", Ct.Raw)
      lazy val signature = responseHeader(sf"signature", Ct.Strict(Sf.Dictionary))

      /** [[https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-digest-headers-10#section-2
        * digest-headers draft rfc]
        */
      lazy val `content-digest` = responseHeader(sf"content-digest", Ct.Strict(Sf.Dictionary))
   end ResponseHd
end HeaderSelectors

object Selectors:
   import Parameters.*

   /** Strict Format types, can be one of three */
   enum Sf:
      case List
      case Item // a parameterized item
      case Dictionary

   enum CollationTp:
      case Raw, Bin
      case Strict(tp: Sf)
      case DictSel(key: Rfc8941.SfString)

      def toParam: Params = this match
         case Raw          => Params()
         case Strict(tp)   => Params(sfTk -> true)
         case Bin          => Params(bsTk -> true)
         case DictSel(key) => Params(keyTk -> key)

   def render(
       tp: CollationTp,
       headerName: String //todo: the header name should be a special type
   )(headerValues: NonEmptyList[String]): Try[String] =
      import CollationTp.*
      tp match
         case Raw          => raw(headerValues)
         case Strict(tp)   => sfValue(headerValues, tp)
         case Bin          => bin(headerValues)
         case DictSel(key) => sfDictNameSelector(key, headerValues, headerName)
   end render

   def sfParseDict(
       headerValue: String
   ): Try[Rfc8941.SfDict] =
     Rfc8941.Parser.sfDictionary.parseAll(headerValue) match
        case Left(err)   => Failure(HTTPHeaderParseException(err, headerValue))
        case Right(dict) => Success(dict)

   def sfParseList(
       headerValue: String
   ): Try[Rfc8941.SfList] =
     Rfc8941.Parser.sfList.parseAll(headerValue) match
        case Left(err)   => Failure(HTTPHeaderParseException(err, headerValue))
        case Right(dict) => Success(dict)

   def sfParseItem(
       headerValue: String
   ): Try[Rfc8941.PItem[?]] =
     Rfc8941.Parser.sfItem.parseAll(headerValue) match
        case Left(err)   => Failure(HTTPHeaderParseException(err, headerValue))
        case Right(dict) => Success(dict)

   def sfValue(headers: NonEmptyList[String], interp: Sf): Try[String] =
      val combinedHeaders = headers.toList.mkString(", ")
      // sfParse(combinedHeaders).map{
      interp match
         case Sf.Item       => sfParseItem(combinedHeaders).map(_.canon)
         case Sf.Dictionary => sfParseDict(combinedHeaders).map(_.canon)
         case Sf.List       => sfParseItem(combinedHeaders).map(_.canon)

   /** Dict selector with name param */
   def sfDictNameSelector(
       nameSelector: Rfc8941.SfString,
       headers: NonEmptyList[String],
       headerName: String
   ): Try[String] =
      val combinedHeaders = headers.toList.mkString(", ")
      for
         dict <- sfParseDict(combinedHeaders)
         name <- Try(Rfc8941.Token(nameSelector.asciiStr))
         value <- dict.get(name)
           .toRight(SelectorException(
             s"No dictionary element >$nameSelector< in header $headerName with value >$combinedHeaders"
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

   import run.cosy.http.messages.Selectors.CollationTp as Ct

   /**
     * When a header is named sf this means it can be interpreted as a structured header.
     * This map specifies how to interpret them.
     * for information on how existing headers can be understood as working with rfc8941 see
     * [[https://greenbytes.de/tech/webdav/draft-ietf-httpbis-retrofit-latest.html Retrofit Structured Fields for HTTP]]
     * olso some good overview documentation on
     * [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers HTTP Headers]]
     */
   val knownHeaders: Map[String, Selectors.Sf] = HashMap(
     "accept"                           -> Sf.List,
     "accept-encoding"                  -> Sf.List,
     "accept-language"                  -> Sf.List,
     "accept-patch"                     -> Sf.List,
     "accept-post"                      -> Sf.List,
     "accept-ranges"                    -> Sf.List,
     "access-control-allow-credentials" -> Sf.Item,
     "access-control-allow-headers"     -> Sf.List,
     "access-control-allow-methods"     -> Sf.List,
     "access-control-allow-origin"      -> Sf.Item,
     "access-control-expose-headers"    -> Sf.List,
     "access-control-max-age"           -> Sf.Item,
     "access-control-request-headers"   -> Sf.List,
     "access-control-request-method"    -> Sf.Item,
     "age"                              -> Sf.Item,
     "allow"                            -> Sf.List,
     "alpn"                             -> Sf.List,
     "alt-svc"                          -> Sf.Dictionary,
     "alt-used"                         -> Sf.Item,
     "cache-control"                    -> Sf.Dictionary,
     "cdn-loop"                         -> Sf.List,
     "clear-site-data"                  -> Sf.List,
     "connection"                       -> Sf.List,
     "content-encoding"                 -> Sf.List,
     "content-language"                 -> Sf.List,
     "content-length"                   -> Sf.List,
     "content-type"                     -> Sf.Item,
     "cross-origin-resource-policy"     -> Sf.Item,
     "dnt"                              -> Sf.Item,
     "expect"                           -> Sf.Dictionary,
     "expect-ct"                        -> Sf.Dictionary,
     "host"                             -> Sf.Item,
     "keep-alive"                       -> Sf.Dictionary,
     "max-forwards"                     -> Sf.Item,
     "origin"                           -> Sf.Item,
     "pragma"                           -> Sf.Dictionary,
     "prefer"                           -> Sf.Dictionary,
     "preference-applied"               -> Sf.Dictionary,
     "retry-after"                      -> Sf.Item,
     "sec-websocket-extensions"         -> Sf.List,
     "sec-websocket-protocol"           -> Sf.List,
     "sec-websocket-version"            -> Sf.Item,
     "server-timing"                    -> Sf.List,
     "surrogate-control"                -> Sf.Dictionary,
     "te"                               -> Sf.List,
     "timing-allow-origin"              -> Sf.List,
     "trailer"                          -> Sf.List,
     "transfer-encoding"                -> Sf.List,
     "upgrade-insecure-requests"        -> Sf.Item,
     "vary"                             -> Sf.List,
     "x-content-type-options"           -> Sf.Item,
     "x-frame-options"                  -> Sf.Item,
     "x-xss-protection"                 -> Sf.List
   )
//
//     /** a list because sometimes multiple lengths are sent! */
//     "content-length" -> Ct.Strict(Sf.List),
//     "signature"      -> Ct.Strict(Sf.Dictionary),
//     "content-digest" -> Ct.Strict(Sf.Dictionary),
//
//     /** Defined in:
//       * [[https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-client-cert-field-03#section-2 httpbis-client-cert]]
//       */
//     "client-cert" -> Ct.Strict(Sf.Item),
//     "client-cert" -> Ct.Strict(Sf.List),
//     "forwarded"   -> Ct.Raw
//   )
