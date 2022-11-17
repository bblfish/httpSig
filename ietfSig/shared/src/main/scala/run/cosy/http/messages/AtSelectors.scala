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
import run.cosy.http.headers.Rfc8941.{Params, SfString}
import run.cosy.http.messages.Component.{nameTk, reqTk}
import run.cosy.http.messages.{AtSelector, ServerContext}

import scala.util.Try

/** the server context may not know the default Host, but it cannot really not know if the server is
  * running http or https...
  */
class ServerContext private (val defaultHost: Option[String], val secure: Boolean, val port: Int)

object ServerContext:
   def apply(defaultHost: String, secure: Boolean): ServerContext =
     new ServerContext(Some(defaultHost), secure, if secure then 443 else 80)

   /** If the server wishes the default host to remain unguessable then use this constructor
     */
   def apply(secure: Boolean): ServerContext =
     new ServerContext(None, secure, if secure then 443 else 80)

   def apply(defaultHost: String, secure: Boolean, port: Int) =
     new ServerContext(Some(defaultHost), secure, port)
end ServerContext

class AtSelectors[F[_], H <: Http](at: AtComponents[F, H])(using
    sc: ServerContext
):
   import scala.language.implicitConversions

   def method(onReq: Boolean = false): AtSelector[Http.Request[F, H]] =
     at.`@method`(onReq).get

   def authority(onReq: Boolean = false): AtSelector[Http.Request[F, H]] =
     at.`@authority`(onReq).get

   /** best not used if not HTTP1.1 */
   def requestTarget(onReq: Boolean = false): AtSelector[Http.Request[F, H]] =
     at.`@request-target`(onReq).get

   def path(onReq: Boolean = false): AtSelector[Http.Request[F, H]] =
     at.`@path`(onReq).get

   def query(onReq: Boolean = false): AtSelector[Http.Request[F, H]] =
     at.`@query`(onReq).get

   def queryParam(name: String, onReq: Boolean = false): AtSelector[Http.Request[F, H]] =
     at.`@query-param`(toP(onReq) + (nameTk -> SfString(name))).get

   protected def toP(onReq: Boolean): Params =
     if onReq == true then Params(reqTk -> true) else Params()

   // requiring knowing context info on server
   def scheme(onReq: Boolean = false): AtSelector[Http.Request[F, H]] =
     at.`@scheme`(onReq).get

   def targetUri(onReq: Boolean = false): AtSelector[Http.Request[F, H]] =
     at.`@target-uri`(onReq).get

   // on responses
   def status(): AtSelector[Http.Response[F, H]] =
     at.`@status`().get

   protected given Conversion[Boolean, Rfc8941.Params] = toP

end AtSelectors
