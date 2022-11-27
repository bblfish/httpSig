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

package run.cosy.akka.http.messages

import akka.http.scaladsl.model.{HttpMessage, HttpRequest, HttpResponse}
import cats.Id
import cats.data.NonEmptyList
import run.cosy.akka.http.AkkaTp
import run.cosy.akka.http.AkkaTp.HT
import run.cosy.http.Http
import run.cosy.http.Http.{Request, Response}
import run.cosy.http.auth.{AttributeException, HTTPHeaderParseException, SelectorException}
import run.cosy.http.headers.Rfc8941.{Param, Params}
import run.cosy.http.headers.{ParsingException, Rfc8941}
import run.cosy.http.messages.*

import java.nio.charset.StandardCharsets
import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.HttpHeader

class SelectorFnsAkka(using sc: ServerContext)
    extends SelectorFns[Id, HT]:

   override val method: RequestFn =
     RequestAkka { req => Success(req.method.value) }

   override val authority: RequestFn =
     RequestAkka { req =>
       Try(
         req.effectiveUri(true, sc.defaultHost.map(Host(_)).getOrElse(Host.empty))
           .authority.toString().toLowerCase(java.util.Locale.ROOT).nn
       )
     }

   /** best not used if not HTTP1.1 according to spec. Does not even work with Akka (see test suite)
     */
   override val requestTarget: RequestFn =
     RequestAkka { req => Success(req.uri.toString()) }

   // raw headers, no interpretation
   override def requestHeaders(headerName: HeaderId): RequestFn =
     RequestAkka { (req: HttpRequest) =>
       SelectorAkka.getHeaders(headerName)(req)
     }

   // raw headers, no interpretation
   override def responseHeaders(headerName: HeaderId): ResponseFn =
     ResponseAkka {
       SelectorAkka.getHeaders(headerName)
     }

   override val path: RequestFn =
     RequestAkka { req =>
       Success(req.uri.path.toString())
     }

   override val query: RequestFn =
     RequestAkka { req =>
       Try(
         req.uri.queryString(StandardCharsets.US_ASCII.nn)
           .map("?" + _).getOrElse("?")
       )
     }

   override def queryParam(name: Rfc8941.SfString): RequestFn =
     RequestAkka { req =>
       req.uri.query().getAll(name.asciiStr).reverse match
          case Nil => Failure(SelectorException(
              s"No query parameter with key ${name} found. Suspicious."
            ))
          case head :: tail => Success(NonEmptyList(head, tail))
     }

   override val scheme: RequestFn =
     RequestAkka { req =>
       Try(req.effectiveUri(
         securedConnection = sc.secure,
         defaultHostHeader = sc.defaultHost.map(Host(_)).getOrElse(Host.empty)
       ).scheme)
     }

   override val targetUri: RequestFn = RequestAkka { req =>
     Success(req.effectiveUri(
       securedConnection = sc.secure,
       defaultHostHeader = sc.defaultHost.map(Host(_)).getOrElse(Host.empty)
     ).toString())
   }

   override val status: ResponseFn = ResponseAkka { resp =>
     Success("" + resp.status.intValue)
   }

   case class RequestAkka(
       val sigValues: HttpRequest => Try[String | NonEmptyList[String]]
   ) extends SelectorFn[Http.Request[Id, HT]]:
      override val signingValues: Request[Id, HT] => Try[String | NonEmptyList[String]] =
        msg => sigValues(msg.asInstanceOf[HttpRequest])

   case class ResponseAkka(
       val sigValues: HttpResponse => Try[String | NonEmptyList[String]]
   ) extends SelectorFn[Http.Response[Id, HT]]:
      override val signingValues: Response[Id, HT] => Try[String | NonEmptyList[String]] =
        msg => sigValues(msg.asInstanceOf[HttpResponse])

end SelectorFnsAkka

object SelectorAkka:
   import run.cosy.http.headers.Rfc8941.Serialise.given

   def getHeaders(name: HeaderId)(msg: HttpMessage): Try[NonEmptyList[String]] =
      val N = name.specName
      msg.headers.collect { case HttpHeader(N, value) => value.trim.nn }.toList match
         case Nil =>
           Failure(SelectorException(s"No headers named ${name.canon} selectable in request"))
         case head :: tail => Success(NonEmptyList(head, tail))
end SelectorAkka
