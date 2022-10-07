/*
 * Copyright 2021 Henry Story
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package run.cosy.http.auth

import scala.util.Success
import akka.http.scaladsl.model.HttpMethods.*
import akka.http.scaladsl.model.headers.*
import akka.http.scaladsl.model.{
  ContentTypes,
  DateTime,
  HttpEntity,
  HttpMethods,
  HttpRequest,
  HttpResponse,
  MediaTypes,
  StatusCodes,
  Uri
}
import run.cosy.akka.http.AkkaTp
import run.cosy.akka.http.headers.{
  AkkaDictSelector,
  AkkaMessageSelectors,
  Signature,
  UntypedAkkaSelector,
  `Signature-Input`
}
import run.cosy.http.headers.{DictSelector, Rfc8941, SigInput, SigInputs, Signatures}
import run.cosy.http.auth.HttpMessageSigningSuite
import run.cosy.http.Http
import run.cosy.http.Http.{Message, Request, Response}
import run.cosy.http.utils.StringUtils.*
import run.cosy.akka.http.headers.`Signature-Input`
import run.cosy.http.headers.Rfc8941.*
import run.cosy.http.headers.MessageSelector

import scala.language.implicitConversions
import run.cosy.akka.http.AkkaTp.H4

import cats.effect.{Async, IO}

class AkkaHttpMessageSigningSuite extends HttpMessageSigningSuite[IO, H4]:
   given pem: bobcats.util.PEMUtils                       = bobcats.util.BouncyJavaPEMUtils
   given ops: run.cosy.http.HttpOps[H4]                   = run.cosy.akka.http.AkkaTp.httpOps
   given sigSuite: run.cosy.http.auth.SigningSuiteHelpers = new SigningSuiteHelpers
   val selectorsSecure   = AkkaMessageSelectors(true, Uri.Host("bblfish.net"), 443)
   val selectorsInSecure = AkkaMessageSelectors(false, Uri.Host("bblfish.net"), 80)
   val messageSignature: run.cosy.http.auth.MessageSignature[H4] = AkkaHttpMessageSignature
   import selectorsSecure.*

   /** todo: with Akka it is possible to automatically convert a String to an HttpMessage, but it
     * requires more work, a different test suite potentially, etc... That will be needed for large
     * test suites though. See
     *   1. akka.http.impl.engine.parsing.RequestParserSpec
     *   1. akka.http.impl.engine.parsing.ResponseParserSpec
     */
   @throws[Exception]
   def toRequest(request: HttpMessage): Request[IO, H4] =
     request match
        case `§2.1.2_HeaderField` => HttpRequest(
            method = HttpMethods.GET,
            uri = Uri("/xyz"),
            headers = Seq(
              Host("www.example.com"),
              Date(DateTime(2014, 6, 7, 20, 51, 35)),
              RawHeader("X-OWS-Header", "   Leading and trailing whitespace.   "),
              RawHeader(
                "X-Obs-Fold-Header",
                """Obsolete
                  |      line folding. """.stripMargin
              ),
              RawHeader(
                "X-Example",
                """Example header
                  |   with some whitespace.  """.stripMargin
              ),
              `Cache-Control`(CacheDirectives.`max-age`(60)),
              RawHeader("X-Empty-Header", ""),
              `Cache-Control`(CacheDirectives.`must-revalidate`),
              RawHeader("X-Dictionary", "   a=1,    b=2;x=1;y=2,   c=(a   b   c)")
            )
          )
        case `§2.2.x_Request` => HttpRequest(
            HttpMethods.POST,
            Uri("/path?param=value"),
            headers = Seq(Host("www.example.com"))
          )
        case `§2.2.3_Request_AbsoluteURI` =>
          HttpRequest(HttpMethods.GET, Uri("http://www.example.com"))
        case `§2.2.3_Request_AbsoluteURI_2` =>
          HttpRequest(HttpMethods.GET, Uri("http://www.example.com/a"))
        case `§2.2.6_Request_AbsoluteURI` =>
          HttpRequest(HttpMethods.GET, Uri("http://www.example.com/a/b/"))
        case `§2.2.x_Request_Absolute` =>
          HttpRequest(HttpMethods.GET, Uri("https://www.example.com/path?param=value"))
        case `§2.2.6_Request_rel` =>
          HttpRequest(HttpMethods.POST, Uri("/a/b/"), Seq(Host("www.example.com")))
        case `§2.2.6_Request_Options` =>
          HttpRequest(HttpMethods.OPTIONS, Uri("*"), headers = Seq(Host("server.example.com")))
        case `§2.2.6_CONNECT` => HttpRequest(
            HttpMethods.CONNECT,
            Uri("", Uri.Authority(Uri.Host("www.example.com"), 80), Uri.Path.Empty, None, None)
          ).withHeaders(Host(Uri.Host("www.example.com")))
        case `§2.2.8_Request_1` => HttpRequest(
            HttpMethods.POST,
            uri = Uri("/path?param=value&foo=bar&baz=batman"),
            headers = Seq(Host(Uri.Host("www.example.com")))
          )
        case `§2.2.8_Request_2` => HttpRequest(
            HttpMethods.POST,
            uri = Uri("/path?queryString"),
            headers = Seq(Host(Uri.Host("www.example.com")))
          )
        case `§2.2.8_Request_3` => HttpRequest(HttpMethods.GET, Uri("/path?"))
            .withHeaders(Host(Uri.Host("www.example.com")))
        case `§2.2.9_Request` =>
          HttpRequest(HttpMethods.GET, Uri("/path?param=value&foo=bar&baz=batman&qux="))
            .withHeaders(Host(Uri.Host("www.example.com")))
        case `§2.2.11_Request` => HttpRequest(
            POST,
            Uri("/foo?param=value&pet=dog"),
            headers = Seq(
              Host("example.com"),
              Date(DateTime(2021, 4, 20, 2, 7, 55)),
              `Signature-Input`(SigInputs(
                Rfc8941.Token("sig1"),
                SigInput(IList(
                  `@authority`.sf,
                  `content-type`.sf
                )(
                  Param("created", SfInt(1618884475)),
                  Param("keyid", SfString("test-key-rsa-pss"))
                )).get
              )),
              run.cosy.akka.http.headers.Signature(Signatures(
                Token("sig1"),
                """KuhJjsOKCiISnKHh2rln5ZNIrkRvue0DSu5rif3g7ckTbbX7C4\
					  |  Jp3bcGmi8zZsFRURSQTcjbHdJtN8ZXlRptLOPGHkUa/3Qov79gBeqvHNUO4bhI27p\
					  |  4WzD1bJDG9+6ml3gkrs7rOvMtROObPuc78A95fa4+skS/t2T7OjkfsHAm/enxf1fA\
					  |  wkk15xj0n6kmriwZfgUlOqyff0XLwuH4XFvZ+ZTyxYNoo2+EfFg4NVfqtSJch2WDY\
					  |  7n/qmhZOzMfyHlggWYFnDpyP27VrzQCQg8rM1Crp6MrwGLa94v6qP8pq0sQVq2DLt\
					  |  4NJSoRRqXTvqlWIRnexmcKXjQFVz6YSA==""".rfc8792single.base64Decode
              ))
            )
          ).withEntity(ContentTypes.`application/json`, """{"hello": "world"}""")
        case `§2.3_Request` => HttpRequest(
            method = HttpMethods.GET,
            uri = Uri("/foo"),
            headers = Seq(
              Host("example.org"),
              Date(DateTime(2021, 4, 20, 2, 7, 55)),
              RawHeader(
                "X-Example",
                """Example header
                  |   with some whitespace.  """.stripMargin
              ),
              `Cache-Control`(CacheDirectives.`max-age`(60)),
              RawHeader("X-Empty-Header", ""),
              `Cache-Control`(CacheDirectives.`must-revalidate`)
            ),
            entity =
              HttpEntity(MediaTypes.`application/json`.toContentType, """{"hello": "world"}""")
          )
        case `§3.2_Request` =>
          val Success(sigIn) = run.cosy.akka.http.headers.`Signature-Input`
            .parse("""sig1=("@method" "@path" "@authority" \
							|  "cache-control" "x-empty-header" "x-example");created=1618884475\
							|  ;keyid="test-key-rsa-pss"""".rfc8792single): @unchecked
          val Success(sig) = Signature.parse(
            """sig1=:P0wLUszWQjoi54udOtydf9IWTfNhy+r53jGFj9XZuP4uKwxyJo1\
					|  RSHi+oEF1FuX6O29d+lbxwwBao1BAgadijW+7O/PyezlTnqAOVPWx9GlyntiCiHzC8\
					|  7qmSQjvu1CFyFuWSjdGa3qLYYlNm7pVaJFalQiKWnUaqfT4LyttaXyoyZW84jS8gya\
					|  rxAiWI97mPXU+OVM64+HVBHmnEsS+lTeIsEQo36T3NFf2CujWARPQg53r58RmpZ+J9\
					|  eKR2CD6IJQvacn5A4Ix5BUAVGqlyp8JYm+S/CWJi31PNUjRRCusCVRj05NrxABNFv3\
					|  r5S9IXf2fYJK+eyW4AiGVMvMcOg==:""".rfc8792single
          ): @unchecked
          val req: Request[IO, H4] = toRequest(`§2.3_Request`)
          val newreq               = req.addHeaders(Seq(`Signature-Input`(sigIn), Signature(sig)))
          newreq
        case `§4.3_Request` =>
          val forwardedHdr         = RawHeader("Forwarded", "for=192.0.2.123")
          val req: Request[IO, H4] = toRequest(`§3.2_Request`)
          req.addHeaders(req.headers ++ Seq(forwardedHdr))
        case `§4.3_Enhanced` =>
          // here we try raw headers
          val forwardedHdr = RawHeader("Forwarded", "for=192.0.2.123")
          val sigIn = RawHeader(
            "Signature-Input",
            """sig1=("@method" "@path" "@authority" \
				  |    "cache-control" "x-empty-header" "x-example")\
				  |    ;created=1618884475;keyid="test-key-rsa-pss", \
				  |  proxy_sig=("signature";key="sig1" "forwarded")\
				  |    ;created=1618884480;keyid="test-key-rsa";alg="rsa-v1_5-sha256"""".rfc8792single
          )
          val sig = RawHeader(
            "Signature",
            """sig1=:P0wLUszWQjoi54udOtydf9IWTfNhy+r53jGFj9XZuP4uKwxyJo\
				  |    1RSHi+oEF1FuX6O29d+lbxwwBao1BAgadijW+7O/PyezlTnqAOVPWx9GlyntiCi\
				  |    HzC87qmSQjvu1CFyFuWSjdGa3qLYYlNm7pVaJFalQiKWnUaqfT4LyttaXyoyZW8\
				  |    4jS8gyarxAiWI97mPXU+OVM64+HVBHmnEsS+lTeIsEQo36T3NFf2CujWARPQg53\
				  |    r58RmpZ+J9eKR2CD6IJQvacn5A4Ix5BUAVGqlyp8JYm+S/CWJi31PNUjRRCusCV\
				  |    Rj05NrxABNFv3r5S9IXf2fYJK+eyW4AiGVMvMcOg==:, \
				  |  proxy_sig=:cjGvZwbsq9JwexP9TIvdLiivxqLINwp/ybAc19KOSQuLvtmMt3EnZx\
				  |    NiE+797dXK2cjPPUFqoZxO8WWx1SnKhAU9SiXBr99NTXRmA1qGBjqus/1Yxwr8k\
				  |    eB8xzFt4inv3J3zP0k6TlLkRJstkVnNjuhRIUA/ZQCo8jDYAl4zWJJjppy6Gd1X\
				  |    Sg03iUa0sju1yj6rcKbMABBuzhUz4G0u1hZkIGbQprCnk/FOsqZHpwaWvY8P3hm\
				  |    cDHkNaavcokmq+3EBDCQTzgwLqfDmV0vLCXtDda6CNO2Zyum/pMGboCnQn/VkQ+\
				  |    j8kSydKoFg6EbVuGbrQijth6I0dDX2/HYcJg==:""".rfc8792single
          )
          val req: Request[IO, H4] = toRequest(`§2.3_Request`)
          req.addHeaders(Seq(forwardedHdr, sigIn, sig))
        case B2_test_request => HttpRequest(
            method = HttpMethods.POST,
            uri = Uri("/foo?param=value&pet=dog"),
            headers = Seq(
              Host("example.com"),
              // note: changed minute because of bug https://github.com/httpwg/http-extensions/issues/1901
              Date(DateTime(2021, 4, 20, 2, 7, 56)),
              RawHeader("Digest", "SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=")
            )
          ).withEntity(ContentTypes.`application/json`, """{"hello": "world"}""")
        case B3_ProxyEnhanced_Request => HttpRequest(
            method = HttpMethods.POST,
            uri = Uri("/foo?Param=value&pet=Dog"),
            headers = Seq(
              Host("service.internal.example"),
              // note: changed minute because of bug https://github.com/httpwg/http-extensions/issues/1901
              Date(DateTime(2021, 4, 20, 2, 7, 55)),
              RawHeader(
                "Client-Cert",
                """:MIIBqDCCAU6gAwIBAgIBBzAKBggqhkjOPQQDAjA6MRswGQYDVQQKD\
					  |  BJMZXQncyBBdXRoZW50aWNhdGUxGzAZBgNVBAMMEkxBIEludGVybWVkaWF0ZSBDQT\
					  |  AeFw0yMDAxMTQyMjU1MzNaFw0yMTAxMjMyMjU1MzNaMA0xCzAJBgNVBAMMAkJDMFk\
					  |  wEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8YnXXfaUgmnMtOXU/IncWalRhebrXmck\
					  |  C8vdgJ1p5Be5F/3YC8OthxM4+k1M6aEAEFcGzkJiNy6J84y7uzo9M6NyMHAwCQYDV\
					  |  R0TBAIwADAfBgNVHSMEGDAWgBRm3WjLa38lbEYCuiCPct0ZaSED2DAOBgNVHQ8BAf\
					  |  8EBAMCBsAwEwYDVR0lBAwwCgYIKwYBBQUHAwIwHQYDVR0RAQH/BBMwEYEPYmRjQGV\
					  |  4YW1wbGUuY29tMAoGCCqGSM49BAMCA0gAMEUCIBHda/r1vaL6G3VliL4/Di6YK0Q6\
					  |  bMjeSkC3dFCOOB8TAiEAx/kHSB4urmiZ0NX5r5XarmPk0wmuydBVoU4hBVZ1yhk=:""".rfc8792single
              )
            )
          ).withEntity(ContentTypes.`application/json`, """{"hello": "world"}""")
        case _ => throw new Exception("no translation available for request " + request)

   @throws[Exception]
   def toResponse(response: HttpMessage): Response[IO, H4] =
     response match
        case `§2.2.10_Response` => HttpResponse(StatusCodes.OK)
            .withHeaders(Date(DateTime.now))
        case `§2.2.11_UnsignedResponse` => HttpResponse(
            StatusCodes.OK,
            headers = Seq(Date(DateTime(2021, 4, 20, 2, 7, 56)))
          ).withEntity(
            ContentTypes.`application/json`,
            """{"busy": true, "message": "Your call is very important to us"}"""
          )
        case B2_test_response => HttpResponse(
            StatusCodes.OK,
            headers = Seq(
              Date(DateTime(2021, 4, 20, 2, 7, 56)),
              RawHeader("Digest", "SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=")
            )
          ).withEntity(ContentTypes.`application/json`, """{"hello": "world"}""")
        case _ => throw new Exception("no translation available for response " + response)

   val rfcAppdxB2Req = HttpRequest(
     method = HttpMethods.POST,
     uri = Uri("/foo?param=value&pet=dog"),
     headers = Seq(
       Host(Uri.Host("example.com"), 0),
       Date(DateTime(2021, 4, 20, 2, 7, 55)),
       RawHeader("Digest", "SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=")
     ),
     entity = HttpEntity(MediaTypes.`application/json`.toContentType, """{"hello": "world"}""")
   )

   val `x-example`: MessageSelector[Request[IO, H4]] = new UntypedAkkaSelector[Request[IO, H4]]:
      override val lowercaseName: String = "x-example"
   val `x-empty-header`: MessageSelector[Request[IO, H4]] =
     new UntypedAkkaSelector[Request[IO, H4]]:
        override val lowercaseName: String = "x-empty-header"
   val `x-ows-header`: MessageSelector[Request[IO, H4]] = new UntypedAkkaSelector[Request[IO, H4]]:
      override val lowercaseName: String = "x-ows-header"
   val `x-obs-fold-header`: MessageSelector[Request[IO, H4]] =
     new UntypedAkkaSelector[Request[IO, H4]]:
        override val lowercaseName: String = "x-obs-fold-header"
   val `x-dictionary`: DictSelector[Message[IO, H4]] = new AkkaDictSelector[Message[IO, H4]]:
      override val lowercaseName: String = "x-dictionary"

   test("§2.2.6. Request Target for CONNECT (does not work for Akka)".fail) {
     // this does not work for AKKA because akka returns //www.example.com:80
     val reqCONNECT = toRequest(`§2.2.6_CONNECT`)
     assertEquals(
       `@request-target`.signingString(reqCONNECT),
       `request-target`("www.example.com:80")
     )
   }

end AkkaHttpMessageSigningSuite
