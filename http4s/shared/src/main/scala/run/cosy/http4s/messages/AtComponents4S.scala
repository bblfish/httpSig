package run.cosy.http4s.messages

import run.cosy.http.Http
import run.cosy.http.messages.{AtComponents, AtSelector, ServerContext}
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT as H4

class AtComponents4S[F[_]](using ServerContext)
  extends AtComponents[F, H4] with Http4sAt[F]:

  def method(onReq: Boolean = false): AtSelector[Http.Request[F, H4]] =
  
  
  
  def authority(onReq: Boolean = false): AtSelector[Http.Request[F, H4]]

  /** best not used if not HTTP1.1 */
  def requestTarget(onReq: Boolean = false): AtSelector[Http.Request[F, H4]]
  def path(onReq: Boolean = false): AtSelector[Http.Request[F, H4]]
  def query(onReq: Boolean = false): AtSelector[Http.Request[F, H4]]
  def queryParam(name: String, onReq: Boolean = false): AtSelector[Http.Request[F, H4]]

  // requiring knowing context info on server
  def scheme(onReq: Boolean = false): AtSelector[Http.Request[F, H4]]
  def targetUri(onReq: Boolean = false): AtSelector[Http.Request[F, H4]]

  // on responses
  def status(): AtSelector[Http.Response[F, H4]]

end