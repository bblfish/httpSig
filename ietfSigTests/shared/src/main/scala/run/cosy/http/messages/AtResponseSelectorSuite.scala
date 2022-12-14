package run.cosy.http.messages

import munit.CatsEffectSuite
import run.cosy.http.Http
import run.cosy.http.auth.ParsingExc

trait AtResponseSelectorSuite[H <: Http] extends CatsEffectSuite:
  def sel(sc: ServerContext): AtResSelectors[H]
  def interp: TestHttpMsgInterpreter[H]

   def resS(meth: String, res: String, attrs: (String, String)*): Either[ParsingExc, String] = Right {
     val ats = for (k, v) <- attrs.toSeq yield s"""$k="$v""""
     val optAtts = if ats.isEmpty then "" else ats.mkString(";", ";", "")
     s""""$meth"$optAtts: $res"""
   }
   
   test("2.2.9 Status Code") {
    
    // we still need these
    val res: Http.Response[H] = interp.asResponse(HttpMessageDB.`2.2.9_Status_Code`)
    val sc: ServerContext = ServerContext("bblfish.net", false) // this should have no effect here
    val selF: AtResSelectors[H] = sel(sc)
    import selF.*

    // because of typesafety we can only make one test here
    assertEquals(`@status`.signingStr(res), resS("@status", "200"))
    assertEquals(`@status`.signingStr(res), Right(""""@status": 200"""))
  }

