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

package run.cosy.akka.http.message

import akka.http.scaladsl.model
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{HttpMessage, HttpRequest, HttpResponse}
import cats.Id
import run.cosy.akka.http.AkkaTp
import run.cosy.akka.http.AkkaTp.HT
import run.cosy.akka.http.message.{AkkaAtComponent, AkkaAtReqPlainComponent, AkkaRequestAtComponent, AtReqSelector}
import run.cosy.http.Http
import run.cosy.http.Http.{Message, Request}
import run.cosy.http.headers.*
import run.cosy.http.headers.Rfc8941.Params
import run.cosy.http.headers.Rfc8941.Serialise.given

import scala.util.{Success, Try}

trait AkkaAtComponent extends AtComponent:
   override type Msg <: Http.Message[Id, HT]

trait AkkaRequestAtComponent extends AkkaAtComponent:
   override type Msg = Http.Request[Id, HT]

trait AtReqSelector extends AtSelector[Http.Request[Id, HT]]
    with SelectorOneLine[Http.Request[Id, HT]]

/* Plain requests selectors with no need for attributes other than "req" */
class AkkaAtReqPlainComponent(override val name: String)(select: HttpRequest => Try[String])
    extends AkkaRequestAtComponent:
   override def mkSelector(p: Params) = new AtReqSelector:
      def params: Params = p
      def name: String   = AkkaAtReqPlainComponent.this.name
      override protected def value(req: Http.Request[Id, HT]): Try[String] =
         val r = req.asInstanceOf[HttpRequest]
         select(r)
end AkkaAtReqPlainComponent

object `@method` extends AkkaAtReqPlainComponent("@method")(req =>
      Success(req.method.value)
    )

case class `@target-uri`()(using sc : ServerContext)
    extends AkkaAtReqPlainComponent("@target-uri")((req: HttpRequest) =>
      Success(req.effectiveUri(
        securedConnection = sc.secure,
        defaultHostHeader = Host(sc.defaultHost)
      ).toString())
    )

case class `@authority`()(using sc: ServerContext)
    extends AkkaAtReqPlainComponent("@authority")((req: HttpRequest) =>
      Try(
        req.effectiveUri(true, Host(sc.defaultHost))
          .authority.toString().toLowerCase(java.util.Locale.ROOT).nn
      )
    )

case class `@scheme`()(using sc: ServerContext)
    extends AkkaAtReqPlainComponent("@scheme")(_ =>
      Success(if sc.secure then "https" else "http")
    )

// This won't work for Options in Akka
object `@request-target`
    extends AkkaAtReqPlainComponent("@request-target")((req: HttpRequest) =>
      Success(req.uri.toString())
    )

object `@path`
    extends AkkaAtReqPlainComponent("@path")((req: HttpRequest) =>
      Success(req.uri.path.toString())
    )

object `@query`
    extends AkkaAtReqPlainComponent("@query")((req: HttpRequest) =>
      Success(
        req.uri.queryString(java.nio.charset.StandardCharsets.US_ASCII.nn).map("?" + _).getOrElse(
          "?"
        )
      )
    )

object `@query-param` extends AkkaRequestAtComponent:
   import run.cosy.http.headers.Component.nameTk
   override val name: String                          = "@query-param"
   override def requiredParamKeys: Set[Rfc8941.Token] = Set(nameTk)

   override def mkSelector(p: Params) = new AtSelector[Http.Request[Id, HT]]:
      def params: Params = p
      def name: String   = `@query-param`.name
      override def signingStr(req: Http.Request[Id, HT]): Try[String] = Try {
        val r: HttpRequest = req.asInstanceOf[HttpRequest]
        params(nameTk) match
           case value: Rfc8941.SfString =>
             r.uri.query().getAll(value.asciiStr) match
                case Nil => throw run.cosy.http.auth.SelectorException(
                    s"No query parameter with key ${value.asciiStr} found. Suspicious."
                  )
                case nonEmptylist =>
                  val hdrKey = s""""$lowercaseName";name=${value.canon}: """
                  nonEmptylist.reverse.map(hdrKey + _).mkString("\n")
           case _ => throw run.cosy.http.auth.SelectorException(
               s"selector $lowercaseName only takes one >${nameTk.canon}< parameter. Received " + params
             )
      }
end `@query-param`

object `@status` extends AkkaAtComponent:
   type Msg = Http.Response[Id, HT]
   // since this can only appear on the response it cannot have a req parameter
   override def optionalParamKeys: Set[Rfc8941.Token] = Set.empty
   override val name: String                          = "@status"
   override def mkSelector(p: Params) = new AtSelector[Http.Response[Id, HT]]
     with SelectorOneLine[Http.Response[Id, HT]]:
      def params: Params = p
      def name: String   = `@status`.name
      override protected def value(res: Http.Response[Id, HT]): Try[String] =
         val hres: HttpResponse = res.asInstanceOf[HttpResponse]
         Success("" + hres.status.intValue)
end `@status`

//  new BasicMessageSelector[Http.Request[F, HT]] :
//    override def lowercaseName: String = "@method"
//
//    override def specialForRequests: Boolean = true
//
//    override protected def signingStringValue(req: Http.Request[F, HT]): Try[String] =
//      val r = req.asInstanceOf[HttpRequest]
//      Success(r.method.value) // already uppercase
