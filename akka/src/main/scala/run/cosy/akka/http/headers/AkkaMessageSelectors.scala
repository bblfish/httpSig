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

package run.cosy.akka.http.headers

import akka.http.scaladsl.model.ContentTypes.NoContentType
import akka.http.scaladsl.model.headers.{Authorization, Date, ETag, Host, `Cache-Control`}
import akka.http.scaladsl.model.{HttpMessage, HttpRequest, HttpResponse}
import run.cosy.akka.http.AkkaTp
import run.cosy.http.auth.{
  AttributeMissingException,
  SelectorException,
  UnableToCreateSigHeaderException
}
import run.cosy.http.headers.{
  BasicMessageHeaderSelector,
  BasicMessageSelector,
  DictSelector,
  MessageSelector,
  MessageSelectors,
  Rfc8941
}
import run.cosy.http.headers.Rfc8941.Serialise.given

import java.nio.charset.Charset
import java.util.Locale
import scala.util.{Failure, Success, Try}
import run.cosy.http.Http
import run.cosy.akka.http.AkkaTp.HT

class AkkaMessageSelectors[F[_]](
    val securedConnection: Boolean,
    val defaultHost: akka.http.scaladsl.model.Uri.Host,
    val defaultPort: Int
) extends MessageSelectors[F, HT]:
   import Http.*

   override lazy val authorization: MessageSelector[Http.Request[F, HT]] =
     new TypedAkkaSelector[Http.Request[F, HT], Authorization]:
        def akkaCompanion = Authorization
   override lazy val `cache-control`: MessageSelector[Http.Message[F, HT]] =
     new TypedAkkaSelector[Http.Message[F, HT], `Cache-Control`]:
        def akkaCompanion = `Cache-Control`
   override lazy val date: MessageSelector[Http.Message[F, HT]] =
     new TypedAkkaSelector[Http.Message[F, HT], Date]:
        def akkaCompanion = Date
   override lazy val etag: MessageSelector[Http.Response[F, HT]] =
     new TypedAkkaSelector[Http.Response[F, HT], ETag]:
        def akkaCompanion = ETag
   override lazy val host: MessageSelector[Http.Request[F, HT]] =
     new TypedAkkaSelector[Http.Request[F, HT], Host]:
        def akkaCompanion = Host
   override lazy val signature: DictSelector[Http.Message[F, HT]] =
     new AkkaDictSelector[Http.Message[F, HT]]:
        override val lowercaseName: String = "signature"
   override lazy val `content-type`: MessageSelector[Http.Message[F, HT]] =
     new UntypedAkkaSelector[Http.Message[F, HT]]:
        override val lowercaseName: String = "content-type"
        override def signingString(msg: Http.Message[F, HT], params: Rfc8941.Params): Try[String] =
          if params.isEmpty then
             val m = msg.asInstanceOf[HttpMessage]
             m.entity.contentType match
                case NoContentType => Failure(
                    UnableToCreateSigHeaderException(s"""No header '$lowercaseName' in request""")
                  )
                case ct => Success(s""""$lowercaseName": $ct""")
          else
             Failure(SelectorException(
               s"selector $lowercaseName does not take parameters. Received " + params
             ))
   override lazy val `content-length`: MessageSelector[Http.Message[F, HT]] =
     new UntypedAkkaSelector[Http.Message[F, HT]]:
        override val lowercaseName: String = "content-length"
        override def signingString(msg: Http.Message[F, HT], params: Rfc8941.Params): Try[String] =
           val m = msg.asInstanceOf[HttpMessage]
           if params.isEmpty then
              m.entity.contentLengthOption match
                 case None => Failure(
                     UnableToCreateSigHeaderException(s"""No header '$lowercaseName' in request""")
                   )
                 case Some(cl) => Success(s""""$lowercaseName": $cl""")
           else
              Failure(SelectorException(
                s"selector $lowercaseName does not take parameters. Received " + params
              ))
   override lazy val `client-cert`: MessageSelector[Http.Message[F, HT]] =
     new UntypedAkkaSelector[Http.Message[F, HT]]:
        override val lowercaseName: String = "client-cert"

   override lazy val digest: MessageSelector[Http.Message[F, HT]] =
     new UntypedAkkaSelector[Http.Message[F, HT]]:
        override val lowercaseName: String = "digest"
   override lazy val forwarded: MessageSelector[Http.Message[F, HT]] =
     new UntypedAkkaSelector[Http.Message[F, HT]]:
        override val lowercaseName: String = "forwarded"
   override lazy val `@request-target`: BasicMessageSelector[Http.Request[F, HT]] =
     new BasicMessageSelector[Http.Request[F, HT]]:
        override val lowercaseName: String       = "@request-target"
        override def specialForRequests: Boolean = true

        override protected def signingStringValue(req: Http.Request[F, HT]): Try[String] =
           val r = req.asInstanceOf[HttpRequest]
           Try(r.uri.toString()) // tests needed with connnect
        //		req.method match
        //		case HttpMethods.CONNECT => Failure(UnableToCreateSigHeaderException("Akka cannot correctly prcess @request-target on CONNECT requests"))
        //		case _ => Success(s""""$lowercaseName": ${req.uri}""")
   override lazy val `@method`: BasicMessageSelector[Http.Request[F, HT]] =
     new BasicMessageSelector[Http.Request[F, HT]]:
        override def lowercaseName: String       = "@method"
        override def specialForRequests: Boolean = true

        override protected def signingStringValue(req: Http.Request[F, HT]): Try[String] =
           val r = req.asInstanceOf[HttpRequest]
           Success(r.method.value) // already uppercase
   override lazy val `@target-uri`: BasicMessageSelector[Http.Request[F, HT]] =
     new BasicMessageSelector[Http.Request[F, HT]]:
        override def lowercaseName: String       = "@target-uri"
        override def specialForRequests: Boolean = true
        override protected def signingStringValue(req: Http.Request[F, HT]): Try[String] =
           val r = req.asInstanceOf[HttpRequest]
           Success(r.effectiveUri(securedConnection, defaultHostHeader).toString())

   override lazy val `@authority`: BasicMessageSelector[Http.Request[F, HT]] =
     new BasicMessageSelector[Http.Request[F, HT]]:
        override def lowercaseName: String       = "@authority"
        override def specialForRequests: Boolean = true

        // todo: inefficient as it builds whole URI to extract only a small piece
        override protected def signingStringValue(req: Http.Request[F, HT]): Try[String] =
           val r = req.asInstanceOf[HttpRequest]
           Try(r.effectiveUri(true, defaultHostHeader)
             .authority.toString().toLowerCase(Locale.US).nn // is locale correct?
           )
   private lazy val defaultHostHeader =
      val p =
        if defaultPort == 0 then 0
        else if securedConnection & defaultPort == 443 then 0
        else if defaultPort == 80 then 0
        else defaultPort
      Host(defaultHost, p)

   override lazy val `@scheme`: BasicMessageSelector[Http.Request[F, HT]] =
     new BasicMessageSelector[Http.Request[F, HT]]:
        override def lowercaseName: String       = "@scheme"
        override def specialForRequests: Boolean = true

        // todo: inefficient as it builds whole URI to extract only a small piece
        override protected def signingStringValue(msg: Http.Request[F, HT]): Try[String] = Success(
          if securedConnection then "https" else "http"
        )
   override lazy val `@path`: BasicMessageSelector[Http.Request[F, HT]] =
     new BasicMessageSelector[Http.Request[F, HT]]:
        override def lowercaseName: String       = "@path"
        override def specialForRequests: Boolean = true

        override protected def signingStringValue(req: Http.Request[F, HT]): Try[String] =
           val r = req.asInstanceOf[HttpRequest]
           Try(r.uri.path.toString())
   override lazy val `@status`: BasicMessageSelector[Http.Response[F, HT]] =
     new BasicMessageSelector[Http.Response[F, HT]]:
        override def lowercaseName: String = "@status"

        override protected def signingStringValue(res: Http.Response[F, HT]): Try[String] =
           val r = res.asInstanceOf[HttpResponse]
           Try("" + r.status.intValue())

   override lazy val `@query`: BasicMessageSelector[Http.Request[F, HT]] =
     new BasicMessageSelector[Http.Request[F, HT]]:
        val ASCII: Charset                       = Charset.forName("ASCII").nn
        override def lowercaseName: String       = "@query"
        override def specialForRequests: Boolean = true

        override protected def signingStringValue(req: Http.Request[F, HT]): Try[String] =
           val r = req.asInstanceOf[HttpRequest]
           Try(r.uri.queryString(ASCII).map("?" + _).getOrElse(""))

   override lazy val `@query-params`: MessageSelector[Http.Request[F, HT]] =
     new MessageSelector[Http.Request[F, HT]]:
        val nameParam: Rfc8941.Token             = Rfc8941.Token("name")
        val ASCII: Charset                       = Charset.forName("ASCII").nn
        override def lowercaseName: String       = "@query-params"
        override def specialForRequests: Boolean = true

        override def signingString(req: Http.Request[F, HT], params: Rfc8941.Params): Try[String] =
          params.toSeq match
             case Seq(nameParam -> (value: Rfc8941.SfString)) => Try {
                 val r        = req.asInstanceOf[HttpRequest]
                 val queryStr = r.uri.query().get(value.asciiStr).getOrElse("")
                 s""""$lowercaseName";name=${value.canon}: $queryStr"""
               }
             case _ => Failure(
                 SelectorException(
                   s"selector $lowercaseName only takes ${nameParam.canon} parameters. Received " + params
                 )
               )
   override lazy val `@request-response`: MessageSelector[Http.Request[F, HT]] =
     new MessageSelector[Http.Request[F, HT]]:
        val keyParam: Rfc8941.Token              = Rfc8941.Token("key")
        override def lowercaseName: String       = "@request-response"
        override def specialForRequests: Boolean = true

        override def signingString(msg: Http.Request[F, HT], params: Rfc8941.Params): Try[String] =
          params.toSeq match
             case Seq(keyParam -> (value: Rfc8941.SfString)) => signingStringFor(msg, value)
             case _ => Failure(
                 SelectorException(
                   s"selector $lowercaseName only takes ${keyParam.canon} paramters. Received " + params
                 )
               )

        protected def signingStringFor(
            msg: Http.Request[F, HT],
            key: Rfc8941.SfString
        ): Try[String] =
          for
             sigsDict <- signature.sfDictParse(msg)
             keyStr   <- Try(Rfc8941.Token(key.asciiStr))
             signature <- sigsDict.get(keyStr)
               .toRight(AttributeMissingException(s"could not find signature '$keyStr'"))
               .toTry
          yield s""""$lowercaseName";key=${key.canon}: ${signature.canon}"""
end AkkaMessageSelectors
