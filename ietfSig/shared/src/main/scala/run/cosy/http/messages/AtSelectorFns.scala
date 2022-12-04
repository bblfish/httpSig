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

trait ReqFns[F[_], H <: Http] extends AtReqSelectorFns[F, H] with RequestHeaderSelectorFns[F, H]
trait ResFns[F[_], H <: Http] extends AtResSelectorFns[F, H] with ResponseHeaderSelectorFns[F, H]

/** The functions that need to look at the whole HTTP Message */
trait AtReqSelectorFns[F[_], H <: Http]:
   type RequestFn = SelectorFn[Http.Request[F, H]]

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
end AtReqSelectorFns

trait AtResSelectorFns[F[_], H <: Http]:
   type ResponseFn = SelectorFn[Http.Response[F, H]]

   // on responses
   def status: ResponseFn

/** The functions that only need to look at the HTTP headers */
trait RequestHeaderSelectorFns[F[_], H <: Http]:
   type RequestFn = SelectorFn[Http.Request[F, H]]
   def requestHeaders(name: HeaderId): RequestFn

trait ResponseHeaderSelectorFns[F[_], H <: Http]:
   type ResponseFn = SelectorFn[Http.Response[F, H]]
   def responseHeaders(name: HeaderId): ResponseFn
