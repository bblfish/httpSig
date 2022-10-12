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
   override type Message[F[_]]  =  org.http4s.Request[F] | org.http4s.Response[F]
   override type Request[F[_]]  =  org.http4s.Request[F]
   override type Response[F[_]] =  org.http4s.Response[F]

   override type Header = org.http4s.Header.Raw

   given hOps: HttpOps[HT] with

      extension [F[_]](msg: Http.Message[F, HT])
        def headers: Seq[Http.Header[HT]] =
          // the asInstanceOf is needed here to avoid infinite recursion on `headers`
          msg.asInstanceOf[org.http4s.Message[F]].headers.headers

      extension [F[_], R <: Http.Message[F, HT]](msg: R)
         def addHeaders(headers: Seq[Http.Header[HT]]): R =
            val m = msg.asInstanceOf[org.http4s.Message[F]]
            m.withHeaders((m.headers ++ Headers(headers)))
              .asInstanceOf[R]

         def addHeader(name: String, value: String): R =
            val m = msg.asInstanceOf[org.http4s.Message[F]]
            m.putHeaders(org.http4s.Header.Raw(CIString(name), value))
              .asInstanceOf[R]
         // this is used in tests
         def removeHeader(name: String): R =
            val m = msg.asInstanceOf[org.http4s.Message[F]]
            m.removeHeader(CIString(name))
              .asInstanceOf[R]

         // used in tests: return the Optional Value
         def headerValue(name: String): Option[String] =
            val m = msg.asInstanceOf[org.http4s.Message[F]]
            m.headers.get(CIString(name)).map { nel =>
              nel.foldLeft("")((str, raw) => str + ", " + raw.value.trim)
            }
end Http4sTp
