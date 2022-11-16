package run.cosy.http.messages

import munit.CatsEffectSuite
import run.cosy.http.Http.{Request, Response}
import run.cosy.http.messages.AtSelector
import run.cosy.http.messages.{AtComponents, ServerContext}
import run.cosy.http.{Http, auth}

import scala.util.Success
import scala.util.Try

trait AtComponentSuite[F[_], H <: Http] extends CatsEffectSuite:
   def at(using ServerContext): AtComponents[F, H]
   def interp: HttpMsgInterpreter[F, H]

   test("@method") {
     given ServerContext                   = ServerContext("www.example.com", true)
     val req: Http.Request[F, H]           = interp.asRequest(HttpMessageDB.`2.2.1_Method_POST`)
     val method: AtSelector[Request[F, H]] = at.method()
     val sigStr                            = method.signingStr(req)
     assertEquals(sigStr, Success(""""@method": POST"""))
     val methodwithReq = at.method(true)
     assertEquals(methodwithReq.signingStr(req), Success(""""@method";req: POST"""))
   }

   /** select for requests that are give 1 line results */
   def reqS(selector: AtSelector[Request[F, H]], value: String)(using req: Request[F, H]): Unit =
     assertEquals(selector.signingStr(req), Success(selector.identifier + value))

   def reqFail(selector: AtSelector[Request[F, H]])(using req: Request[F, H]): Unit =
      val result: Try[String] = selector.signingStr(req)
      assert(result.isFailure, result)

   /** sometimes we want to test the full answer coming back, especially when the answer has
     * multiple lines
     */
   def rawReqSelector(selector: AtSelector[Request[F, H]], value: String)(using
       req: Request[F, H]
   ): Unit =
     assertEquals(selector.signingStr(req), Success(value))

   /** sometimes we want to test the full answer coming back, especially when the answer has
     * multiple lines
     */
   def rawResponseSelector(selector: AtSelector[Response[F, H]], value: String)(using
       res: Response[F, H]
   ): Unit =
     assertEquals(selector.signingStr(res), Success(value))

   def resS(selector: AtSelector[Http.Response[F, H]], value: String)(using
       req: Http.Response[F, H]
   ): Unit =
     assertEquals(selector.signingStr(req), Success(selector.identifier + value))

   test("2.2.1_POST with all @xxx") {
     given Http.Request[F, H] = interp.asRequest(HttpMessageDB.`2.2.1_Method_POST`)
     given ServerContext      = ServerContext("www.example.com", true)

     reqS(at.method(), "POST")
     reqS(at.method(true), "POST")
     reqS(at.requestTarget(), "/path?param=value")
     reqS(at.requestTarget(true), "/path?param=value")
     reqS(at.path(), "/path")
     reqS(at.path(true), "/path")
     reqS(at.query(), "?param=value")
     reqS(at.query(true), "?param=value")
     reqS(at.queryParam(name = "param"), "value")
     reqS(at.queryParam(name = "param", true), "value")

     reqS(at.authority(), "www.example.com")
     reqS(at.authority(true), "www.example.com")
     reqS(at.scheme(), "https")
     reqS(at.scheme(true), "https")
     reqS(at.targetUri(), "https://www.example.com/path?param=value")
     reqS(at.targetUri(true), "https://www.example.com/path?param=value")
   }

   test("2.2.1_GET with all @xxx") {
     given Http.Request[F, H] = interp.asRequest(HttpMessageDB.`2.2.1_Method_GET`)
     given ServerContext      = ServerContext("bblfish.net", false)

     reqS(at.method(), "GET")
     reqS(at.method(true), "GET")
     reqS(at.requestTarget(), "/path?param=value")
     reqS(at.requestTarget(true), "/path?param=value")
     reqS(at.path(), "/path")
     reqS(at.path(true), "/path")
     reqS(at.query(), "?param=value")
     reqS(at.query(true), "?param=value")
     reqS(at.queryParam(name = "param"), "value")
     reqS(at.queryParam(name = "param", true), "value")

     reqS(
       at.authority(),
       "www.example.com"
     ) // these stay the same because they are given in the ehader
     reqS(at.authority(true), "www.example.com")
     reqS(at.scheme(), "http")
     reqS(at.scheme(true), "http")
     reqS(at.targetUri(), "http://www.example.com/path?param=value")
     reqS(at.targetUri(true), "http://www.example.com/path?param=value")
   }

   test("2.2.5_GET with long URL - with 2 query params - testing all @xxx") {
     given Http.Request[F, H] = interp.asRequest(HttpMessageDB.`2.2.5_GET_with_LongURL`)
     given ServerContext = ServerContext("bblfish.net", false) // this should have no effect here

     reqS(at.method(), "GET")
     reqS(at.method(true), "GET")
     reqS(at.requestTarget(), "https://test.example.com/path?param=value&param=another")
     reqS(at.requestTarget(true), "https://test.example.com/path?param=value&param=another")
     reqS(at.path(), "/path")
     reqS(at.path(true), "/path")
     reqS(at.query(), "?param=value&param=another")
     reqS(at.query(true), "?param=value&param=another")
     rawReqSelector(
       at.queryParam(name = "param"),
       """"@query-param";name="param": value
         |"@query-param";name="param": another""".stripMargin
     )
     rawReqSelector(
       at.queryParam(name = "param", true),
       """"@query-param";req;name="param": value
         |"@query-param";req;name="param": another""".stripMargin
     )

     reqS(
       at.authority(),
       "test.example.com"
     ) // these stay the same because they are given in the ehader
     reqS(at.authority(true), "test.example.com")
     reqS(at.scheme(), "https")
     reqS(at.scheme(true), "https")
     reqS(at.targetUri(), "https://test.example.com/path?param=value&param=another")
     reqS(at.targetUri(true), "https://test.example.com/path?param=value&param=another")
   }

   test("2.2.5_Options (for akka) testing all @xxx") {
     given Http.Request[F, H] = interp.asRequest(HttpMessageDB.`2.2.5_OPTIONS_4akka`)
     given ServerContext = ServerContext("bblfish.net", false) // this should have no effect here

     reqS(at.method(), "OPTIONS")
     reqS(at.method(true), "OPTIONS")
     reqS(at.requestTarget(), "https://www.example.com:443")
     reqS(at.requestTarget(true), "https://www.example.com:443")
     reqS(at.path(), "") // todo: is this correct? it probably is, if the following is
     reqS(at.path(true), "")
     reqS(at.query(), "?")
     reqS(at.query(true), "?")
// these stay the same because they are given in the ehader
     reqS(
       at.authority(),
       "www.example.com"
     ) 
     reqS(at.authority(true), "www.example.com")
     reqS(at.scheme(), "https")
     reqS(at.scheme(true), "https")
     reqS(at.targetUri(), "https://www.example.com")
     reqS(at.targetUri(true), "https://www.example.com")
   }

   test("2.2.5_CONNECT") {
     try
       given Http.Request[F, H] = interp.asRequest(HttpMessageDB.`2.2.5_CONNECT`)
       given ServerContext = ServerContext("bblfish.net", false) // this should have no effect here
       reqS(at.method(), "CONNECT")
       reqS(at.method(true), "CONNECT")
       reqS(at.requestTarget(), "www.example.com:80")
       reqS(at.requestTarget(true), "www.example.com:80")

     catch case MessageInterpreterError(Platform.Akka, msg) => println(msg)

   }

   test("2.2.5_Options") {
     try
        given Http.Request[F, H] = interp.asRequest(HttpMessageDB.`2.2.5_OPTIONS`)
        given ServerContext = ServerContext("bblfish.net", false) // this should have no effect here
        reqS(at.method(), "OPTIONS")
        reqS(at.method(true), "OPTIONS")
        reqS(at.requestTarget(), "*")
        reqS(at.requestTarget(true), "*")
     catch case MessageInterpreterError(Platform.Akka, msg) => println(msg)

   }

   test("2.2.8_Query_String") {
     given Http.Request[F, H] = interp.asRequest(HttpMessageDB.`2.2.7_Query_String`)
     given ServerContext = ServerContext("cosy.run", true) // this should have no effect here

     reqS(at.method(), "HEAD")
     reqS(at.method(true), "HEAD")
     reqS(at.requestTarget(), "/path?queryString")
     reqS(at.requestTarget(true), "/path?queryString")
     reqS(at.path(), "/path")
     // todo: is this correct? it probably is, if the following is
     reqS(at.path(true), "/path")
     reqS(at.query(), "?queryString")
     reqS(at.query(true), "?queryString")
     rawReqSelector(
       at.queryParam(name = "queryString"),
       """"@query-param";name="queryString": """
     )
     rawReqSelector(
       at.queryParam(name = "queryString", true),
       """"@query-param";req;name="queryString": """
     )
     reqFail(at.queryParam("q"))
     reqFail(at.queryParam("q",true))
     // we use the server context
     reqS(at.authority(), "cosy.run") 
     reqS(at.authority(true), "cosy.run")
     reqS(at.scheme(), "https")
     reqS(at.scheme(true), "https")
     reqS(at.targetUri(), "https://cosy.run/path?queryString")
     reqS(at.targetUri(true), "https://cosy.run/path?queryString")

   }
   
   test("2.2.8_Query_Parameters") {
     given Http.Request[F, H] = interp.asRequest(HttpMessageDB.`2.2.8_Query_Parameters`)
     given ServerContext = ServerContext("bblfish.net", false) // this should have no effect here

     reqS(at.method(), "GET")
     reqS(at.method(true), "GET")
     reqS(at.requestTarget(), "/path?param=value&foo=bar&baz=batman&qux=")
     reqS(at.requestTarget(true), "/path?param=value&foo=bar&baz=batman&qux=")
     reqS(at.path(), "/path") // todo: is this correct? it probably is, if the following is
     reqS(at.path(true), "/path")
     reqS(at.query(), "?param=value&foo=bar&baz=batman&qux=")
     reqS(at.query(true), "?param=value&foo=bar&baz=batman&qux=")
     rawReqSelector(
       at.queryParam(name = "param"),
       """"@query-param";name="param": value""".stripMargin
     )
     rawReqSelector(
       at.queryParam(name = "param", true),
       """"@query-param";req;name="param": value""".stripMargin
     )
     // we use the server context
     reqS(at.authority(), "bblfish.net")
     reqS(at.authority(true), "bblfish.net")
     reqS(at.scheme(), "http")
     reqS(at.scheme(true), "http")
     reqS(at.targetUri(), "http://bblfish.net/path?param=value&foo=bar&baz=batman&qux=")
     reqS(at.targetUri(true), "http://bblfish.net/path?param=value&foo=bar&baz=batman&qux=")

   }

   test("2.2.9 Response Code") {
     //we still need these
     given Http.Response[F, H] = interp.asResponse(HttpMessageDB.`2.2.9_Status_Code`)
     given ServerContext = ServerContext("bblfish.net", false) // this should have no effect here

     //because of typesafety we can only make one test here
     resS(at.status(), "200")
     rawResponseSelector(
       at.status(),
       """"@status": 200"""
     )

   }

end AtComponentSuite
