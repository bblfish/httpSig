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

import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.HttpMethods.*
import akka.http.scaladsl.model.headers.*
import cats.Id
import run.cosy.akka.http.AkkaTp
import run.cosy.akka.http.AkkaTp.HT
import run.cosy.http.Http
import run.cosy.http.Http.Request
import run.cosy.http.messages.{MessageInterpreterError, Platform, HttpMessageDB as DB}

import scala.language.implicitConversions
import run.cosy.http.utils.StringUtils.rfc8792single

object AkkaMsgInterpreter extends run.cosy.http.messages.HttpMsgInterpreter[Id, AkkaTp.HT]:
   val VerticalTAB: Char = "\u000B".head

   override def asRequest(header: DB.RequestStr): Http.Request[Id, AkkaTp.HT] =
     header match
        case DB.`§2.1_HeaderField` => HttpRequest(
            HttpMethods.GET,
            Uri("/xyz"),
            Seq(
              RawHeader("Host", "www.example.com"),
              RawHeader("Date", "Sat, 07 Jun 2014 20:51:35 GMT"),
              RawHeader("X-OWS-Header", """   Leading and trailing whitespace.      """),
              RawHeader(
                "X-Obs-Fold-Header",
                s"Obsolete line folding.  "
              ), // <- we have to assume the removal of absolete line folding has alrady occurred
              RawHeader("Cache-Control", "max-age=60"),
              RawHeader("X-Empty-Header", ""),
              RawHeader("Cache-Control", "    must-revalidate"),
              RawHeader("Example-Dict", " a=1,     b=2;x=1;y=2,   c=(a   b   c)   "),
              RawHeader("Example-Header", "value, with, lots"),
              RawHeader("Example-Dict", "d"),
              RawHeader("Example-Header", "of, commas")
            )
          )
        case DB.`2.2.1_Method_POST` => HttpRequest(
            HttpMethods.POST,
            Uri("/path?param=value"),
            headers = Seq(Host("www.example.com"))
          )
        case DB.`2.2.1_Method_GET` => HttpRequest(
            HttpMethods.GET,
            Uri("/path?param=value"),
            headers = Seq(Host("www.example.com"))
          )
        case DB.`2.2.5_GET_with_LongURL` => HttpRequest(
            HttpMethods.GET,
            Uri("https://test.example.com/path?param=value&param=another")
          )
        case DB.`2.2.5_OPTIONS_4akka` => HttpRequest(
            HttpMethods.OPTIONS,
            Uri("https://www.example.com:443"),
            protocol = akka.http.scaladsl.model.HttpProtocols.`HTTP/1.1`
          )
        case DB.`2.2.5_OPTIONS` =>
          throw MessageInterpreterError(Platform.Akka, "OPTIONS * cannot be built in Akka")

        case DB.`2.2.5_CONNECT` => throw MessageInterpreterError(
            Platform.Akka,
            "CONNECT example.com:80 cannot be built in Akka"
          )
        case DB.`2.2.7_Query` => HttpRequest(
            HttpMethods.POST,
            Uri("/path?param=value&foo=bar&baz=bat%2Dman"),
            Seq(RawHeader("Host", "www.example.com"))
          )
        case DB.`2.2.7_Query_String` => HttpRequest(
            HttpMethods.HEAD,
            Uri("/path?queryString")
          )
        case DB.`2.2.8_Query_Parameters` => HttpRequest(
            HttpMethods.GET,
            Uri("/path?param=value&foo=bar&baz=batman&qux=")
          )
        case DB.`2.5_Post_Ex` => HttpRequest(
            HttpMethods.POST,
            Uri("/foo?param=Value&Pet=dog"),
            Seq(
              Host("example.com"),
              RawHeader("Date", "Tue, 20 Apr 2021 02:07:55 GMT"),
              RawHeader(
                "Content-Digest",
                "sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T" +
                  "aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:"
              ),
              RawHeader(
                "Signature-Input",
                """sig1=("@method" "@authority" "@path" \
                   "content-digest" "content-length" "content-type")\
                   ;created=1618884473;keyid="test-key-rsa-pss"""".rfc8792single
              ),
              RawHeader(
                "Signature",
                """sig1=:HIbjHC5rS0BYaa9v4QfD4193TORw7u9edguPh0AW3dMq9WImrl\
                |FrCGUDih47vAxi4L2YRZ3XMJc1uOKk/J0ZmZ+wcta4nKIgBkKq0rM9hs3CQyxXGxH\
                |LMCy8uqK488o+9jrptQ+xFPHK7a9sRL1IXNaagCNN3ZxJsYapFj+JXbmaI5rtAdSf\
                |SvzPuBCh+ARHBmWuNo1UzVVdHXrl8ePL4cccqlazIJdC4QEjrF+Sn4IxBQzTZsL9y\
                |9TP5FsZYzHvDqbInkTNigBcE9cKOYNFCn4D/WM7F6TNuZO9EgtzepLWcjTymlHzK7\
                |aXq6Am6sfOrpIC49yXjj3ae6HRalVc/g==:""".rfc8792single
              )
            ),
            entity = HttpEntity(ContentTypes.`application/json`, """{"hello": "world"}""")
          )

   override def asResponse(header: DB.ResponseStr): Http.Response[Id, AkkaTp.HT] =
     header match
        case DB.`2.2.9_Status_Code` => HttpResponse(
            StatusCodes.OK,
            Seq(RawHeader("Date", "Fri, 26 Mar 2010 00:05:00 GMT"))
          )

end AkkaMsgInterpreter
