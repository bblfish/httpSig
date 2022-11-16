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

import org.http4s.headers.Host
import org.http4s.{Query, Uri, Message as H4Message, Request as H4Request, Response as H4Response}
import run.cosy.http.Http
import run.cosy.http.Http.*
import run.cosy.http.auth.{AttributeException, SelectorException}
import run.cosy.http.headers.Rfc8941.Params
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http.headers.*
import run.cosy.http.messages.{AtComponent, AtSelector, SelectorOneLine, ServerContext}
import run.cosy.http4s.Http4sTp.HT as H4

import scala.util.{Failure, Success, Try}

/** because http4s Request and response messsages depends on an effect Monad F[_], we need to wrap
  * the constructs in a class. All these classes and objects will be used together so this is fine.
  */

trait Http4sAt[F[_]]:

   trait Http4sAtComponent extends AtComponent:
      override type Msg <: Http.Message[F, H4]

   trait Http4sRequestAtComponent extends Http4sAtComponent:
      override type Msg = Http.Request[F, H4]

   trait AtReqSelector extends AtSelector[Http.Request[F, H4]]
       with SelectorOneLine[Http.Request[F, H4]]

   /* Plain requests selectors with no need for attributes other than "req" */
   class AtReqPlainComponent(override val name: String)(select: H4Request[F] => Try[String])
       extends Http4sRequestAtComponent:
      override def mkSelector(p: Params) = new AtReqSelector:
         def params: Params = p

         def name: String = AtReqPlainComponent.this.name

         override protected def value(req: Http.Request[F, H4]): Try[String] =
            val r: H4Request[F] = req.asInstanceOf[H4Request[F]]
            select(r)
   end AtReqPlainComponent

   object `@method` extends AtReqPlainComponent("@method")(req =>
         Success(req.method.name)
       )

   def defaultAuthorityOpt(using sc: ServerContext): Option[Uri.Authority] = sc.defaultHost.map(h =>
     Uri.Authority(None, Uri.RegName(h), sc.port.orElse(Some(if sc.secure then 443 else 80)))
   )

   def authorityFor(req: H4Request[F])(using sc: ServerContext): Option[Uri.Authority] =
     req.uri.authority.orElse {
       req.headers.get[Host]
         .map(h => Uri.Authority(None, Uri.RegName(h.host), h.port))
         .orElse(defaultAuthorityOpt)
     }

   def defaultScheme(using sc: ServerContext): Uri.Scheme =
     if sc.secure then Uri.Scheme.https else Uri.Scheme.http

   def effectiveUriFor(req: H4Request[F])(using sc: ServerContext): Try[Uri] =
      val uri = req.uri
      if uri.scheme.isDefined && uri.authority.isDefined
      then Success(uri)
      else
         val newUri = uri.copy(
           scheme = uri.scheme.orElse(Some(defaultScheme)),
           authority = authorityFor(req)
         )
         if newUri.authority.isDefined
         then Success(newUri)
         else Failure(SelectorException(s"cannot create effective Uri for req. Got: <$newUri>"))
   end effectiveUriFor

   case class `@target-uri`()(using sc: ServerContext)
       extends AtReqPlainComponent("@target-uri")((req: H4Request[F]) =>
         effectiveUriFor(req).map(_.renderString)
       )

   case class `@authority`()(using sc: ServerContext)
       extends AtReqPlainComponent("@authority")((req: H4Request[F]) =>
         Success(defaultScheme.value)
       )

   case class `@scheme`()(using sc: ServerContext)
       extends AtReqPlainComponent("@scheme")(req =>
         effectiveUriFor(req).map(_.scheme.get.value)
       )

   // This won't work for Options in Akka
   object `@request-target`
       extends AtReqPlainComponent("@request-target")((req: H4Request[F]) =>
         Success(req.uri.toString())
       )

   object `@path`
       extends AtReqPlainComponent("@path")((req: H4Request[F]) =>
         Success(req.uri.path.toString())
       )

   object `@query`
       extends AtReqPlainComponent("@query")((req: H4Request[F]) =>
          val q: Query = req.uri.query
          Success {
            if q == Query.empty then "?"
            else if q == Query.blank then "?"
            else
               val qs = q.renderString
               if qs == "" then "?" else "?" + qs
          }
       )

   object `@query-param` extends Http4sRequestAtComponent:

      import run.cosy.http.messages.Component.nameTk

      override val name: String = "@query-param"

      override def requiredParamKeys: Set[Rfc8941.Token] = Set(nameTk)

      override def mkSelector(p: Params) = new AtSelector[Http.Request[F, H4]]:
         def params: Params = p

         def name: String = `@query-param`.name

         override def signingStr(req: Http.Request[F, H4]): Try[String] = Try {
           val r: H4Request[F] = req.asInstanceOf[H4Request[F]]
           params.get(nameTk) match
              case Some(value: Rfc8941.SfString) =>
                  val prefix = s""""$lowercaseName";name=${value.canon}: """
                  r.uri.query.multiParams.get(value.asciiStr).map { vals =>
                    vals.map(v => prefix + v).mkString("\n")
                  } match
                    case Some(x) => x
                    case None => throw SelectorException(
                      s"No query parameter named ${value.canon} found. Suspicious."
                    )
              case _ => throw SelectorException(
                    s"selector $lowercaseName only takes one >${nameTk.canon}< parameter. Received " + params
                  )
         }
   end `@query-param`

   object `@status` extends AtComponent:
      type Msg = Http.Response[F, H4]

      // since this can only appear on the response it cannot have a req parameter
      override def optionalParamKeys: Set[Rfc8941.Token] = Set.empty

      override val name: String = "@status"

      override def mkSelector(p: Params) = new AtSelector[Http.Response[F, H4]]
        with SelectorOneLine[Http.Response[F, H4]]:
         def params: Params = p
         def name: String   = `@status`.name

         override protected def value(res: Http.Response[F, H4]): Try[String] =
            val hres: H4Response[F] = res.asInstanceOf[H4Response[F]]
            Success("" + hres.status.code)
   end `@status`

   //  new BasicMessageSelector[Http.Request[F, H4]] :
   //    override def lowercaseName: String = "@method"
   //
   //    override def specialForRequests: Boolean = true
   //
   //    override protected def signingStringValue(req: Http.Request[F, H4]): Try[String] =
   //      val r = req.asInstanceOf[HttpRequest]
   //      Success(r.method.value) // already uppercase