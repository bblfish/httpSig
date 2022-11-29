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
import scala.util.{Try, Success, Failure}
import scala.collection.immutable.ListMap
import run.cosy.http.auth.ParsingExc

class RequestHeaderSel[F[_], H <: Http](
    override val name: HeaderId,
    collTp: name.AllowedCollation,
    override val selectorFn: SelectorFn[Http.Request[F, H]],
    override val params: Rfc8941.Params = ListMap()
) extends RequestSelector[F, H]:
   override def renderNel(nel: NonEmptyList[String]): Either[ParsingExc, String] =
     Selectors.render(name, collTp)(nel).map(header + _)

class ResponseHeaderSel[F[_], H <: Http](
    override val name: HeaderId,
    collTp: name.AllowedCollation,
    override val selectorFn: SelectorFn[Http.Response[F, H]],
    override val params: Rfc8941.Params = ListMap()
) extends ResponseSelector[F, H]:
   override def renderNel(nel: NonEmptyList[String]): Either[ParsingExc, String] =
     Selectors.render(name, collTp)(nel).map(header + _)

trait HeaderSelectors[F[_], H <: Http](using sf: HeaderSelectorFns[F, H]):
   import Selectors.CollationTp

   /** build a request Header selector for the given header id.
     * @collTp
     *   the way to interpret the headers values returned
     */
   def onRequest(name: HeaderId)(
       collTp: name.AllowedCollation
   ): RequestSelector[F, H] =
     new RequestHeaderSel[F, H](
       name,
       collTp,
       sf.requestHeaders(name),
       collTp.toParam
     )

   def onResponse(name: HeaderId)(
       collTp: name.AllowedCollation
   ): ResponseSelector[F, H] =
     new ResponseHeaderSel[F, H](
       name,
       collTp,
       sf.responseHeaders(name),
       collTp.toParam
     )

   import run.cosy.http.messages.HeaderIds.retrofit as retro

   /** for information on how existing headers can be understood as working with rfc8941 see
     * [[https://greenbytes.de/tech/webdav/draft-ietf-httpbis-retrofit-latest.html Retrofit Structured Fields for HTTP]]
     * oslo some good overview documentation on
     * [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers HTTP Headers]]
     */
   object RequestHd:
      import Selectors.{SelFormat, CollationTp as Ct}
      import run.cosy.http.messages.HeaderIds.Request as req

      lazy val accept = onRequest(retro.accept)
      /* could not find authorization in retrofit. Could one send more than one? Yet, I think. */
      lazy val authorization   = onRequest(req.authorization)
      lazy val `cache-control` = onRequest(retro.`cache-control`)
      lazy val `content-type`  = onRequest(retro.`content-type`)

      /** a list because sometimes multiple lengths are sent! */
      lazy val `content-length` = onRequest(retro.`content-length`)
      lazy val signature_sf     = onRequest(req.signature)
      // todo: it feels like dict objs should allow the user to select the type cloer to the point
      // of creating the signature.
      lazy val signature_dict   = onRequest(req.signature)
      lazy val `content-digest` = onRequest(req.`content-digest`)

      /** Defined in:
        * [[https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-client-cert-field-03#section-2 httpbis-client-cert]]
        */
      lazy val `client-cert`       = onRequest(req.`client-cert`)
      lazy val `client-cert-chain` = onRequest(req.`client-cert-chain`)
      lazy val forwarded           = onRequest(req.forwarded)
   end RequestHd

   object ResponseHd:
      import Selectors.{SelFormat, CollationTp as Ct}
      import run.cosy.http.messages.HeaderIds.Response as res

      lazy val `accept-post`   = onResponse(retro.`accept-post`)
      lazy val `cache-control` = onResponse(res.`cache-control`)

      /** Clients cannot send date headers via JS. Also not really useful as the time is available
        * in the Signature-Input in unix time. see
        * [[https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6 rfc9110]] for handling
        * requirements (from httpbis-retrofit)
        */
      lazy val date             = onResponse(res.date)
      lazy val `content-type`   = onResponse(res.`content-type`)
      lazy val `content-length` = onResponse(res.`content-length`)

      /** One could create a new parameter to convert to SF-ETAG specified by httpbis-retrofit */
      lazy val etag      = onResponse(res.etag)
      lazy val signature = onResponse(res.signature)

      /** [[https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-digest-headers-10#section-2
        * digest-headers draft rfc]
        */
      lazy val `content-digest` =
        onResponse(res.`content-digest`)
   end ResponseHd
end HeaderSelectors
