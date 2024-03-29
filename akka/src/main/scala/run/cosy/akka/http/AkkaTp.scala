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

package run.cosy.akka.http

import akka.http.scaladsl.model
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpHeader, HttpMessage, HttpRequest, HttpResponse}
import cats.Id
import cats.effect.IO
import run.cosy.http.headers.{SignatureInputMatcher, SignatureMatcher}
import run.cosy.http.{Http, HttpOps}

object AkkaTp extends Http:
   override type F[A]     = cats.Id[A]
   override type Message  = model.HttpMessage
   override type Request  = model.HttpRequest
   override type Response = model.HttpResponse
   override type Header   = model.HttpHeader

//   given Conversion[model.HttpRequest, Http.Request[IO, HT]] with
//      def apply(req: HttpRequest): Http.Request[IO, HT] = req.asInstanceOf[Http.Request[IO, HT]]
//   given Conversion[model.HttpResponse, Http.Response[IO, HT]] with
//      def apply(req: HttpResponse): Http.Response[IO, HT] = req.asInstanceOf[Http.Response[IO, HT]]

   given hOps: HttpOps[HT] with

      extension (msg: Http.Message[HT])
        def headerSeq: Seq[Http.Header[HT]] =
           val m = msg.asInstanceOf[model.HttpMessage]
           m.headers

      override val Signature: SignatureMatcher[HT] = run.cosy.akka.http.headers.Signature
      override val `Signature-Input`: SignatureInputMatcher[HT] =
        run.cosy.akka.http.headers.`Signature-Input`

      extension [R <: Http.Message[HT]](msg: R)
         def addHeaders(headers: Seq[Http.Header[HT]]): R =
            // don't know how to get rid of the  asInstanceOf
            val m      = msg.asInstanceOf[HttpMessage]
            val newreq = m.withHeaders(headers.map(_.asInstanceOf[HttpHeader]) ++ m.headers)
            newreq.asInstanceOf[R]

         def addHeader(name: String, value: String): R =
            val m = msg.asInstanceOf[HttpMessage]
            m.withHeaders(m.headers.prepended(RawHeader(name, value))).asInstanceOf[R]

         def removeHeader(name: String): R =
            val m = msg.asInstanceOf[model.HttpMessage]
            m.removeHeader(name).asInstanceOf[R]

         def headerValue(name: String): Option[String] =
            val m = msg.asInstanceOf[HttpMessage]
            name.toLowerCase(java.util.Locale.ENGLISH) match
               case "content-type" =>
                 if m.entity.isKnownEmpty then None
                 else Option(m.entity.contentType.toString)
               case "content-length" =>
                 if m.entity.isKnownEmpty then Some("0")
                 else m.entity.contentLengthOption.map(_.toString)
               // todo: need to adapt for multiple headers
               case _ =>
                 import scala.jdk.OptionConverters.*
                 m.getHeader(name).toScala.nn.map(_.value.nn)

end AkkaTp
