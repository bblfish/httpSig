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

package run.cosy.http4s.headers

import org.http4s.{Uri, Message as H4Message, Request as H4Request, Response as H4Response}
import run.cosy.http.Http
import run.cosy.http.Http.*
import run.cosy.http.headers.{DictSelector, HeaderSelector, MessageSelector, MessageSelectors}
import run.cosy.http4s.Http4sTp.H4
import run.cosy.http.headers.BasicMessageSelector
import scala.util.{Try,Success,Failure}
import org.http4s.headers.Host
import org.http4s.Query
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http.headers.Rfc8941
import run.cosy.http.auth.{AttributeMissingException,SelectorException}

object Http4sMessageSelector:
   def request[F[_]](name: String): MessageSelector[Http.Request[F, H4]] =
     new BasicHeaderSelector[F,Http.Request[F, H4]]:
        override val lowercaseName: String = name
   def response[F[_]](name: String): MessageSelector[Http.Response[F, H4]] =
     new BasicHeaderSelector[F, Http.Response[F, H4]]:
        override val lowercaseName: String = name

//   def hdrMessage[F[_]](name: String): MessageSelector[Http.Message[F, H4]] =
//     new BasicHeaderSelector[F, Http.Message[F, H4]]:
//        override val lowercaseName: String = name
//
//   def dictMsg[F[_], M <: Http.Message[F, H4]](name: String): DictSelector[M] =
//     new Http4sDictSelector[F, M]:
//        override def lowercaseName: String = name
end Http4sMessageSelector

class Http4sMessageSelectors[F[_]](
    val securedConnection: Boolean,
    val defaultAuthority: Uri.Authority,
    val defaultPort: Int
) extends MessageSelectors[F, H4]:
   import Http4sMessageSelector.*

   override lazy val authorization: MessageSelector[Http.Request[F, H4]] =
     new BasicHeaderSelector[F, Http.Request[F, H4]]:
        override def lowercaseName: String = "authorization"

   override lazy val date: MessageSelector[Http.Message[F, H4]] =
     new BasicHeaderSelector[F, Http.Message[F, H4]]:
        override def lowercaseName: String = "date"

   override lazy val etag: MessageSelector[Http.Response[F, H4]] =
     new BasicHeaderSelector[F, Http.Response[F, H4]]:
        override def lowercaseName: String = "etag"

   override lazy val host: MessageSelector[Http.Request[F, H4]] =
     new BasicHeaderSelector[F, Http.Request[F, H4]]:
        override def lowercaseName: String = "host"

   override lazy val signature: DictSelector[Http.Message[F, H4]] =
     new Http4sDictSelector[F, Http.Message[F, H4]]:
        override def lowercaseName: String = "signature"

   override lazy val digest: MessageSelector[Http.Message[F, H4]] =
     new BasicHeaderSelector[F, Http.Message[F, H4]]:
        override def lowercaseName: String = "digest"

   override lazy val forwarded: MessageSelector[Http.Message[F, H4]] =
     new BasicHeaderSelector[F, Http.Message[F, H4]]:
        override def lowercaseName: String = "forwarded"

   override lazy val `cache-control`: MessageSelector[Http.Message[F, H4]] =
     new BasicHeaderSelector[F, Http.Message[F, H4]]:
        override def lowercaseName: String = "cache-control"

   override lazy val `client-cert`: MessageSelector[Http.Message[F, H4]] =
     new BasicHeaderSelector[F, Http.Message[F, H4]]:
        override def lowercaseName: String = "client-cert"

   override lazy val `content-length`: MessageSelector[Http.Message[F, H4]] =
     new BasicHeaderSelector[F, Http.Message[F, H4]]:
        override def lowercaseName: String = "content-length"

   override lazy val `content-type`: MessageSelector[Http.Message[F, H4]] =
     new BasicHeaderSelector[F, Http.Message[F, H4]]:
        override def lowercaseName: String = "content-type"

   override lazy val `@request-target`: MessageSelector[Http.Request[F, H4]] =
     new BasicMessageSelector[Http.Request[F, H4]]:
        override def lowercaseName: String       = "@request-target"
        override def specialForRequests: Boolean = true
        // todo: return an error if http version above 1.1? (see spec warning)
        override protected def signingStringValue(msg: Http.Request[F, H4]): Try[String] =
           val m = msg.asInstanceOf[H4Request[F]]
           Try(m.uri.renderString)
   end `@request-target`

   override lazy val `@method`: MessageSelector[Http.Request[F, H4]] =
     new BasicMessageSelector[Http.Request[F, H4]]:
        override def lowercaseName: String       = "@method"
        override def specialForRequests: Boolean = true

        override protected def signingStringValue(msg: Http.Request[F, H4]): Try[String] =
           val m = msg.asInstanceOf[H4Request[F]]
           Success(m.method.name) // already uppercase
   end `@method`

   override lazy val `@target-uri`: MessageSelector[Http.Request[F, H4]] =
     new BasicMessageSelector[Http.Request[F, H4]]:
        val defaultScheme: Some[Uri.Scheme] =
          if securedConnection then Some(Uri.Scheme.https) else Some(Uri.Scheme.http)
        val defaultAuthorityOpt: Some[Uri.Authority] = Some(defaultAuthority)
        override def lowercaseName: String           = "@target-uri"
        override def specialForRequests: Boolean     = true

        override protected def signingStringValue(msg: Http.Request[F, H4]): Try[String] =
           val m   = msg.asInstanceOf[H4Request[?]]
           val uri = m.uri
           if uri.scheme.isDefined && uri.authority.isDefined then Success(uri.renderString)
           else
              Success(uri.copy(
                uri.scheme.orElse(defaultScheme),
                uri.authority.orElse {
                  m.headers.get[Host]
                    .map(h => Uri.Authority(None, Uri.RegName(h.host), h.port))
                    .orElse(defaultAuthorityOpt)
                }
              ).renderString)

   end `@target-uri`

   override lazy val `@authority`: MessageSelector[Http.Request[F, H4]] =
     new BasicMessageSelector[Http.Request[F, H4]]:
        override def lowercaseName: String       = "@authority"
        override def specialForRequests: Boolean = true

        override protected def signingStringValue(msg: Http.Request[F, H4]): Try[String] =
           val m = msg.asInstanceOf[H4Request[?]]
           Success(m.uri.authority.getOrElse(
             m.headers.get[Host]
               .map(h => Uri.Authority(None, Uri.RegName(h.host), h.port))
               .getOrElse(defaultAuthority)
           ).renderString)

   end `@authority`

   override lazy val `@scheme`: MessageSelector[Http.Request[F, H4]] =
     new BasicMessageSelector[Http.Request[F, H4]]:
        override def lowercaseName: String       = "@scheme"
        override def specialForRequests: Boolean = true
        val scheme: Uri.Scheme = if securedConnection then Uri.Scheme.https else Uri.Scheme.http

        // todo: inefficient as it builds whole URI to extract only a small piece
        override protected def signingStringValue(msg: Http.Request[F, H4]): Try[String] =
          Success(scheme.value)
   end `@scheme`

   override lazy val `@path`: MessageSelector[Http.Request[F, H4]] =
     new BasicMessageSelector[Http.Request[F, H4]]:
        override def lowercaseName: String       = "@path"
        override def specialForRequests: Boolean = true

        override protected def signingStringValue(msg: Http.Request[F, H4]): Try[String] =
           val m = msg.asInstanceOf[H4Request[?]]
           Try(m.uri.path.renderString)
   end `@path`

   override lazy val `@status`: MessageSelector[Http.Response[F, H4]] =
     new BasicMessageSelector[Http.Response[F, H4]]:
        override def lowercaseName: String = "@status"

        override protected def signingStringValue(msg: Http.Response[F, H4]): Try[String] =
           val m = msg.asInstanceOf[H4Response[F]]
           Success("" + m.status.code)
   end `@status`

   override lazy val `@query`: MessageSelector[Http.Request[F, H4]] =
     new BasicMessageSelector[Http.Request[F, H4]]:
        override def lowercaseName: String       = "@query"
        override def specialForRequests: Boolean = true

        override protected def signingStringValue(msg: Http.Request[F, H4]): Try[String] =
           val m = msg.asInstanceOf[H4Request[?]]
           val q = m.uri.query
           Success {
             if q == Query.empty then ""
             else if q == Query.blank then "?"
             else
                val qs = m.uri.query.renderString
                if qs == "" then "?" else "?" + qs // todo: check this is right
           }
   end `@query`

   override lazy val `@query-params`: MessageSelector[Http.Request[F, H4]] =
     new MessageSelector[Http.Request[F, H4]]:
        val nameParam: Rfc8941.Token             = Rfc8941.Token("name")
        override def lowercaseName: String       = "@query-params"
        override def specialForRequests: Boolean = true

        override def signingString(msg: Http.Request[F, H4], params: Rfc8941.Params): Try[String] =
           val m = msg.asInstanceOf[H4Request[?]]
           params.toSeq match
              case Seq(nameParam -> (value: Rfc8941.SfString)) => Try {
                  val queryStr = m.uri.query.params.get(value.asciiStr).getOrElse("")
                  s""""$lowercaseName";name=${value.canon}: $queryStr"""
                }
              case _ => Failure(
                  SelectorException(
                    s"selector $lowercaseName only takes ${nameParam.canon} parameters. Received " + params
                  )
                )
   end `@query-params`

   override lazy val `@request-response`: MessageSelector[Http.Request[F, H4]] =
     new MessageSelector[Http.Request[F, H4]]:
        val keyParam: Rfc8941.Token              = Rfc8941.Token("key")
        override def lowercaseName: String       = "@request-response"
        override def specialForRequests: Boolean = true

        override def signingString(msg: Http.Request[F, H4], params: Rfc8941.Params): Try[String] =
          params.toSeq match
             case Seq(keyParam -> (value: Rfc8941.SfString)) => signingStringFor(msg, value)
             case _ => Failure(
                 SelectorException(
                   s"selector $lowercaseName only takes ${keyParam.canon} paramters. Received " + params
                 )
               )

        protected def signingStringFor(
            msg: Http.Request[F, H4],
            key: Rfc8941.SfString
        ): Try[String] =
          for
             sigsDict <- signature.sfDictParse(msg)
             keyStr   <- Try(Rfc8941.Token(key.asciiStr))
             signature <- sigsDict.get(keyStr)
               .toRight(AttributeMissingException(s"could not find signature '$keyStr'"))
               .toTry
          yield s""""$lowercaseName";key=${key.canon}: ${signature.canon}"""
   end `@request-response`

end Http4sMessageSelectors
