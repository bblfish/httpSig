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
     val req: Http.Request[F, H] = interp.asRequest(HttpMessageDB.`2.2.1_Method`)
     val method: AtSelector[Request[F, H]] = at.method()
     val sigStr = method.signingStr(req)
     assertEquals(sigStr, Success(""""@method": POST"""))
   }
