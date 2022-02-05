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

package run.cosy.http4s

import org.http4s.Headers
import org.typelevel.ci.CIString
import run.cosy.http.Http.{Header, Message}
import run.cosy.http.{Http, HttpOps}

object Http4sTp extends Http:
   override type Message  = org.http4s.Request[?] | org.http4s.Response[?]
   override type Request  = org.http4s.Request[?]
   override type Response = org.http4s.Response[?]

   override type Header = org.http4s.Header.Raw

   given httpOps: HttpOps[Http4sTp.type] with
      type H = Http4sTp.type
      extension (msg: Http.Message[H])
        def headers: Seq[Http.Header[H]] = msg.headers.headers

      extension [R <: Http.Message[H]](msg: R)
         def addHeaders(headers: Seq[Http.Header[H]]): R =
           msg.withHeaders(msg.headers ++ Headers(headers.toList))
             .asInstanceOf[R] // todo: how to get same result without asInstanceOf?

         def addHeader(name: String, value: String): R =
           msg.putHeaders(org.http4s.Header.Raw(CIString(name), value))
             .asInstanceOf[R]
         // this is used in tests
         def removeHeader(name: String): R =
           msg.removeHeader(CIString(name))
             .asInstanceOf[R]

         // used in tests: return the Optional Value
         def headerValue(name: String): Option[String] =
           msg.headers.get(CIString(name)).map { nel =>
             nel.foldLeft("")((str, raw) => str + ", " + raw.value.trim)
           }
