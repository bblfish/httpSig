package run.cosy.http.messages

import run.cosy.http.Http
import scala.util.Either

enum Platform:
   case Akka
   case Http4s

case class MessageInterpreterError(platform: Platform, message: String) extends Exception

trait HttpMsgInterpreter[F[_], H <: Http]:

   import run.cosy.http.messages.HttpMessageDB.{RequestStr,ResponseStr}

   def asRequest(header: RequestStr): Http.Request[F, H]

   def asResponse(header: ResponseStr): Http.Response[F,H]