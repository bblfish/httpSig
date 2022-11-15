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

import run.cosy.http.Http

case class ServerContext(defaultHost: String, secure: Boolean)

trait AtComponents[F[_], H <: Http](using ServerContext):

   def method(onReq: Boolean = false): AtSelector[Http.Request[F, H]]
   def authority(onReq: Boolean = false): AtSelector[Http.Request[F, H]]
   def requestTarget(onReq: Boolean = false): AtSelector[Http.Request[F, H]]
   def path(onReq: Boolean = false): AtSelector[Http.Request[F, H]]
   def query(onReq: Boolean = false): AtSelector[Http.Request[F, H]]
   def queryParam(name: String, onReq: Boolean = false): AtSelector[Http.Request[F, H]]
   
   // requiring knowing context info on server
   def scheme(onReq: Boolean)(using ServerContext): AtSelector[Http.Request[F, H]]
   def targetUri(onReq: Boolean = false)(using ServerContext): AtSelector[Http.Request[F, H]]
  
   // on responses
   def status(): AtSelector[Http.Response[F, H]]

end AtComponents
