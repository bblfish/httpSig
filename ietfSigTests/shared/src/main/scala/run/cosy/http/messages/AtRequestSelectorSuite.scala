package run.cosy.http.messages

import munit.CatsEffectSuite
import run.cosy.http.Http.{Request, Response}
import run.cosy.http.auth.ParsingExc
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.messages.{AServerContext, AtReqSelectors}
import run.cosy.http.{Http, auth}

import scala.util.{Success, Try}

/** Test all @selectors on requests
  * @tparam F
  *   the functor for Http
  * @tparam H
  *   the subtype of Http
  */
trait AtRequestSelectorSuite[H <: Http] extends CatsEffectSuite:
   def sel(sc: ServerContext): AtReqSelectors[H]
   def interp: TestHttpMsgInterpreter[H]
   def platform: HttpMsgPlatform

   test("@method") {
     val sc: AServerContext   = AServerContext("www.example.com", true)
     val req: Http.Request[H] = interp.asRequest(HttpMessageDB.`2.2.1_Method_POST`)
     val sigStr               = sel(sc).`@method`.signingStr(req)
     assertEquals(sigStr, Right(""""@method": POST"""))
   }
   test("@method with no server context makes no difference") {
     val sc: ServerContext    = NoServerContext
     val req: Http.Request[H] = interp.asRequest(HttpMessageDB.`2.2.1_Method_POST`)
     val sigStr               = sel(sc).`@method`.signingStr(req)
     assertEquals(sigStr, Right(""""@method": POST"""))
   }
   def reqFail(selector: RequestSelector[H], req: Request[H]): Unit =
      val result: Try[String] = selector.signingStr(req).toTry
      assert(result.isFailure, result)

   def resS(meth: String, res: String, attrs: (String, String)*): Either[ParsingExc, String] =
     Right {
       val ats     = for (k, v) <- attrs.toSeq yield s"""$k="$v""""
       val optAtts = if ats.isEmpty then "" else ats.mkString(";", ";", "")
       s""""$meth"$optAtts: $res"""
     }

   test("2.2.1_POST with all @xxx") {
     val req: Http.Request[H]    = interp.asRequest(HttpMessageDB.`2.2.1_Method_POST`)
     val sc: AServerContext      = AServerContext("www.example.com", true)
     val selF: AtReqSelectors[H] = sel(sc)

     assertEquals(selF.`@method`.signingStr(req), resS("@method", "POST"))
     assertEquals(selF.`@method`.signingStr(req), resS("@method", "POST"))
     assertEquals(
       selF.`@request-target`.signingStr(req),
       resS("@request-target", "/path?param=value")
     )
     assertEquals(selF.`@path`.signingStr(req), resS("@path", "/path"))
     assertEquals(selF.`@query`.signingStr(req), resS("@query", "?param=value"))
     assertEquals(
       selF.`@query-param`(sf"param").signingStr(req),
       resS("@query-param", "value", "name" -> "param")
     )

     assertEquals(selF.`@authority`.signingStr(req), resS("@authority", "www.example.com"))
     assertEquals(selF.`@scheme`.signingStr(req), resS("@scheme", "https"))
     assertEquals(
       selF.`@target-uri`.signingStr(req),
       resS("@target-uri", "https://www.example.com/path?param=value")
     )
   }
   
   test("2.2.1_POST with all @xxx") {
     val req: Http.Request[H] = interp.asRequest(HttpMessageDB.`2.2.1_Method_POST`)
     val sc: AServerContext = AServerContext("www.example.com", true)
     val selF: AtReqSelectors[H] = sel(sc)
   
     assertEquals(selF.`@method`.signingStr(req), resS("@method", "POST"))
     assertEquals(selF.`@method`.signingStr(req), resS("@method", "POST"))
     assertEquals(
       selF.`@request-target`.signingStr(req),
       resS("@request-target", "/path?param=value")
     )
     assertEquals(selF.`@path`.signingStr(req), resS("@path", "/path"))
     assertEquals(selF.`@query`.signingStr(req), resS("@query", "?param=value"))
     assertEquals(
       selF.`@query-param`(sf"param").signingStr(req),
       resS("@query-param", "value", "name" -> "param")
     )
   
     assertEquals(selF.`@authority`.signingStr(req), resS("@authority", "www.example.com"))
     assertEquals(selF.`@scheme`.signingStr(req), resS("@scheme", "https"))
     assertEquals(
       selF.`@target-uri`.signingStr(req),
       resS("@target-uri", "https://www.example.com/path?param=value")
     )
   }
   
   test("2.2.1_GET with all @xxx no server context") {
     val req: Http.Request[H]    = interp.asRequest(HttpMessageDB.`2.2.1_Method_GET`)
     val selF: AtReqSelectors[H] = sel(NoServerContext)
     import selF.*

     assertEquals(`@method`.signingStr(req), resS("@method", "GET"))
     assertEquals(`@request-target`.signingStr(req), resS("@request-target", "/path?param=value"))
     assertEquals(`@path`.signingStr(req), resS("@path", "/path"))
     assertEquals(`@query`.signingStr(req), resS("@query", "?param=value"))
     assertEquals(
       `@query-param`(sf"param").signingStr(req),
       resS("@query-param", "value", "name" -> "param")
     )

     assertEquals(`@authority`.signingStr(req), resS("@authority", "www.example.com"))
     // these stay the same because they are given in the ehader
     val schemeEither: Either[ParsingExc, String] = `@scheme`.signingStr(req)
     assert(schemeEither.isLeft,schemeEither)
     val targetEither = `@target-uri`.signingStr(req)
     assert(targetEither.isLeft, targetEither)
   }

   test("2.2.5_GET with long URL - with 2 query params - testing all @xxx") {
     val req: Http.Request[H] = interp.asRequest(HttpMessageDB.`2.2.5_GET_with_LongURL`)
     val sc: AServerContext =
       AServerContext("bblfish.net", false) // this should have no effect here
     val selF: AtReqSelectors[H] = sel(sc)
     import selF.*

     assertEquals(`@method`.signingStr(req), resS("@method", "GET"))
     assertEquals(
       `@request-target`.signingStr(req),
       resS("@request-target", "https://test.example.com/path?param=value&param=another")
     )
     assertEquals(`@path`.signingStr(req), resS("@path", "/path"))
     assertEquals(`@query`.signingStr(req), resS("@query", "?param=value&param=another"))
     assertEquals(
       `@query-param`(sf"param").signingStr(req),
       Right(""""@query-param";name="param": value
               |"@query-param";name="param": another""".stripMargin)
     )

     assertEquals(
       `@authority`.signingStr(req),
       resS("@authority", "test.example.com")
     ) // these stay the same because they are given in the ehader
     assertEquals(`@scheme`.signingStr(req), resS("@scheme", "https"))
     assertEquals(
       `@target-uri`.signingStr(req),
       resS("@target-uri", "https://test.example.com/path?param=value&param=another")
     )
   }

   test("2.2.5_Options (for akka) testing all @xxx") {
     val req: Http.Request[H] = interp.asRequest(HttpMessageDB.`2.2.5_OPTIONS_4akka`)
     val sc = AServerContext("bblfish.net", false) // this should have no effect here
     val selF: AtReqSelectors[H] = sel(sc)
     import selF.*

     assertEquals(`@method`.signingStr(req), resS("@method", "OPTIONS"))
     if platform != HttpMsgPlatform.Akka then
        assertEquals(
          `@request-target`.signingStr(req),
          resS("@request-target", "https://www.example.com:443")
        )
     else
        println("@request-target on a full URL is not consistent on akka with http4s")
     assertEquals(
       `@path`.signingStr(req),
       resS("@path", "")
     ) // todo: is this correct? it probably is, if the following is
     assertEquals(`@query`.signingStr(req), resS("@query", "?"))
     // these stay the same because they are given in the ehader
     assertEquals(
       `@authority`.signingStr(req),
       resS("@authority", "www.example.com")
     )
     assertEquals(`@scheme`.signingStr(req), resS("@scheme", "https"))
     assertEquals(`@target-uri`.signingStr(req), resS("@target-uri", "https://www.example.com"))
   }

   test("2.2.5_Options (for akka) testing all @xxx with no server context (no need)") {
     val req: Http.Request[H] = interp.asRequest(HttpMessageDB.`2.2.5_OPTIONS_4akka`)
     val selF: AtReqSelectors[H] = sel(NoServerContext)
     import selF.*
   
     assertEquals(`@method`.signingStr(req), resS("@method", "OPTIONS"))
     if platform != HttpMsgPlatform.Akka then
       assertEquals(
         `@request-target`.signingStr(req),
         resS("@request-target", "https://www.example.com:443")
       )
     else
       println("@request-target on a full URL is not consistent on akka with http4s")
     assertEquals(
       `@path`.signingStr(req),
       resS("@path", "")
     ) // todo: is this correct? it probably is, if the following is
     assertEquals(`@query`.signingStr(req), resS("@query", "?"))
     // these stay the same because they are given in the ehader
     assertEquals(
       `@authority`.signingStr(req),
       resS("@authority", "www.example.com")
     )
     assertEquals(`@scheme`.signingStr(req), resS("@scheme", "https"))
     assertEquals(`@target-uri`.signingStr(req), resS("@target-uri", "https://www.example.com"))
   }
   /** TODO integrate errors depending on platform into munit
     * @gabro
     *   wrote: > @bblfish this is a good use case for test transforms. You can define a tag, and
     *   write a transform that applies to tests with that tag. You can draw some inspiration from
     *   the flaky test transform in munit >
     *   https://github.com/scalameta/munit/blob/main/munit/shared/src/main/scala/munit/TestTransforms.scala
     */

   test("2.2.5_CONNECT") {
     try
        val req: Http.Request[H] = interp.asRequest(HttpMessageDB.`2.2.5_CONNECT`)
        val sc: AServerContext =
          AServerContext("bblfish.net", false) // this should have no effect here
        val selF: AtReqSelectors[H] = sel(sc)
        import selF.*
        assertEquals(`@method`.signingStr(req), resS("@method", "CONNECT"))
        assertEquals(
          `@request-target`.signingStr(req),
          resS("@request-target", "www.example.com:80")
        )
     catch case MessageInterpreterError(HttpMsgPlatform.Akka, msg) => println(msg)

   }

   test("2.2.5_Options") {
     try
        val req: Http.Request[H] = interp.asRequest(HttpMessageDB.`2.2.5_OPTIONS`)
        val sc: AServerContext =
          AServerContext("bblfish.net", false) // this should have no effect here
        val selF: AtReqSelectors[H] = sel(sc)
        import selF.*

        assertEquals(`@method`.signingStr(req), resS("@method", "OPTIONS"))
        assertEquals(`@request-target`.signingStr(req), resS("@request-target", "*"))
     catch case MessageInterpreterError(HttpMsgPlatform.Akka, msg) => println(msg)

   }

   test("2.2.7_Query") {
     val req: Http.Request[H] = interp.asRequest(HttpMessageDB.`2.2.7_Query`)
     val sc: AServerContext =
       AServerContext("bblfish.net", false) // this should have no effect here
     val selF: AtReqSelectors[H] = sel(sc)
     import selF.*
     if platform != HttpMsgPlatform.Akka then
        assertEquals(`@query`.signingStr(req), resS("@query", "?param=value&foo=bar&baz=bat%2Dman"))
     else println("2.2.7 Query does not work with out Akka test suite.")
     assertEquals(`@method`.signingStr(req), resS("@method", "POST"))
     assertEquals(
       `@request-target`.signingStr(req),
       resS("@request-target", "/path?param=value&foo=bar&baz=bat%2Dman")
     )
   }

   test("2.2.8_Query_String") {
     val req: Http.Request[H] = interp.asRequest(HttpMessageDB.`2.2.7_Query_String`)
     val sc: AServerContext = AServerContext("cosy.run", true) // this should have no effect here
     val selF: AtReqSelectors[H] = sel(sc)
     import selF.*
   
     assertEquals(`@method`.signingStr(req), resS("@method", "HEAD"))
     assertEquals(`@request-target`.signingStr(req), resS("@request-target", "/path?queryString"))
     assertEquals(`@path`.signingStr(req), resS("@path", "/path"))
     // todo: is this correct? it probably is, if the following is
     assertEquals(`@query`.signingStr(req), resS("@query", "?queryString"))
     assertEquals(
       `@query-param`(sf"queryString").signingStr(req),
       Right(""""@query-param";name="queryString": """)
     )
     reqFail(`@query-param`(sf"q"), req)
     // we use the server context
     assertEquals(`@authority`.signingStr(req), resS("@authority", "cosy.run"))
     assertEquals(`@scheme`.signingStr(req), resS("@scheme", "https"))
     assertEquals(
       `@target-uri`.signingStr(req),
       resS("@target-uri", "https://cosy.run/path?queryString")
     )
   
   }

   test("2.2.8_Query_String with no server context") {
     val req: Http.Request[H] = interp.asRequest(HttpMessageDB.`2.2.7_Query_String`)
     val selF: AtReqSelectors[H] = sel(NoServerContext)
     import selF.*

     assertEquals(`@method`.signingStr(req), resS("@method", "HEAD"))
     assertEquals(`@request-target`.signingStr(req), resS("@request-target", "/path?queryString"))
     assertEquals(`@path`.signingStr(req), resS("@path", "/path"))
     // todo: is this correct? it probably is, if the following is
     assertEquals(`@query`.signingStr(req), resS("@query", "?queryString"))
     assertEquals(
       `@query-param`(sf"queryString").signingStr(req),
       Right(""""@query-param";name="queryString": """)
     )
     reqFail(`@query-param`(sf"q"), req)
     // we use the server context
     val authEither = `@authority`.signingStr(req)
     assert(authEither.isLeft,authEither)
     val schemeEither = `@scheme`.signingStr(req)
     assert(schemeEither.isLeft, schemeEither)
     val targetEither = `@target-uri`.signingStr(req)
     assert(targetEither.isLeft, targetEither)
   }

   test("2.2.8_Query_Parameters") {
     val req: Http.Request[H] = interp.asRequest(HttpMessageDB.`2.2.8_Query_Parameters`)
     val sc: AServerContext =
       AServerContext("bblfish.net", false) // this should have no effect here
     val selF: AtReqSelectors[H] = sel(sc)
     import selF.*

     assertEquals(`@method`.signingStr(req), resS("@method", "GET"))
     assertEquals(
       `@request-target`.signingStr(req),
       resS("@request-target", "/path?param=value&foo=bar&baz=batman&qux=")
     )
     assertEquals(
       `@path`.signingStr(req),
       resS("@path", "/path")
     ) // todo: is this correct? it probably is, if the following is
     assertEquals(`@query`.signingStr(req), resS("@query", "?param=value&foo=bar&baz=batman&qux="))
     assertEquals(
       `@query-param`(sf"param").signingStr(req),
       Right(""""@query-param";name="param": value""")
     )
     // we use the server context
     assertEquals(`@authority`.signingStr(req), resS("@authority", "bblfish.net"))
     assertEquals(`@scheme`.signingStr(req), resS("@scheme", "http"))
     assertEquals(
       `@target-uri`.signingStr(req),
       resS("@target-uri", "http://bblfish.net/path?param=value&foo=bar&baz=batman&qux=")
     )

   }

end AtRequestSelectorSuite
