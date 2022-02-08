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

package run.cosy.http4s.auth

import run.cosy.http.Http.{Message, Request, Response}
import run.cosy.http.auth.{HttpMessageSigningSuite, MessageSignature, SigningSuiteHelpers}
import run.cosy.http.headers.{DictSelector, HeaderSelector, MessageSelectors}
import run.cosy.http4s.Http4sTp
import org.http4s.{Header, Method, ParseResult, Uri}
import org.typelevel.ci.CIString
import run.cosy.http4s.headers.{BasicHeaderSelector, Http4sDictSelector, Http4sMessageSelectors}
import run.cosy.http.headers.MessageSelector

trait Http4sMessageSigningSuite extends HttpMessageSigningSuite[Http4sTp.type]:
   type H = Http4sTp.type
   override val selectorsSecure: MessageSelectors[H] =
     new Http4sMessageSelectors(true, Uri.Authority(None, Uri.RegName("bblfish.net"), None), 443)
   override val selectorsInSecure: MessageSelectors[H] =
     new Http4sMessageSelectors(false, Uri.Authority(None, Uri.RegName("bblfish.net"), None), 80)
   override val messageSignature: MessageSignature[H] = run.cosy.http4s.auth.Http4sMessageSignature

   override def toRequest(request: HttpMessage): Request[H] =
     request.split(Array('\n', '\r')).toList match
        case head :: tail =>
          head.split("\\s+").nn.toList match
             case methd :: path :: httpVersion :: Nil =>
               val Right(m) = org.http4s.Method.fromString(methd.nn): @unchecked
               val Right(p) = org.http4s.Uri.fromString(path.nn): @unchecked
               val Right(v) = org.http4s.HttpVersion.fromString(httpVersion.nn): @unchecked
               val rawH: scala.List[org.http4s.Header.Raw] = parseHeaders(tail)
               import org.http4s.Header.ToRaw.{given, *}
               // we can ignore the body here, since that is actually not relevant to signing
               org.http4s.Request(m, p, v, org.http4s.Headers(rawH))
             case _ => throw new Exception("Badly formed HTTP Request Command '" + head + "'")
        case _ => throw new Exception("Badly formed HTTP request")

   override def toResponse(response: HttpMessage): Response[H] =
     response.split(Array('\n', '\r')).nn.toList match
        case head :: tail =>
          head.split("\\s+").nn.toList match
             case httpVersion :: statusCode :: statusCodeStr :: Nil =>
               val Right(status) =
                 org.http4s.Status.fromInt(Integer.parseInt(statusCode.nn)): @unchecked
               val Right(version) = org.http4s.HttpVersion.fromString(httpVersion.nn): @unchecked
               val rawH: scala.List[org.http4s.Header.Raw] = parseHeaders(tail)
               import org.http4s.Header.ToRaw.{given, *}
               org.http4s.Response(status, version, org.http4s.Headers(rawH))
             case _ => throw new Exception("Badly formed HTTP Response Command '" + head + "'")
        case _ => throw new Exception("Badly formed HTTP request")

   private def parseHeaders(nonMethodLines: List[String]) =
      val (headers, body) = // we loose the body for the moment
         val i = nonMethodLines.indexOf("")
         if i < 0 then (nonMethodLines, List())
         else nonMethodLines.splitAt(i)
      val foldedHeaders = headers.foldLeft(List[String]()) { (ll, str) =>
        if str.contains(':') then str :: ll
        else (ll.head.nn.trim.nn + " " + str.trim.nn) :: ll.tail
      }
      val rawH: List[Header.Raw] = foldedHeaders.map(line => line.splitAt(line.indexOf(':'))).map {
        case (h, v) => Header.Raw(CIString(h.trim.nn), v.tail.trim.nn)
      }
      rawH.reverse
   override val `x-example`: MessageSelector[Request[H]] =
     new BasicHeaderSelector[Request[H]]:
        override val lowercaseName: String = "x-example"

   override val `x-empty-header`: MessageSelector[Request[H]] =
     new BasicHeaderSelector[Request[H]]:
        override val lowercaseName: String = "x-empty-header"

   override val `x-ows-header`: MessageSelector[Request[H]] =
     new BasicHeaderSelector[Request[H]]:
        override val lowercaseName: String = "x-ows-header"

   override val `x-obs-fold-header`: MessageSelector[Request[H]] =
     new BasicHeaderSelector[Request[H]]:
        override val lowercaseName: String = "x-obs-fold-header"

   override val `x-dictionary`: DictSelector[Message[H]] =
     new Http4sDictSelector[Message[H]]:
        override val lowercaseName: String = "x-dictionary"

   test("§2.2.6. Request Target for CONNECT (works for Http4s but not Akka)") {
     // this does not work for AKKA because akka returns //www.example.com:80
     val reqCONNECT = toRequest(`§2.2.6_CONNECT`)
     assertEquals(
       selectorsSecure.`@request-target`.signingString(reqCONNECT),
       `request-target`("www.example.com:80")
     )
   }
end Http4sMessageSigningSuite
