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

import cats.effect.IO
import org.http4s as h4
import org.http4s.Headers
import org.typelevel.ci.CIString
import run.cosy.http.Http.{Header, Message}
import run.cosy.http.headers.{SignatureInputMatcher, SignatureMatcher}
import run.cosy.http.{Http, HttpOps}

object Http4sTp extends Http:
   override type F[A]     = cats.effect.IO[A]
   override type Message  = org.http4s.Request[F] | org.http4s.Response[F]
   override type Request  = org.http4s.Request[F]
   override type Response = org.http4s.Response[F]

   override type Header = org.http4s.Header.Raw

   given hOps: HttpOps[HT] with

      override val Signature: SignatureMatcher[HT] = run.cosy.http4s.headers.Signature
      override val `Signature-Input`: SignatureInputMatcher[HT] =
        run.cosy.http4s.headers.`Signature-Input`

      extension (msg: Http.Message[HT])
        def headerSeq: Seq[Http.Header[HT]] =
          // the asInstanceOf is needed here to avoid infinite recursion on `headers`
          msg.asInstanceOf[org.http4s.Message[F]].headers.headers

      extension [R <: Http.Message[HT]](msg: R)
         def addHeaders(headers: Seq[Http.Header[HT]]): R =
            val m = msg.asInstanceOf[org.http4s.Message[F]]
            m.withHeaders((m.headers.transform(l => l ++ headers)))
              .asInstanceOf[R]

         def addHeader(name: String, value: String): R =
            val m = msg.asInstanceOf[org.http4s.Message[F]]
            m.putHeaders(h4.Header.Raw(CIString(name), value))
              .asInstanceOf[R]
         // this is used in tests
         def removeHeader(name: String): R =
            val m = msg.asInstanceOf[h4.Message[F]]
            m.removeHeader(CIString(name))
              .asInstanceOf[R]

         // used in tests: return the Optional Value
         def headerValue(name: String): Option[String] =
            val m = msg.asInstanceOf[h4.Message[F]]
            m.headers.get(CIString(name)).map { nel =>
              nel.foldLeft("")((str, raw) => str + ", " + raw.value.trim)
            }
end Http4sTp
