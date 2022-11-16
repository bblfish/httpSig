package run.cosy.http.messages

import run.cosy.http.Http
import run.cosy.http.messages.{AtComponents, ServerContext}
import run.cosy.http4s.Http4sTp.HT
import run.cosy.http4s.Http4sTp

class Http4sAtComponentSuite[F[_]] extends AtComponentSuite[F, Http4sTp.HT]:

  def at(using ServerContext): AtComponents[cats.Id, Http4sTp.HT] =
     val hat = new run.cosy.http4s.headers.Http4sAt[HT]
  def interp: HttpMsgInterpreter[cats.Id, Http4sTp.HT] = Http4sMsgInterpreter[]
