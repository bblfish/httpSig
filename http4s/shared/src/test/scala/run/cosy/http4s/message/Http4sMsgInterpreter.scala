
package run.cosy.http4s.message

import run.cosy.http.Http
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT
import cats.effect.IO
import org.http4s.Header
import org.typelevel.ci.CIString



class Http4sMsgInterpreter[F[_]] extends run.cosy.http.messages.HttpMsgInterpreter[F, Http4sTp.HT]:

   import run.cosy.http.messages.HttpMessageDB.{RequestStr, ResponseStr}

   override def asRequest(request: RequestStr): Http.Request[F, HT] =
       request.str.split(Array('\n', '\r')).toList match
          case head :: tail =>
            head.split("\\s+").nn.toList match
               case methd :: path :: httpVersion :: Nil =>
                 val Right(m) = org.http4s.Method.fromString(methd.nn): @unchecked
                 val Right(p) = org.http4s.Uri.fromString(path.nn): @unchecked
                 val Right(v) = org.http4s.HttpVersion.fromString(httpVersion.nn): @unchecked
                 val rawH: scala.List[org.http4s.Header.Raw] = parseHeaders(tail)
                 import org.http4s.Header.ToRaw.{given, *}
                 // we can ignore the body here, since that is actually not relevant to signing
                 org.http4s.Request[F](m, p, v, org.http4s.Headers(rawH))
                   .asInstanceOf[Http.Request[F, HT]] // <- todo: why needed?
               case _ => throw new Exception("Badly formed HTTP Request Command '" + head + "'")
          case _ => throw new Exception("Badly formed HTTP request")
  
   override def asResponse(response: ResponseStr): Http.Response[F, HT] =
       response.str.split(Array('\n', '\r')).nn.toList match
          case head :: tail =>
            head.split("\\s+").nn.toList match
               case httpVersion :: statusCode :: statusCodeStr :: Nil =>
                 val Right(status) =
                   org.http4s.Status.fromInt(Integer.parseInt(statusCode.nn)): @unchecked
                 val Right(version) = org.http4s.HttpVersion.fromString(httpVersion.nn): @unchecked
                 val rawH: scala.List[org.http4s.Header.Raw] = parseHeaders(tail)
                 import org.http4s.Header.ToRaw.{given, *}
                 org.http4s.Response[F](status, version, org.http4s.Headers(rawH))
                   .asInstanceOf[Http.Response[F, HT]] // <- todo: why needed?
               case _ => throw new Exception("Badly formed HTTP Response Command '" + head + "'")
          case _ => throw new Exception("Badly formed HTTP request")
  
   private def parseHeaders(nonMethodLines: List[String]): List[Header.Raw] =
      val (headers, body) = // we loose the body for the moment
         val i = nonMethodLines.indexOf("")
         if i < 0 then (nonMethodLines, List())
         else nonMethodLines.splitAt(i)
      val foldedHeaders = headers.foldLeft(List[String]()) { (ll, str) =>
        if str.contains(':') then str :: ll
        else (ll.head.nn.trim.nn + " " + str.trim.nn) :: ll.tail
      }
      val rawH: List[Header.Raw] = foldedHeaders.map(line => line.splitAt(line.indexOf(':'))).map {
        case (h, v) => Header.Raw(CIString(h.trim.nn), v.tail.trim.nn)
      }
      rawH.reverse
  
end Http4sMsgInterpreter