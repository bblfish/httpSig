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

import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.settings.ParserSettings.ConflictingContentTypeHeaderProcessingMode.NoContentType
import cats.Id
import cats.data.NonEmptyList
import run.cosy.akka.http.AkkaTp
import run.cosy.akka.http.AkkaTp.HT
import run.cosy.http.Http
import run.cosy.http.Http.{Request, Response}
import run.cosy.http.auth.{
  AttributeException,
  HTTPHeaderParseException,
  ParsingExc,
  SelectorException
}
import run.cosy.http.headers.Rfc8941.{Param, Params}
import run.cosy.http.headers.{ParsingException, Rfc8941}
import run.cosy.http.messages.*

import java.nio.charset.StandardCharsets
import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

class RequestSelectorFnsAkka(using sc: ServerContext)
    extends ReqFns[HT]:

   override val method: RequestFn =
     RequestAkka { req => Right(req.method.value) }

   override val authority: RequestFn =
     RequestAkka { req =>
       try
          Right(req.effectiveUri(
            sc.secure,
            sc.defaultHost.map(Host(_))
              .getOrElse(Host.empty)
          ).authority.toString().toLowerCase(java.util.Locale.ROOT).nn)
       catch
          case urlEx: IllegalUriException =>
            Left(
              SelectorException("cannot calculate effectuve url for request. " + urlEx.getMessage)
            )
     }

   /** best not used if not HTTP1.1 according to spec. Does not even work with Akka (see test suite)
     */
   override val requestTarget: RequestFn =
     RequestAkka { req => Right(req.uri.toString()) }

   // raw headers, no interpretation
   override def requestHeaders(headerName: HeaderId): RequestFn =
     RequestAkka { (req: HttpRequest) =>
       SelectorAkka.getHeaders(headerName)(req)
     }

   override val path: RequestFn =
     RequestAkka { req =>
       Right(req.uri.path.toString())
     }

   override val query: RequestFn =
     RequestAkka { req =>
       Right(
         req.uri.queryString(StandardCharsets.US_ASCII.nn)
           .map("?" + _).getOrElse("?")
       )
     }

   override def queryParam(name: Rfc8941.SfString): RequestFn =
     RequestAkka { req =>
       req.uri.query().getAll(name.asciiStr).reverse match
          case Nil => Left(SelectorException(
              s"No query parameter with key ${name} found. Suspicious."
            ))
          case head :: tail => Right(NonEmptyList(head, tail))
     }

   override val scheme: RequestFn =
     RequestAkka { req =>
       Right(req.effectiveUri(
         securedConnection = sc.secure,
         defaultHostHeader = sc.defaultHost.map(Host(_)).getOrElse(Host.empty)
       ).scheme)
     }

   override val targetUri: RequestFn = RequestAkka { req =>
     Right(req.effectiveUri(
       securedConnection = sc.secure,
       defaultHostHeader = sc.defaultHost.map(Host(_)).getOrElse(Host.empty)
     ).toString())
   }

   case class RequestAkka(
       val sigValues: HttpRequest => Either[ParsingExc, String | NonEmptyList[String]]
   ) extends SelectorFn[Http.Request[HT]]:
      override val signingValues: Request[HT] => Either[ParsingExc, String | NonEmptyList[String]] =
        msg => sigValues(msg.asInstanceOf[HttpRequest])

end RequestSelectorFnsAkka

class ResponseSelectorFnsAkka(using sc: ServerContext)
    extends ResFns[HT]:

   override val status: ResponseFn = ResponseAkka { resp =>
     Right("" + resp.status.intValue)
   }

   case class ResponseAkka(
       val sigValues: HttpResponse => Either[ParsingExc, String | NonEmptyList[String]]
   ) extends SelectorFn[Http.Response[HT]]:
      override val signingValues
          : Response[HT] => Either[ParsingExc, String | NonEmptyList[String]] =
        msg => sigValues(msg.asInstanceOf[HttpResponse])

   // raw headers, no interpretation
   override def responseHeaders(headerName: HeaderId): ResponseFn =
     ResponseAkka {
       SelectorAkka.getHeaders(headerName)
     }

object SelectorAkka:
   import run.cosy.http.headers.Rfc8941.Serialise.given

   def getHeaders(name: HeaderId)(msg: HttpMessage): Either[ParsingExc, NonEmptyList[String]] =
      val N = name.specName
      msg.headers.collect { case HttpHeader(N, value) => value.trim.nn }.toList match
         case Nil =>
           name.specName match
              case "content-length" => msg.entity.contentLengthOption
                  .toRight(SelectorException("no content-length header set"))
                  .map(l => NonEmptyList.one("" + l))
              case "content-type" if msg.entity.contentType != NoContentType =>
                Right(NonEmptyList.one(msg.entity.contentType.value))
              case _ =>
                Left(SelectorException(s"No headers named ${name.canon} selectable in request"))
         case head :: tail => Right(NonEmptyList(head, tail))
end SelectorAkka
