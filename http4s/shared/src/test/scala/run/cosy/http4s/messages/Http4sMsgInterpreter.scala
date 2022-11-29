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

package run.cosy.http4s.messages

import run.cosy.http.Http
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT
import cats.effect.IO
import org.http4s.Header
import org.typelevel.ci.CIString
import run.cosy.http.messages.TestHttpMsgInterpreter
import scodec.bits.ByteVector

class Http4sMsgInterpreter[F[_]]
    extends run.cosy.http.messages.TestHttpMsgInterpreter[F, Http4sTp.HT]:

   import run.cosy.http.messages.HttpMessageDB.{RequestStr, ResponseStr}

   override def asRequest(request: RequestStr): Http.Request[F, HT] =
     request.str.split(Array('\n', '\r')).toList match
        case head :: tail =>
          head.split("\\s+").nn.toList match
             case methd :: path :: httpVersion :: Nil =>
               val Right(m)     = org.http4s.Method.fromString(methd.nn): @unchecked
               val Right(p)     = org.http4s.Uri.fromString(path.nn): @unchecked
               val Right(v)     = org.http4s.HttpVersion.fromString(httpVersion.nn): @unchecked
               val (rawH, body) = parseHeaders(tail)
               import org.http4s.Header.ToRaw.{given, *}
               // we can ignore the body here, since that is actually not relevant to signing
               org.http4s.Request[F](m, p, v, org.http4s.Headers(rawH))
                 .asInstanceOf[Http.Request[F, HT]] // <- todo: why needed?
             case _ => throw new Exception("Badly formed HTTP Request Command '" + head + "'")
        case _ => throw new Exception("Badly formed HTTP request")

   override def asResponse(response: ResponseStr): Http.Response[F, HT] =
     response.str.split(Array('\n', '\r')).nn.toList match
        case head :: tail =>
          head.split("\\s+").nn.toList match
             case httpVersion :: statusCode :: statusCodeStr :: Nil =>
               val Right(status) =
                 org.http4s.Status.fromInt(Integer.parseInt(statusCode.nn)): @unchecked
               val Right(version) = org.http4s.HttpVersion.fromString(httpVersion.nn): @unchecked
               val (rawH, body)   = parseHeaders(tail)
               import org.http4s.Header.ToRaw.{given, *}
               val e = body match
                  case "" => org.http4s.Entity.Empty
                  case _  => org.http4s.Entity.Strict(ByteVector.encodeAscii(body).toOption.get)
               org.http4s.Response[F](status, version, org.http4s.Headers(rawH), e)
                 .asInstanceOf[Http.Response[F, HT]] // <- todo: why needed?
             case _ => throw new Exception("Badly formed HTTP Response Command '" + head + "'")
        case _ => throw new Exception("Badly formed HTTP request")

   private def parseHeaders(nonMethodLines: List[String]): (List[Header.Raw], String) =
      val (headers, body) = TestHttpMsgInterpreter.headersAndBody(nonMethodLines)
      val hds             = headers.map { case (h, v) => Header.Raw(CIString(h), v) }
      (hds, body)

end Http4sMsgInterpreter
