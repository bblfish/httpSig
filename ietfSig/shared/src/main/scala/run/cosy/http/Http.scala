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

   /** F is the functor type (monad actually) that gives access to the content or entity. It is
     * explicit in Http4s where it is usually IO, but it Akka it is implicit, and probably a Future.
     * So this type is going to be needed if we want to collect the tail headers that appear after
     * the header is transmitted. In any case this is important when making ... todo: find the
     * reason they were introduced as previsously and build a test to make sure this change is
     * functioning. probably trying to fix what this commit fixed:
     * https://github.com/bblfish/SolidCtrlApp/pull/1/commits/992a6feb1c4cc1d01880371dc95cb87fcc252d4a
     * I think it was related to using http4s requests and then trying to work with them as Http
     * objects... Somehow the F of the IO got lost
     */
   type F[_]
   type HT = http.type
   type Message <: Matchable
   type Request <: Message
   type Response <: Message
   type Header <: Matchable

   given hOps: HttpOps[HT]
end Http

trait HttpOps[H <: Http]:
   import Http.*

   val Signature: SignatureMatcher[H]
   val `Signature-Input`: SignatureInputMatcher[H]

   /** extensions needed to abstract across HTTP implementations for our purposes */
   extension (msg: Http.Message[H])
     def headerSeq: Seq[Http.Header[H]]

   extension [R <: Http.Message[H]](msg: R)
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
   type Message[H <: Http] = Request[H] | Response[H]

   type Request[H <: Http] =
     H match
        case GetRequest[req] => req

   type Response[H <: Http] =
     H match
        case GetResponse[res] => res

   type Header[H <: Http] <: Matchable =
     H match
        case GetHeader[res] => res

//   private type GetMessage[F[_], M]       = Http { type Message[F] = M }
   private type GetRequest[R]             = Http { type Request = R }
   private type GetResponse[R]            = Http { type Response = R }
   private type GetHeader[R <: Matchable] = Http { type Header = R }
end Http
