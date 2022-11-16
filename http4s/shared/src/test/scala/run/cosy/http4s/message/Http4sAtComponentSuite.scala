package run.cosy.http.messages

import run.cosy.http.Http
import run.cosy.http.messages.{AtComponents, ServerContext}
import run.cosy.http4s.Http4sTp.HT
import run.cosy.http4s.Http4sTp

class AkkaAtComponentSuite[F[_]] extends AtComponentSuite[F, Http4sTp.HT]:

  def at(using ServerContext): AtComponents[cats.Id, Http4sTp.HT] =
    new run.cosy.http4s.headers
  def interp: HttpMsgInterpreter[cats.Id, Http4sTp.HT] = Http4sMsgInterpreter[]
