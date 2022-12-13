package run.cosy.http.dummy

import run.cosy.http.messages.TestHttpMsgInterpreter
import run.cosy.http.Http.Request
import run.cosy.http.messages.HttpMessageDB.RequestStr
import run.cosy.http.messages.HttpMessageDB
import run.cosy.http.Http.Response
import run.cosy.http.messages.HttpMessageDB.ResponseStr
import org.typelevel.ci.*

class DummyMsgInterpreter extends TestHttpMsgInterpreter[DummyHttp.HT]:
   val VerticalTAB: Char           = "\u000B".head

   def asRequest(header: RequestStr): Request[DummyHttp.HT] =
      val res = header match
         case HttpMessageDB.`§2.1_HeaderField` => Seq[(CIString, String)](
             ci"Host"              -> "www.example.com",
             ci"Date"              -> "Sat, 07 Jun 2014 20:51:35 GMT",
             ci"X-OWS-Header"      -> """     Leading and trailing whitespace.               """,
             ci"X-Obs-Fold-Header" -> " Obsolete    \u000B line folding.".stripMargin,
             ci"Cache-Control"     -> "max-age=60",
             ci"Example-Header"    -> "value, with, lots",
             ci"X-Empty-Header"    -> "",
             ci"Cache-Control"     -> "   must-revalidate",
             ci"Example-Dict"      -> "a=1,    b=2;x=1;y=2,   c=(a   b   c) ",
             ci"Example-Dict"      -> "  d",
             ci"Example-Header"    -> "of, commas"
           )
      removeObsLF(res).asInstanceOf[Request[DummyHttp.HT]]
   end asRequest
   def asResponse(header: ResponseStr): Response[DummyHttp.HT] = ???


   def removeObsLF(msg: Seq[(CIString, String)]): Seq[(CIString, String)] = msg.map { (k, v) =>
      (k, v.split(VerticalTAB).map(_.trim).mkString(" "))
   }

end DummyMsgInterpreter
