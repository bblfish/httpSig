package run.cosy.http4s.messages

import run.cosy.http.Http
import run.cosy.http.headers.Rfc8941.SfString
import run.cosy.http.messages.Component.nameTk
import run.cosy.http.messages.{AtComponents, AtSelector, ServerContext}
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT as H4
import run.cosy.http4s.messages.Http4sAt

class AtComponents4S[F[_]](using ServerContext)
    extends AtComponents[F, H4] with Http4sAt[F]:
   import scala.language.implicitConversions

   def method(onReq: Boolean = false): AtSelector[Http.Request[F, H4]] =
     `@method`(onReq).get

   def authority(onReq: Boolean = false): AtSelector[Http.Request[F, H4]] =
     `@authority`()(onReq).get

   /** best not used if not HTTP1.1 */
   def requestTarget(onReq: Boolean = false): AtSelector[Http.Request[F, H4]] =
     `@request-target`(onReq).get

   def path(onReq: Boolean = false): AtSelector[Http.Request[F, H4]] =
     `@path`(onReq).get

   def query(onReq: Boolean = false): AtSelector[Http.Request[F, H4]] =
     `@query`(onReq).get

   def queryParam(name: String, onReq: Boolean = false): AtSelector[Http.Request[F, H4]] =
     `@query-param`(toP(onReq) + (nameTk -> SfString(name))).get

   // requiring knowing context info on server
   def scheme(onReq: Boolean = false): AtSelector[Http.Request[F, H4]] =
     `@scheme`()(onReq).get

   def targetUri(onReq: Boolean = false): AtSelector[Http.Request[F, H4]] =
     new `@target-uri`()(onReq).get

   // on responses
   def status(): AtSelector[Http.Response[F, H4]] =
     `@status`().get

end AtComponents4S
