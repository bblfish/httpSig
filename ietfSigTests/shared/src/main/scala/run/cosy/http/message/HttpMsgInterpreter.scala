package run.cosy.http.message

import run.cosy.http.Http

trait HttpMsgInterpreter[F[_], H <: Http]:

   import run.cosy.http.message.HttpMessageDB.RequestStr

   def asRequest(header: RequestStr): Http.Request[F, H]
