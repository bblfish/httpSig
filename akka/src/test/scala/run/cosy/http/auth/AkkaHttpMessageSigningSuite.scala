package run.cosy.http.auth

import akka.http.scaladsl.model.HttpMethods.*
import akka.http.scaladsl.model.headers.*
import akka.http.scaladsl.model.{ContentTypes, DateTime, HttpEntity, HttpMethods, HttpRequest, HttpResponse, MediaTypes, StatusCodes, Uri}
import run.cosy.akka.http.AkkaTp
import run.cosy.akka.http.headers.`Signature-Input`
import run.cosy.http.headers.Rfc8941.{IList, Param, SfInt, SfString, Token}
import run.cosy.http.headers.{DictSelector, Rfc8941, SigInput, SigInputs, Signatures}
import run.cosy.akka.http.headers.UntypedAkkaSelector
import run.cosy.http.auth.HttpMessageSigningSuite
import run.cosy.http.Http
import run.cosy.http.Http.{Message, Request, Response}
import run.cosy.akka.http.headers.AkkaMessageSelectors
import run.cosy.akka.http.headers.AkkaDictSelector
import run.cosy.http.utils.StringUtils.*


class AkkaHttpMessageSigningSuite extends HttpMessageSigningSuite[AkkaTp.type]:
	type A = AkkaTp.type
	val selectorsSecure= AkkaMessageSelectors(true,Uri.Host("bblfish.net"),443)
	val selectorsInSecure= AkkaMessageSelectors(false,Uri.Host("bblfish.net"),80)
	val messageSignature: run.cosy.http.auth.MessageSignature[A] = AkkaHttpMessageSignature
	import selectorsSecure.*
	/**
	 * todo: with Akka it is possible to automatically convert a String to an HttpMessage,
	 *   but it requires more work, a different test suite potentially, etc... That will
	 *   be needed for large test suistes though. See
	 *    1. akka.http.impl.engine.parsing.RequestParserSpec
	 *    1. akka.http.impl.engine.parsing.ResponseParserSpec
	 **/
	@throws[Exception]
	def toRequest(request: HttpMessage): Request[A] =
		request match
		case `§2.1.2_HeaderField` => HttpRequest(
			method = HttpMethods.GET,
			uri = Uri("/xyz"),
			headers = Seq(
				Host("www.example.com"),
				Date(DateTime(2014, 06, 07, 20, 51, 35)),
				RawHeader("X-OWS-Header", "   Leading and trailing whitespace.   "),
				RawHeader("X-Obs-Fold-Header",
					"""Obsolete
					  |      line folding. """.stripMargin),
				RawHeader("X-Example",
					"""Example header
					  |   with some whitespace.  """.stripMargin),
				`Cache-Control`(CacheDirectives.`max-age`(60)),
				RawHeader("X-Empty-Header", ""),
				`Cache-Control`(CacheDirectives.`must-revalidate`),
				RawHeader("X-Dictionary", "   a=1,    b=2;x=1;y=2,   c=(a   b   c)")
			))
		case `§2.2.x_Request` =>  HttpRequest(HttpMethods.POST, Uri("/path?param=value"),
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
			HttpRequest(HttpMethods.POST, Uri("/a/b"),
				Seq(Host("www.example.com")))
		case `§2.2.6_Request_Options` =>
			HttpRequest(HttpMethods.OPTIONS, Uri("*"), headers = Seq(Host("server.example.com")))
		case `§2.2.6_CONNECT` => HttpRequest(HttpMethods.CONNECT,
				Uri("", Uri.Authority(Uri.Host("www.example.com"), 80), Uri.Path.Empty, None, None)
			).withHeaders(Host(Uri.Host("www.example.com")))
		case `§2.2.8_Request_1` =>  HttpRequest(HttpMethods.POST,
			uri = Uri("/path?param=value&foo=bar&baz=batman"),
			headers = Seq(Host(Uri.Host("www.example.com"))))
		case `§2.2.8_Request_2` => HttpRequest(HttpMethods.POST,
			uri = Uri("/path?queryString"),
			headers = Seq(Host(Uri.Host("www.example.com"))))
		case `§2.2.8_Request_3` => HttpRequest(HttpMethods.GET, Uri("/path?"))
			.withHeaders(Host(Uri.Host("www.example.com")))
		case `§2.2.9_Request` =>  HttpRequest(HttpMethods.GET,
			Uri("/path?param=value&foo=bar&baz=batman&qux="))
			.withHeaders(Host(Uri.Host("www.example.com")))
		case `§2.2.11_Request` => HttpRequest(POST, Uri("/foo?param=value&pet=dog"),
			headers = Seq(
				Host("example.com"),
				Date(DateTime(2021, 04, 20, 02, 07, 55)),
				`Signature-Input`(SigInputs(Rfc8941.Token("sig1"), SigInput(IList(
					`@authority`.sf, `content-type`.sf)(
					Param("created", SfInt(1618884475)), Param("keyid", SfString("test-key-rsa-pss"))
				)).get)),
				run.cosy.http.headers.Signature(Signatures(Token("sig1"),
					"""KuhJjsOKCiISnKHh2rln5ZNIrkRvue0DSu5rif3g7ckTbbX7C4\
					  |  Jp3bcGmi8zZsFRURSQTcjbHdJtN8ZXlRptLOPGHkUa/3Qov79gBeqvHNUO4bhI27p\
					  |  4WzD1bJDG9+6ml3gkrs7rOvMtROObPuc78A95fa4+skS/t2T7OjkfsHAm/enxf1fA\
					  |  wkk15xj0n6kmriwZfgUlOqyff0XLwuH4XFvZ+ZTyxYNoo2+EfFg4NVfqtSJch2WDY\
					  |  7n/qmhZOzMfyHlggWYFnDpyP27VrzQCQg8rM1Crp6MrwGLa94v6qP8pq0sQVq2DLt\
					  |  4NJSoRRqXTvqlWIRnexmcKXjQFVz6YSA==""".rfc8792single.base64Decode
				)))
			).withEntity(ContentTypes.`application/json`, """{"hello": "world"}""")
		case `§2.3_Request` => HttpRequest(
			method = HttpMethods.GET,
			uri = Uri("/foo"),
			headers = Seq(
				Host("example.org"),
				Date(DateTime(2021, 04, 20, 02, 07, 55)),
				RawHeader("X-Example",
					"""Example header
					  |   with some whitespace.  """.stripMargin),
				`Cache-Control`(CacheDirectives.`max-age`(60)),
				RawHeader("X-Empty-Header", ""),
				`Cache-Control`(CacheDirectives.`must-revalidate`)
			),
			entity = HttpEntity(MediaTypes.`application/json`.toContentType, """{"hello": "world"}""")
		)
		case _ => throw new Exception("no translation available for request "+request)

	@throws[Exception]
	def toResponse(response: HttpMessage): Response[A] =
		response match
		case `§2.2.10_Response` => HttpResponse(StatusCodes.OK)
			.withHeaders(Date(DateTime.now))
		case `§2.2.11_UnsignedResponse` => HttpResponse(StatusCodes.OK,
			headers = Seq(Date(DateTime(2021, 04, 20, 02, 07, 56)))
		).withEntity(ContentTypes.`application/json`,
			"""{"busy": true, "message": "Your call is very important to us"}"""
		)
		case _ => throw new Exception("no translation available for response "+response)

	val rfcAppdxB2Req = HttpRequest(method = HttpMethods.POST,
		uri = Uri("/foo?param=value&pet=dog"),
		headers = Seq(
			Host(Uri.Host("example.com"), 0),
			Date(DateTime(2021, 04, 20, 02, 07, 55)),
			RawHeader("Digest", "SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE="),
		),
		entity = HttpEntity(MediaTypes.`application/json`.toContentType, """{"hello": "world"}""")
	)

	val `x-example`: HeaderSelector[Request[A]] = new UntypedAkkaSelector[Request[A]] {
		override val lowercaseName: String = "x-example"
	}
	val `x-empty-header`:  HeaderSelector[Request[A]] = new UntypedAkkaSelector[Request[A]] {
		override val lowercaseName: String = "x-empty-header"
	}
	val `x-ows-header`: HeaderSelector[Request[A]] =  new UntypedAkkaSelector[Request[A]]{
		override val lowercaseName: String = "x-ows-header"
	}
	val `x-obs-fold-header`: HeaderSelector[Request[A]] = new UntypedAkkaSelector[Request[A]]{
		override val lowercaseName: String = "x-obs-fold-header"
	}
	val `x-dictionary`: DictSelector[Message[A]] = new AkkaDictSelector[Message[A]] {
		override val lowercaseName: String = "x-dictionary"
	}

end AkkaHttpMessageSigningSuite

