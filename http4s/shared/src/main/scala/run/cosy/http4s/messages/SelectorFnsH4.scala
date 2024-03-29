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

import cats.data.NonEmptyList
import org.http4s.headers.Host
import org.http4s.{Query, Uri, Message as H4Message, Request as H4Request, Response as H4Response}
import org.typelevel.ci.CIString
import run.cosy.http.Http
import run.cosy.http.auth.{
  AttributeException,
  ParsingExc,
  SelectorException,
  MissingContextException
}
import run.cosy.http.headers.Rfc8941
import run.cosy.http.messages.Parameters.nameTk
import run.cosy.http.messages.*
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT as H4
import run.cosy.http4s.messages.SelectorFnsH4.getHeaders
import run.cosy.platform

import scala.util.{Failure, Success, Try}

class SelectorFnsH4(using sc: ServerContext = NoServerContext) extends ReqFns[H4]:
   val SF = SelectorFnsH4

   override def method: RequestFn =
     RequestSelH4(req => Right(req.method.name))

   override def authority: RequestFn = RequestSelH4(req =>
     for
        auth <- SF.authorityFor(req)
          .toRight(SelectorException("could not construct authority for request"))
     yield platform.StringUtil.toLowerCaseInsensitive(auth.renderString)
   )

   /** best not used if not HTTP1.1 */
   override def requestTarget: RequestFn = RequestSelH4(req => Right(req.uri.renderString))

   override def path: RequestFn = RequestSelH4(req => Right(req.uri.path.toString()))

   override def query: RequestFn = RequestSelH4(req =>
     Right {
       val q: Query = req.uri.query
       if q == Query.empty then "?"
       else if q == Query.blank then "?"
       else
          val qs = q.renderString
          if qs == "" then "?" else "?" + qs
     }
   )

   override def queryParam(name: Rfc8941.SfString): RequestFn = RequestSelH4(req =>
     req.uri.query.multiParams.get(name.asciiStr) match
        case None => Left(SelectorException(
            s"No query parameter with key ${name.asciiStr} found. Suspicious."
          ))
        case Some(Nil)          => Right("")
        case Some(head :: tail) => Right(NonEmptyList(head, tail))
   )

   override def scheme: RequestFn = RequestSelH4(req =>
     SF.effectiveUriFor(req).map(_.scheme.get.value)
   )

   override def targetUri: RequestFn = RequestSelH4(req =>
     SF.effectiveUriFor(req).map(uri =>
        val normed =
          uri.copy(authority = uri.authority.map(a => SF.normaliseAuthority(a, uri.scheme)))
        normed.renderString
     )
   )

   override def requestHeaders(name: HeaderId): RequestFn = RequestSelH4(req =>
     SF.getHeaders(req, name)
   )

   case class RequestSelH4(
       val sigValues: H4Request[Http4sTp.F] => Either[ParsingExc, String | NonEmptyList[String]]
   ) extends SelectorFn[Http.Request[H4]]:
      override val signingValues
          : Http.Request[H4] => Either[ParsingExc, String | NonEmptyList[String]] =
        msg => sigValues(msg.asInstanceOf[H4Request[Http4sTp.F]])

end SelectorFnsH4

class ResponseSelectorFnsH4(using sc: AServerContext) extends ResFns[H4]:
   val SF = SelectorFnsH4

   override def responseHeaders(name: HeaderId): ResponseFn = ResponseSelH4(res =>
     SF.getHeaders(res, name)
   )
   override def status: ResponseFn = ResponseSelH4(req => Right("" + req.status.code))

   case class ResponseSelH4(
       sigValues: H4Response[Http4sTp.F] => Either[ParsingExc, String | NonEmptyList[String]]
   ) extends SelectorFn[Http.Response[H4]]:
      override val signingValues
          : Http.Response[H4] => Either[ParsingExc, String | NonEmptyList[String]] =
        msg => sigValues(msg.asInstanceOf[H4Response[Http4sTp.F]])

object SelectorFnsH4:

   def getHeaders[F[_]](msg: H4Message[F], name: HeaderId) =
     msg.headers.get(CIString(name.specName)).map(_.map(_.value)) match
        case None      => Left(SelectorException(s"no header in request named $name"))
        case Some(nel) => Right(nel)

   def defaultAuthorityOpt(using sc: ServerContext): Option[Uri.Authority] =
     sc match
        case NoServerContext => None
        case asc: AServerContext =>
          asc.defaultHost.map(h =>
            Uri.Authority(
              None,
              Uri.RegName(h),
              // we assume that if the ports are the default one then we have the corresponding security values
              // otherwise we just don't know... (a bit awkward. It may be better to fail)
              if asc.secure && asc.port == 443 then None
              else if (!asc.secure) && asc.port == 80 then None
              else Some(asc.port)
            )
          )

   def normaliseAuthority(auth: Uri.Authority, schema: Option[Uri.Scheme]): Uri.Authority =
     schema match
        case Some(Uri.Scheme.https) if auth.port == Some(443) => auth.copy(port = None)
        case Some(Uri.Scheme.http) if auth.port == Some(80)   => auth.copy(port = None)
        case _                                                => auth
   end normaliseAuthority

   def authorityFor[F[_]](req: H4Request[F])(using sc: ServerContext): Option[Uri.Authority] =
     req.uri.authority
       .map(a => normaliseAuthority(a, req.uri.scheme))
       .orElse(req.headers.get[Host]
         .map(h => Uri.Authority(None, Uri.RegName(h.host), h.port))
         .orElse(defaultAuthorityOpt))

   def defaultScheme(using sc: ServerContext): Option[Uri.Scheme] =
     sc match
        case NoServerContext => None
        case asc: AServerContext =>
          Some(if asc.secure then Uri.Scheme.https else Uri.Scheme.http)

   def effectiveUriFor[F[_]](req: H4Request[F])(using
       sc: ServerContext
   ): Either[SelectorException, Uri] =
      val uri = req.uri
      if uri.scheme.isDefined && uri.authority.isDefined
      then Right(uri)
      else
         val newUri = uri.copy(
           scheme = uri.scheme.orElse(defaultScheme),
           authority = authorityFor(req)
         )
         if newUri.authority.isDefined && newUri.scheme.isDefined
         then Right(newUri)
         else Left(SelectorException(s"cannot create effective Uri for req. Got: <$newUri>"))
   end effectiveUriFor
