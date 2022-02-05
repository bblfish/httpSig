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

import cats.data.NonEmptyList
import org.http4s.headers.Host
import org.http4s.{Message, Request, Response, Query}
import run.cosy.http.headers.{
  BasicMessageSelector,
  DictSelector,
  HeaderSelector,
  MessageSelector,
  Rfc8941,
  SelectorOps
}

import scala.util.{Failure, Success, Try}
import org.typelevel.ci.{CIString, *}
import run.cosy.http.Http
import run.cosy.http.auth.{
  AttributeMissingException,
  AuthExc,
  SelectorException,
  UnableToCreateSigHeaderException
}
import run.cosy.http.headers.Rfc8941.SfDict
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http4s.Http4sTp

import java.util.Locale

trait BasicHeaderSelector[HM <: Message[?]]
    extends BasicMessageSelector[HM] with Http4sHeaderSelector[HM]:
   def lowercaseHeaderName: String = lowercaseName

   override protected def signingStringValue(msg: HM): Try[String] =
     for headers <- filterHeaders(msg)
     yield SelectorOps.collate(headers)
end BasicHeaderSelector

trait Http4sHeaderSelector[HM <: Message[?]] extends HeaderSelector[HM]:
   def lowercaseHeaderName: String
   lazy val ciLowercaseName: CIString = CIString(lowercaseHeaderName)

   override def filterHeaders(msg: HM): Try[NonEmptyList[String]] =
     for
        nonEmpty <- msg.headers.get(ciLowercaseName)
          .toRight(UnableToCreateSigHeaderException(
            s"""No headers "$lowercaseHeaderName" in http message"""
          )).toTry
     yield nonEmpty.map(_.value)
end Http4sHeaderSelector

trait Http4sDictSelector[HM <: Http.Message[Http4sTp.type]]
    extends DictSelector[HM] with Http4sHeaderSelector[HM]:
   override lazy val lowercaseHeaderName: String = lowercaseName

end Http4sDictSelector

// the simple header selectors

object authorization extends BasicHeaderSelector[Request[?]]:
   override def lowercaseName: String = "authorization"

object `cache-control` extends BasicHeaderSelector[Message[?]]:
   override def lowercaseName: String = "cache-control"

object date extends BasicHeaderSelector[Message[?]]:
   override def lowercaseName: String = "date"

object etag extends BasicHeaderSelector[Response[?]]:
   override def lowercaseName: String = "etag"

object host extends BasicHeaderSelector[Request[?]]:
   override def lowercaseName: String = "host"

object `content-type` extends BasicHeaderSelector[Message[?]]:
   override def lowercaseName: String = "content-type"

object `content-length` extends BasicHeaderSelector[Message[?]]:
   override def lowercaseName: String = "content-length"

object digest extends BasicHeaderSelector[Message[?]]:
   override val lowercaseName: String = "digest"

object signature extends Http4sDictSelector[Http.Message[Http4sTp.type]]:
   override val lowercaseName: String = "signature"

/** `@request-target` refers to the full request target of the HTTP request message, as defined in
  * "HTTP Semantics" For HTTP 1.1, the component value is equivalent to the request target portion
  * of the request line. However, this value is more difficult to reliably construct in other
  * versions of HTTP. Therefore, it is NOT RECOMMENDED that this identifier be used when versions of
  * HTTP other than 1.1 might be in use.
  *
  * @see
  *   https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-request-target
  */
object `@request-target` extends BasicMessageSelector[Request[?]]:
   override def lowercaseName: String       = "@request-target"
   override def specialForRequests: Boolean = true
   // todo: return an error if http version above 1.1? (see spec warning)
   override protected def signingStringValue(msg: Request[?]): Try[String] =
     Try(msg.uri.renderString)
end `@request-target`

/*
 * can also be used in a response, but requires the original request to
 * calculate
 * @see https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-method
 */
object `@method` extends BasicMessageSelector[Request[?]]:
   override def lowercaseName: String       = "@method"
   override def specialForRequests: Boolean = true

   override protected def signingStringValue(msg: Request[?]): Try[String] =
     Success(msg.method.name) // already uppercase
end `@method`

/** in order to give the target URI we need to know if this is an https connection and what the host
  * header is if not specified in the request note: can also be used in a response, but requires the
  * original request to calculate
  *
  * @see
  *   https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-target-uri
  */
import org.http4s.Uri
case class `@target-uri`(securedConnection: Boolean, defaultAuthority: Uri.Authority)
    extends BasicMessageSelector[Request[?]]:
   val defaultScheme: Some[Uri.Scheme] =
     if securedConnection then Some(Uri.Scheme.https) else Some(Uri.Scheme.http)
   val defaultAuthorityOpt: Some[Uri.Authority] = Some(defaultAuthority)
   override def lowercaseName: String           = "@target-uri"
   override def specialForRequests: Boolean     = true

   override protected def signingStringValue(msg: Request[?]): Try[String] =
      val uri = msg.uri
      if uri.scheme.isDefined && uri.authority.isDefined then Success(uri.renderString)
      else
         Success(uri.copy(
           uri.scheme.orElse(defaultScheme),
           uri.authority.orElse {
             msg.headers.get[Host]
               .map(h => Uri.Authority(None, Uri.RegName(h.host), h.port))
               .orElse(defaultAuthorityOpt)
           }
         ).renderString)

end `@target-uri`

/** we may need to know the host name of the server to use as a default note: can also be used in a
  * response, but requires the original request to calculate
  *
  * @see
  *   https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-authority
  */
case class `@authority`(defaultHostHeader: Uri.Authority) extends BasicMessageSelector[Request[?]]:
   override def lowercaseName: String       = "@authority"
   override def specialForRequests: Boolean = true

   override protected def signingStringValue(msg: Request[?]): Try[String] =
     Success(msg.uri.authority.getOrElse(
       msg.headers.get[Host]
         .map(h => Uri.Authority(None, Uri.RegName(h.host), h.port))
         .getOrElse(defaultHostHeader)
     ).renderString)

end `@authority`

/** we need to know if the server is running on http or https
  *
  * @see
  *   https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-scheme
  */
case class `@scheme`(secure: Boolean) extends BasicMessageSelector[Request[?]]:
   override def lowercaseName: String       = "@scheme"
   override def specialForRequests: Boolean = true
   val scheme: Uri.Scheme                   = if secure then Uri.Scheme.https else Uri.Scheme.http

   // todo: inefficient as it builds whole URI to extract only a small piece
   override protected def signingStringValue(msg: Request[?]): Try[String] = Success(scheme.value)
end `@scheme`

/** @see
  *   https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-path
  */
object `@path` extends BasicMessageSelector[Request[?]]:
   override def lowercaseName: String       = "@path"
   override def specialForRequests: Boolean = true

   override protected def signingStringValue(msg: Request[?]): Try[String] = Try(
     msg.uri.path.renderString
   )
end `@path`

/** @see
  *   https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-status-code
  */
object `@status` extends BasicMessageSelector[Response[?]]:
   override def lowercaseName: String = "@status"

   override protected def signingStringValue(msg: Response[?]): Try[String] =
     Success("" + msg.status.code)
end `@status`

/** @see https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-query */
object `@query` extends BasicMessageSelector[Request[?]]:
   override def lowercaseName: String       = "@query"
   override def specialForRequests: Boolean = true

   override protected def signingStringValue(msg: Request[?]): Try[String] =
      val q = msg.uri.query
      Success {
        if q == Query.empty then ""
        else if q == Query.blank then "?"
        else
           val qs = msg.uri.query.renderString
           if qs == "" then "?" else "?" + qs // todo: check this is right
      }
end `@query`

/** @see
  *   https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-query-parameters
  */
object `@query-params` extends MessageSelector[Request[?]]:
   val nameParam: Rfc8941.Token             = Rfc8941.Token("name")
   override def lowercaseName: String       = "@query-params"
   override def specialForRequests: Boolean = true

   override def signingString(msg: Request[?], params: Rfc8941.Params): Try[String] =
     params.toSeq match
        case Seq(nameParam -> (value: Rfc8941.SfString)) => Try {
            val queryStr = msg.uri.query.params.get(value.asciiStr).getOrElse("")
            s""""$lowercaseName";name=${value.canon}: $queryStr"""
          }
        case _ => Failure(
            SelectorException(
              s"selector $lowercaseName only takes ${nameParam.canon} parameters. Received " + params
            )
          )
end `@query-params`

object `@request-response` extends MessageSelector[Request[?]]:
   val keyParam: Rfc8941.Token              = Rfc8941.Token("key")
   override def lowercaseName: String       = "@request-response"
   override def specialForRequests: Boolean = true

   override def signingString(msg: Request[?], params: Rfc8941.Params): Try[String] =
     params.toSeq match
        case Seq(keyParam -> (value: Rfc8941.SfString)) => signingStringFor(msg, value)
        case _ => Failure(
            SelectorException(
              s"selector $lowercaseName only takes ${keyParam.canon} paramters. Received " + params
            )
          )

   protected def signingStringFor(msg: Request[?], key: Rfc8941.SfString): Try[String] =
     for
        sigsDict <- signature.sfDictParse(msg)
        keyStr   <- Try(Rfc8941.Token(key.asciiStr))
        signature <- sigsDict.get(keyStr)
          .toRight(AttributeMissingException(s"could not find signature '$keyStr'"))
          .toTry
     yield s""""$lowercaseName";key=${key.canon}: ${signature.canon}"""
end `@request-response`
