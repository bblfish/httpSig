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
import run.cosy.http.messages.{AtSelector, ServerContext}

/** the server context may not know the default Host, but it cannot really not know if the server is
  * running http or https...
  */
class ServerContext private (val defaultHost: Option[String], val port: Option[Int], val secure: Boolean)

object ServerContext:
   def apply(defaultHost: String, secure: Boolean, port: Option[Int] = None): ServerContext =
     new ServerContext(Some(defaultHost), port, secure)

   /** If the server wishes the default host to remain unguessable then use this constructor
     */
   def apply(secure: Boolean): ServerContext =
     new ServerContext(None, None, secure)

trait AtComponents[F[_], H <: Http](using ServerContext):

   def method(onReq: Boolean = false): AtSelector[Http.Request[F, H]]
   def authority(onReq: Boolean = false): AtSelector[Http.Request[F, H]]

   /** best not used if not HTTP1.1 */
   def requestTarget(onReq: Boolean = false): AtSelector[Http.Request[F, H]]
   def path(onReq: Boolean = false): AtSelector[Http.Request[F, H]]
   def query(onReq: Boolean = false): AtSelector[Http.Request[F, H]]
   def queryParam(name: String, onReq: Boolean = false): AtSelector[Http.Request[F, H]]

   // requiring knowing context info on server
   def scheme(onReq: Boolean = false): AtSelector[Http.Request[F, H]]
   def targetUri(onReq: Boolean = false): AtSelector[Http.Request[F, H]]

   // on responses
   def status(): AtSelector[Http.Response[F, H]]

end AtComponents
