package run.cosy.http.messages

import run.cosy.http.Http

import scala.util.Either
import scala.util.matching.Regex

enum Platform:
   case Akka
   case Http4s

case class MessageInterpreterError(platform: Platform, message: String) extends Exception

trait HttpMsgInterpreter[F[_], H <: Http]:

   import run.cosy.http.messages.HttpMessageDB.{RequestStr,ResponseStr}

   def asRequest(header: RequestStr): Http.Request[F, H]

   def asResponse(header: ResponseStr): Http.Response[F,H]
   
   
object HttpMsgInterpreter:
  val AttVals: Regex = "^([^:]+):(.*)$".r
  val VerticalTAB: Char = "\u000B".head
  
  def headersAndBody(nonMethodLines: List[String]): (List[(String, String)], String) =
    
    val (headrs, rest) = nonMethodLines.splitAt {
       val i = nonMethodLines.indexOf("")
       if i < 0 then nonMethodLines.length+1 else i
    }
    //remove obsolete line folding
    val hdrsNoOLF = headrs.foldLeft(List[String]()) { case (l, str) =>
       str.headOption match
         case Some(' ') | Some(VerticalTAB) if l.size > 0 =>
           (l.head.trim.nn + " " + str.trim)::l.tail
         case _ => str::l
    }.reverse

    //remove the empty line from the body
    val body = if rest.size > 0 then rest.tail else rest
    val attVals = hdrsNoOLF.map { (h: String) =>
      h match
        case AttVals(h,v) => (h.trim.nn, v.trim.nn)
    }
    (attVals, body.mkString("\n"))