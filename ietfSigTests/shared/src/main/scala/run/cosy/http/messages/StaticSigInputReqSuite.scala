package run.cosy.http.messages

import munit.CatsEffectSuite
import run.cosy.http.Http
import run.cosy.http.auth.MessageSignature
import run.cosy.http.messages.Selectors.*
import run.cosy.http.messages.TestHttpMsgInterpreter

/** This test suite looks at statically built SigInput
  * requests using functions. This is useful for
  * clients writing signatures.
  * */
class StaticSigInputReqSuite[F[_], H <: Http](
 msgSig: MessageSignature[F, H],
 hsel: HeaderSelectors[F, H],
 atSel: AtSelectors[F, H],
 interpret: TestHttpMsgInterpreter[F, H]
) extends CatsEffectSuite:
   val hds = HeaderIds
   
   
   import atSel.*
   import hsel.RequestHd.*
   val DB = HttpMessageDB
   
   test("develop api for using static SigInput selector") {
      val req = interpret.asRequest(DB.`2.4_Req_Ex`)
      val rsel: List[RequestSelector[F, H]] =
         List(`@method`, `@authority`, `@path`, `content-digest`(Raw), `content-length`(Raw), `content-type`(Raw))
   
   }