package run.cosy.http.message

import munit.CatsEffectSuite
import run.cosy.http.Http.Request
import run.cosy.http.headers.AtSelector
import run.cosy.http.{Http, auth}
import scala.util.Success

trait AtComponentSuite[F[_], H <: Http] extends CatsEffectSuite:
   def at : run.cosy.http.headers.AtComponents[F, H]
   def interp: HttpMsgInterpreter[F, H]

   test("@method") {
     val req: Http.Request[F, H] = interp.asRequest(HttpMessageDB.`2.2.1_Method_POST`)
     val method: AtSelector[Request[F, H]] = at.method()
     val sigStr = method.signingStr(req)
     assertEquals(sigStr, Success(""""@method": POST"""))
     val methodwithReq = at.method(true)
     assertEquals(methodwithReq.signingStr(req), Success(""""@method";req: POST"""))
   }
   
   /** select for requests that are give 1 line results */
   def reqSelect(selector: AtSelector[Request[F, H]], value: String)(using req: Request[F,H]): Unit =
     assertEquals(selector.signingStr(req), Success(value))
   
   test("2.2.1_POST on all @"){
     given  Http.Request[F, H] = interp.asRequest(HttpMessageDB.`2.2.1_Method_POST`)
     reqSelect(at.method(), "POST")
     reqSelect(at.method(true), "POST")
     
(
     "@authority" -> "www.example.com",
     "@authority;req" -> "www.example.com",
     "@request-target" -> "/path?param=value",
     "@request-target;req" -> "/path?param=value",
     "@path" -> "/path",
     "@path;req" -> "/path",
     "@query" -> "/path?param=value",
     "@query;req" -> "/path?param=value",
     "@query-param;name" -> ""
)
   }
   