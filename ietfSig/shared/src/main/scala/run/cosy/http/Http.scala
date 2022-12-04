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

package run.cosy.http

import run.cosy.http.Http.Header
import run.cosy.http.headers.{SignatureInputMatcher, SignatureMatcher}

trait Http:
   http =>
   type HT = http.type
   type Message[F[_]] <: Matchable
   type Request[F[_]] <: Message[F]
   type Response[F[_]] <: Message[F]
   type Header <: Matchable

   given hOps: HttpOps[HT]
end Http

trait HttpOps[H <: Http]:
   import Http.*

   val Signature: SignatureMatcher[H]
   val `Signature-Input`: SignatureInputMatcher[H]

   /** extensions needed to abstract across HTTP implementations for our purposes */
   extension [F[_]](msg: Http.Message[F, H])
     def headers: Seq[Http.Header[H]]

   extension [F[_], R <: Http.Message[F, H]](msg: R)
      def addHeaders(headers: Seq[Http.Header[H]]): R
      // here we do really add a header to existing ones.
      // note http4s
      def addHeader(name: String, value: String): R
      // this is used in tests
      def removeHeader(name: String): R
      // used in tests: return the Optional Value
      def headerValue(name: String): Option[String]
end HttpOps

object Http:
   type Message[F[_], H <: Http] = Request[F, H] | Response[F, H]

   type Request[F[_], H <: Http] =
     H match
        case GetRequest[F, req] => req

   type Response[F[_], H <: Http] =
     H match
        case GetResponse[F, res] => res

   type Header[H <: Http] <: Matchable =
     H match
        case GetHeader[res] => res

//   private type GetMessage[F[_], M]       = Http { type Message[F] = M }
   private type GetRequest[F[_], R]       = Http { type Request[F] = R }
   private type GetResponse[F[_], R]      = Http { type Response[F] = R }
   private type GetHeader[R <: Matchable] = Http { type Header = R }
end Http
