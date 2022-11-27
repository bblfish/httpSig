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

   /** Header types as speccified
     * https://greenbytes.de/tech/webdav/draft-ietf-httpbis-retrofit-latest.html#compatible
     */
   object retrofit:
      lazy val `accept`          = HeaderId.list("accept").get
      lazy val `accept-encoding` = HeaderId.list("accept-encoding").get
      lazy val `accept-language` = HeaderId.list("accept-language").get
      lazy val `accept-patch`    = HeaderId.list("accept-patch").get
      lazy val `accept-post`     = HeaderId.list("accept-post").get
      lazy val `accept-ranges`   = HeaderId.list("accept-ranges").get
      lazy val `access-control-allow-credentials` =
        HeaderId.item("access-control-allow-credentials").get
      lazy val `access-control-allow-headers` =
        HeaderId.list("access-control-allow-headers").get
      lazy val `access-control-allow-methods` =
        HeaderId.list("access-control-allow-methods").get
      lazy val `access-control-allow-origin` = HeaderId.item("access-control-allow-origin").get
      lazy val `access-control-expose-headers` =
        HeaderId.list("access-control-expose-headers").get
      lazy val `access-control-max-age` = HeaderId.item("access-control-max-age").get
      lazy val `access-control-request-headers` =
        HeaderId.list("access-control-request-headers").get
      lazy val `access-control-request-method` =
        HeaderId.item("access-control-request-method").get
      lazy val `age`: HeaderId.ItemId     = HeaderId.item("age").get
      lazy val `allow`: HeaderId.ListId   = HeaderId.list("allow").get
      lazy val `alpn`: HeaderId.ListId    = HeaderId.list("alpn").get
      lazy val `alt-svc`: HeaderId.DictId = HeaderId.dict("alt-svc").get
      lazy val `alt-used`                 = HeaderId.item("alt-used").get
      lazy val `cache-control`            = HeaderId.dict("cache-control").get
      lazy val `cdn-loop`                 = HeaderId.list("cdn-loop").get
      lazy val `clear-site-data`          = HeaderId.list("clear-site-data").get
      lazy val `connection`               = HeaderId.list("connection").get
      lazy val `content-encoding`         = HeaderId.list("content-encoding").get
      lazy val `content-language`         = HeaderId.list("content-language").get
      lazy val `content-length`           = HeaderId.list("content-length").get
      lazy val `content-type`             = HeaderId.item("content-type").get
      lazy val `cross-origin-resource-policy` =
        HeaderId.item("cross-origin-resource-policy").get
      lazy val `dnt`                       = HeaderId.item("dnt").get
      lazy val `expect`                    = HeaderId.dict("expect").get
      lazy val `expect-ct`                 = HeaderId.dict("expect-ct").get
      lazy val `host`                      = HeaderId.item("host").get
      lazy val `keep-alive`                = HeaderId.dict("keep-alive").get
      lazy val `max-forwards`              = HeaderId.item("max-forwards").get
      lazy val `origin`                    = HeaderId.item("origin").get
      lazy val `pragma`                    = HeaderId.dict("pragma").get
      lazy val `prefer`                    = HeaderId.dict("prefer").get
      lazy val `preference-applied`        = HeaderId.dict("preference-applied").get
      lazy val `retry-after`               = HeaderId.item("ret:wry-after").get
      lazy val `sec-websocket-extensions`  = HeaderId.list("sec-websocket-extensions").get
      lazy val `sec-websocket-protocol`    = HeaderId.list("sec-websocket-protocol").get
      lazy val `sec-websocket-version`     = HeaderId.item("sec-websocket-version").get
      lazy val `server-timing`             = HeaderId.list("server-timing").get
      lazy val `surrogate-control`         = HeaderId.dict("surrogate-control").get
      lazy val `te`: HeaderId.ListId       = HeaderId.list("te").get
      lazy val `timing-allow-origin`       = HeaderId.list("timing-allow-origin").get
      lazy val `trailer`                   = HeaderId.list("trailer").get
      lazy val `transfer-encoding`         = HeaderId.list("transfer-encoding").get
      lazy val `upgrade-insecure-requests` = HeaderId.item("upgrade-insecure-requests").get
      lazy val `vary`                      = HeaderId.list("vary").get
      lazy val `x-content-type-options`    = HeaderId.item("x-content-type-options").get
      lazy val `x-frame-options`           = HeaderId.item("x-frame-options").get
      lazy val `x-xss-protection`          = HeaderId.list("x-xss-protection").get

   object Request:
      lazy val authorization: HeaderId.OldId = HeaderId("authorization").get
      // todo: another way to model this would be HeaderId.Item("content-type")
      lazy val `content-type`: HeaderId = HeaderId.item("content-type").get

      /** a list because sometimes multiple lengths are sent! */
      lazy val `content-length` = HeaderId.list("content-length").get
      lazy val signature        = HeaderId.dict("signature").get
      lazy val `content-digest` = HeaderId.dict("content-digest").get

      /** Defined in:
        * [[https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-client-cert-field-03#section-2 httpbis-client-cert]]
        */
      lazy val `client-cert`       = HeaderId.item("client-cert").get
      lazy val `client-cert-chain` = HeaderId.list("client-cert").get
      lazy val forwarded           = HeaderId("forwarded").get
   end Request

   object Response:
      lazy val `accept-post`   = HeaderId.list("accept-post").get
      lazy val `cache-control` = HeaderId("cache-control").get

      /** Clients cannot send date headers via JS. Also not really useful as the time is available
        * in the Signature-Input in unix time. see
        * [[https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6 rfc9110]] for handling
        * requirements (from httpbis-retrofit)
        */
      lazy val date: HeaderId.OldId              = HeaderId("date").get
      lazy val `content-type`: HeaderId.ItemId   = HeaderId.item("content-type").get
      lazy val `content-length`: HeaderId.ListId = HeaderId.list("content-length").get

      /** One could create a new parameter to convert to SF-ETAG specified by httpbis-retrofit */
      lazy val etag             = HeaderId("etag").get
      lazy val signature        = HeaderId.dict("signature").get
      lazy val `content-digest` = Request.`content-digest`
