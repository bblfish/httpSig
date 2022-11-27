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
import run.cosy.http.auth.SelectorException
import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.{Params, SfString}
import run.cosy.http.messages.Parameters.{nameTk, reqTk}
import run.cosy.http.messages.SelectorFn

import scala.util.{Failure, Success, Try}

trait SelectorFnTyps[F[_], H <: Http]:
   type RequestFn  = SelectorFn[Http.Request[F, H]]
   type ResponseFn = SelectorFn[Http.Response[F, H]]

trait SelectorFns[F[_], H <: Http] extends AtSelectorFns[F, H] with HeaderSelectorFns[F, H]

/** The functions that need to look at the whole HTTP Message */
trait AtSelectorFns[F[_], H <: Http] extends SelectorFnTyps[F, H]:

   def method: RequestFn

   def authority: RequestFn

   /** best not used if not HTTP1.1 */
   def requestTarget: RequestFn

   def path: RequestFn

   def query: RequestFn

   def queryParam(name: Rfc8941.SfString): RequestFn

   // requiring knowing context info on server
   def scheme: RequestFn

   def targetUri: RequestFn

   // on responses
   def status: ResponseFn
end AtSelectorFns

/** The functions that only need to look at the HTTP headers */
trait HeaderSelectorFns[F[_], H <: Http] extends SelectorFnTyps[F, H]:
   def requestHeaders(name: HeaderId): RequestFn

   def responseHeaders(name: HeaderId): ResponseFn
end HeaderSelectorFns
