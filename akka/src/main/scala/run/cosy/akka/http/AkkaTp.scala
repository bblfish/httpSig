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
import akka.http.scaladsl.model.{HttpHeader, HttpMessage, HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.RawHeader
import run.cosy.http.{Http, HttpOps}
import akka.http.scaladsl.model as ak
import cats.effect.IO

object AkkaTp extends Http:
   override type Message[F[_]]  = model.HttpMessage
   override type Request[F[_]]  = model.HttpRequest
   override type Response[F[_]] = model.HttpResponse
   override type Header         = model.HttpHeader

   given Conversion[model.HttpRequest, Http.Request[IO,H4]] with
     def apply(req: HttpRequest): Http.Request[IO,H4] = req.asInstanceOf[Http.Request[IO,H4]]
   given Conversion[model.HttpResponse, Http.Response[IO,H4]] with
     def apply(req: HttpResponse): Http.Response[IO,H4] = req.asInstanceOf[Http.Response[IO,H4]]

   given httpOps: HttpOps[H4] with

      extension [F[_]](msg: Http.Message[F, H4])
        def headers: Seq[Http.Header[H4]] =
           val m = msg.asInstanceOf[ak.HttpMessage]
           m.headers

      extension [F[_], R <: Http.Message[F, H4]](msg: R)
         def addHeaders(headers: Seq[Http.Header[H4]]): R =
            // don't know how to get rid of the  asInstanceOf
            val m      = msg.asInstanceOf[HttpMessage]
            val newreq = m.withHeaders(headers.map(_.asInstanceOf[HttpHeader]) ++ m.headers)
            newreq.asInstanceOf[R]

         def addHeader(name: String, value: String): R =
            val m = msg.asInstanceOf[HttpMessage]
            m.withHeaders(m.headers.prepended(RawHeader(name, value))).asInstanceOf[R]

         def removeHeader(name: String): R =
            val m = msg.asInstanceOf[ak.HttpMessage]
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
