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

import run.cosy.http.messages.Selectors.SelFormat as SF

object HeaderIds:
   lazy val all =
      import Request.{`content-length` as _, `content-type` as _, *}
      import Response.{etag, `content-digest` as _, `content-length` as _}
      import retrofit.*
      Seq(
        `accept-encoding`,
        `accept-language`,
        `accept-patch`,
        `accept-post`,
        `accept-ranges`,
        `accept`,
        `access-control-allow-credentials`,
        `access-control-allow-headers`,
        `access-control-allow-methods`,
        `access-control-allow-origin`,
        `access-control-expose-headers`,
        `access-control-max-age`,
        `access-control-request-headers`,
        `access-control-request-method`,
        `age`,
        `allow`,
        `alpn`,
        `alt-svc`,
        `alt-used`,
        `cache-control`,
        `cdn-loop`,
        `clear-site-data`,
        `client-cert-chain`,
        `client-cert`,
        `connection`,
        `content-digest`,
        `content-encoding`,
        `content-language`,
        `content-length`,
        `content-type`,
        `cross-origin-resource-policy`,
        date,
        `dnt`,
        `expect-ct`,
        `expect`,
        `host`,
        `keep-alive`,
        `max-forwards`,
        `origin`,
        `pragma`,
        `prefer`,
        `preference-applied`,
        `retry-after`,
        `sec-websocket-extensions`,
        `sec-websocket-protocol`,
        `sec-websocket-version`,
        `server-timing`,
        `surrogate-control`,
        `te`,
        `timing-allow-origin`,
        `trailer`,
        `transfer-encoding`,
        `upgrade-insecure-requests`,
        `vary`,
        `x-content-type-options`,
        `x-frame-options`,
        `x-xss-protection`,
        authorization,
        etag,
        forwarded,
        signature
      )

   /** Header types as speccified
     * https://greenbytes.de/tech/webdav/draft-ietf-httpbis-retrofit-latest.html#compatible
     */
   object retrofit:
      lazy val `accept`          = HeaderId.list("accept").toTry.get
      lazy val `accept-encoding` = HeaderId.list("accept-encoding").toTry.get
      lazy val `accept-language` = HeaderId.list("accept-language").toTry.get
      lazy val `accept-patch`    = HeaderId.list("accept-patch").toTry.get
      lazy val `accept-post`     = HeaderId.list("accept-post").toTry.get
      lazy val `accept-ranges`   = HeaderId.list("accept-ranges").toTry.get
      lazy val `access-control-allow-credentials` =
        HeaderId.item("access-control-allow-credentials").toTry.get
      lazy val `access-control-allow-headers` =
        HeaderId.list("access-control-allow-headers").toTry.get
      lazy val `access-control-allow-methods` =
        HeaderId.list("access-control-allow-methods").toTry.get
      lazy val `access-control-allow-origin` =
        HeaderId.item("access-control-allow-origin").toTry.get
      lazy val `access-control-expose-headers` =
        HeaderId.list("access-control-expose-headers").toTry.get
      lazy val `access-control-max-age` = HeaderId.item("access-control-max-age").toTry.get
      lazy val `access-control-request-headers` =
        HeaderId.list("access-control-request-headers").toTry.get
      lazy val `access-control-request-method` =
        HeaderId.item("access-control-request-method").toTry.get
      lazy val `age`: HeaderId.ItemId     = HeaderId.item("age").toTry.get
      lazy val `allow`: HeaderId.ListId   = HeaderId.list("allow").toTry.get
      lazy val `alpn`: HeaderId.ListId    = HeaderId.list("alpn").toTry.get
      lazy val `alt-svc`: HeaderId.DictId = HeaderId.dict("alt-svc").toTry.get
      lazy val `alt-used`                 = HeaderId.item("alt-used").toTry.get
      lazy val `cache-control`            = HeaderId.dict("cache-control").toTry.get
      lazy val `cdn-loop`                 = HeaderId.list("cdn-loop").toTry.get
      lazy val `clear-site-data`          = HeaderId.list("clear-site-data").toTry.get
      lazy val `connection`               = HeaderId.list("connection").toTry.get
      lazy val `content-encoding`         = HeaderId.list("content-encoding").toTry.get
      lazy val `content-language`         = HeaderId.list("content-language").toTry.get
      lazy val `content-length`           = HeaderId.list("content-length").toTry.get
      lazy val `content-type`             = HeaderId.item("content-type").toTry.get
      lazy val `cross-origin-resource-policy` =
        HeaderId.item("cross-origin-resource-policy").toTry.get
      lazy val `dnt`                       = HeaderId.item("dnt").toTry.get
      lazy val `expect`                    = HeaderId.dict("expect").toTry.get
      lazy val `expect-ct`                 = HeaderId.dict("expect-ct").toTry.get
      lazy val `host`                      = HeaderId.item("host").toTry.get
      lazy val `keep-alive`                = HeaderId.dict("keep-alive").toTry.get
      lazy val `max-forwards`              = HeaderId.item("max-forwards").toTry.get
      lazy val `origin`                    = HeaderId.item("origin").toTry.get
      lazy val `pragma`                    = HeaderId.dict("pragma").toTry.get
      lazy val `prefer`                    = HeaderId.dict("prefer").toTry.get
      lazy val `preference-applied`        = HeaderId.dict("preference-applied").toTry.get
      lazy val `retry-after`               = HeaderId.item("ret:wry-after").toTry.get
      lazy val `sec-websocket-extensions`  = HeaderId.list("sec-websocket-extensions").toTry.get
      lazy val `sec-websocket-protocol`    = HeaderId.list("sec-websocket-protocol").toTry.get
      lazy val `sec-websocket-version`     = HeaderId.item("sec-websocket-version").toTry.get
      lazy val `server-timing`             = HeaderId.list("server-timing").toTry.get
      lazy val `surrogate-control`         = HeaderId.dict("surrogate-control").toTry.get
      lazy val `te`: HeaderId.ListId       = HeaderId.list("te").toTry.get
      lazy val `timing-allow-origin`       = HeaderId.list("timing-allow-origin").toTry.get
      lazy val `trailer`                   = HeaderId.list("trailer").toTry.get
      lazy val `transfer-encoding`         = HeaderId.list("transfer-encoding").toTry.get
      lazy val `upgrade-insecure-requests` = HeaderId.item("upgrade-insecure-requests").toTry.get
      lazy val `vary`                      = HeaderId.list("vary").toTry.get
      lazy val `x-content-type-options`    = HeaderId.item("x-content-type-options").toTry.get
      lazy val `x-frame-options`           = HeaderId.item("x-frame-options").toTry.get
      lazy val `x-xss-protection`          = HeaderId.list("x-xss-protection").toTry.get

   object Request:
      lazy val authorization: HeaderId.OldId = HeaderId("authorization").toTry.get
      // todo: another way to model this would be HeaderId.Item("content-type")
      lazy val `content-type`: HeaderId = HeaderId.item("content-type").toTry.get
      lazy val date: HeaderId.OldId     = HeaderId("date").toTry.get

      /** a list because sometimes multiple lengths are sent! */
      lazy val `content-length` = HeaderId.list("content-length").toTry.get
      lazy val signature        = HeaderId.dict("signature").toTry.get
      lazy val `content-digest` = HeaderId.dict("content-digest").toTry.get

      /** Defined in:
        * [[https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-client-cert-field-03#section-2 httpbis-client-cert]]
        */
      lazy val `client-cert`       = HeaderId.item("client-cert").toTry.get
      lazy val `client-cert-chain` = HeaderId.list("client-cert").toTry.get
      lazy val forwarded           = HeaderId("forwarded").toTry.get
   end Request

   object Response:
      lazy val `accept-post`   = HeaderId.list("accept-post").toTry.get
      lazy val `cache-control` = HeaderId("cache-control").toTry.get

      /** Clients cannot send date headers via JS. Also not really useful as the time is available
        * in the Signature-Input in unix time. see
        * [[https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6 rfc9110]] for handling
        * requirements (from httpbis-retrofit)
        */
      lazy val date: HeaderId.OldId              = HeaderId("date").toTry.get
      lazy val `content-type`: HeaderId.ItemId   = HeaderId.item("content-type").toTry.get
      lazy val `content-length`: HeaderId.ListId = HeaderId.list("content-length").toTry.get

      /** One could create a new parameter to convert to SF-ETAG specified by httpbis-retrofit */
      lazy val etag             = HeaderId("etag").toTry.get
      lazy val signature        = HeaderId.dict("signature").toTry.get
      lazy val `content-digest` = Request.`content-digest`
