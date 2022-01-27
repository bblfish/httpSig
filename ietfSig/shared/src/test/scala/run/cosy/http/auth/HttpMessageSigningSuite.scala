package run.cosy.http.auth

import bobcats.AsymmetricKeyAlg.Signature
import bobcats.PKCS8KeySpec
import cats.effect.Async
import run.cosy.http.{Http, HttpOps}
//todo: SignatureBytes is less likely to class with objects like Signature
import bobcats.Verifier.{SigningString, Signature as SignatureBytes}
import bobcats.util.BouncyJavaPEMUtils
import bobcats.{AsymmetricKeyAlg, SPKIKeySpec, SigningHttpMessages}
import munit.CatsEffectSuite
import run.cosy.http.auth.AgentIds.PureKeyId
import run.cosy.http.headers.*
import run.cosy.http.headers.Rfc8941.*
import run.cosy.http.headers.Rfc8941.SyntaxHelper.*
import scodec.bits.ByteVector
//import com.nimbusds.jose.JWSAlgorithm
//import run.cosy.akka.http.JW2JCA
import cats.effect.IO
import run.cosy.http.utils.StringUtils.*

import java.nio.charset.StandardCharsets
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.security.{KeyFactory, MessageDigest, Signature as JSignature}
import java.time.Clock
import java.util.Base64
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

trait HttpMessageSigningSuite[H <: Http](using
	ops: HttpOps[H],
	sigSuite: SigningSuiteHelpers
) extends CatsEffectSuite:

	import Http.*
	import ops.*

	type HttpMessage = String
	val selectorsSecure: MessageSelectors[H]
	val selectorsInSecure: MessageSelectors[H]
	val messageSignature: run.cosy.http.auth.MessageSignature[H]

	import messageSignature.{*, given}
	import selectorsSecure.*
	// special headers used in the spec that we won't find elsewhere
	val `x-example`: HeaderSelector[Request[H]]
	val `x-empty-header`: HeaderSelector[Request[H]]
	val `x-ows-header`: HeaderSelector[Request[H]]
	val `x-obs-fold-header`: HeaderSelector[Request[H]]
	val `x-dictionary`: DictSelector[Message[H]]

	given ec: ExecutionContext = scala.concurrent.ExecutionContext.global
	given clock: Clock = Clock.fixed(java.time.Instant.ofEpochSecond(16188845000), java.time.ZoneOffset.UTC).nn

	def expectedKeyedHeader(name: String, key: String, value: String) = Success("\"" + name + "\";key=\"" + key + "\": " + value)
	def expectedNameHeader(name: String, nameVal: String, value: String) = Success("\"" + name + "\";name=\"" + nameVal + "\": " + value)
	def `request-target`(value: String): Success[String] = expectedHeader("@request-target", value)
	def expectedHeader(name: String, value: String) = Success("\"" + name + "\": " + value)

	given specialRequestSelectorOps: run.cosy.http.headers.SelectorOps[Request[H]] =
		selectorsSecure.requestSelectorOps.append(
			`x-example`, `x-empty-header`, `x-ows-header`, `x-obs-fold-header`, `x-dictionary`,
		)
	given specialResponseSelectorOps: run.cosy.http.headers.SelectorOps[Response[H]] =
		selectorsSecure.responseSelectorOps.append(`x-dictionary`)

	@throws[Exception]
	def toRequest(request: HttpMessage): Request[H]
	@throws[Exception]
	def toResponse(response: HttpMessage): Response[H]

	//helper method
	extension (bytes: Try[ByteVector])
		def toAscii: Try[String] = bytes.flatMap(_.decodeAscii.toTry)

	/** example from [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html */
	// the example in the spec does not have the `GET`. Added here for coherence
	val `§2.1.2_HeaderField`: HttpMessage =
	"""GET /xyz HTTP/1.1
	  |Host: www.example.com
	  |Date: Tue, 07 Jun 2014 20:51:35 GMT
	  |X-OWS-Header:   Leading and trailing whitespace.
	  |X-Obs-Fold-Header: Obsolete
	  |    line folding.
	  |X-Empty-Header:
	  |Cache-Control: max-age=60
	  |Cache-Control:    must-revalidate
	  |X-Dictionary:  a=1,    b=2;x=1;y=2,   c=(a   b   c)""".stripMargin

	test("§2.1.2 HTTP Field Examples") {
		import messageSignature.signingString
		val rfcCanonReq: Request[H] = toRequest(`§2.1.2_HeaderField`)
		assertEquals(
			`cache-control`.signingString(rfcCanonReq),
			expectedHeader("cache-control", "max-age=60, must-revalidate"))
		//note: It is Saturday, not Tuesday: either that is an error in the spec, or example of header with wrong date
		//   we fixed it here, because it is difficult to get broken dates past akka
		assertEquals(`date`.signingString(rfcCanonReq),
			expectedHeader("date", "Sat, 07 Jun 2014 20:51:35 GMT"))
		assertEquals(`host`.signingString(rfcCanonReq),
			expectedHeader("host", "www.example.com"))
		assertEquals(`x-empty-header`.signingString(rfcCanonReq),
			expectedHeader("x-empty-header", ""))
		assertEquals(`x-obs-fold-header`.signingString(rfcCanonReq),
			expectedHeader("x-obs-fold-header", "Obsolete line folding."))
		assertEquals(`x-ows-header`.signingString(rfcCanonReq),
			expectedHeader("x-ows-header", "Leading and trailing whitespace."))
		val il = IList(
			`cache-control`.sf, date.sf, host.sf, `x-empty-header`.sf, `x-obs-fold-header`.sf,
			`x-ows-header`.sf, `x-dictionary`.sf)(Token("keyid") -> sf"some-key")
		val Some(signatureInput) = SigInput(il): @unchecked
		assertEquals(rfcCanonReq.signingString(signatureInput).toAscii, Success(
			""""cache-control": max-age=60, must-revalidate
			  |"date": Sat, 07 Jun 2014 20:51:35 GMT
			  |"host": www.example.com
			  |"x-empty-header": \
			  |
			  |"x-obs-fold-header": Obsolete line folding.
			  |"x-ows-header": Leading and trailing whitespace.
			  |"x-dictionary": a=1, b=2;x=1;y=2, c=(a b c)
			  |"@signature-params": ("cache-control" "date" "host" "x-empty-header" \
			  |"x-obs-fold-header" "x-ows-header" "x-dictionary");keyid="some-key"""".rfc8792single
		))
		//should fail because there is a header we don't know how to interpret
		val il2 = IList(
			`cache-control`.sf, date.sf, host.sf, `x-empty-header`.sf, sf"x-not-implemented",
			`x-obs-fold-header`.sf, `x-ows-header`.sf, `x-dictionary`.sf)(Token("keyid") -> sf"some-key")
		val Some(signatureInputFail) = SigInput(il2): @unchecked
		rfcCanonReq.signingString(signatureInputFail) match
		case Failure(InvalidSigException(msg)) if msg.contains("x-not-implemented") => assert(true)
		case x => fail("this should have failed because header x-not-implemented is not supported")

		//fail because one of the headers is missing
		rfcCanonReq.removeHeader("X-Empty-Header").signingString(signatureInput) match
		case Failure(UnableToCreateSigHeaderException(msg)) if msg.contains("x-empty-header") => assert(true)
		case x => fail("not failing in the right way? received:" + x.toString)

		//fail because keyid is not specified (note: this is no longer required in the spec,..., check)
		val ilNoKeyId = IList(date.sf, host.sf)()
		val Some(signatureInputNoKeyId) = SigInput(ilNoKeyId): @unchecked
		assert(rfcCanonReq.signingString(signatureInputNoKeyId).isSuccess)
	}

	test("§2.1.3 Dictionary Structured Field Members") {
		val rfcCanonReq: Request[H] = toRequest(`§2.1.2_HeaderField`)
		assertEquals(`x-dictionary`.signingStringFor(rfcCanonReq),
			expectedHeader("x-dictionary", "a=1, b=2;x=1;y=2, c=(a b c)"))
		assertEquals(`x-dictionary`.signingString(rfcCanonReq),
			expectedHeader("x-dictionary", "a=1, b=2;x=1;y=2, c=(a b c)"))
		assertEquals(`x-dictionary`.signingString(rfcCanonReq, Rfc8941.Params()),
			expectedHeader("x-dictionary", "a=1, b=2;x=1;y=2, c=(a b c)"))
		assertEquals(`x-dictionary`.signingStringFor(rfcCanonReq, SfString("a")),
			expectedKeyedHeader("x-dictionary", "a", "1"))
		assertEquals(`x-dictionary`.signingString(rfcCanonReq, Rfc8941.Params(`x-dictionary`.keyParam -> SfString("a"))),
			expectedKeyedHeader("x-dictionary", "a", "1"))
		assertEquals(`x-dictionary`.signingStringFor(rfcCanonReq, SfString("b")),
			expectedKeyedHeader("x-dictionary", "b", "2;x=1;y=2"))
		assertEquals(`x-dictionary`.signingStringFor(rfcCanonReq, SfString("c")),
			expectedKeyedHeader("x-dictionary", "c", "(a b c)"))
	}

	test("§2.2.1 Signature Parameters") {
		val si: Option[SigInput] = SigInput(IList(`@target-uri`.sf, `@authority`.sf, date.sf, `cache-control`.sf,
			`x-empty-header`.sf, `x-example`.sf)(
			Token("keyid") -> SfString("test-key-rsa-pss"),
			Token("alg") -> SfString("rsa-pss-sha512"),
			Token("created") -> SfInt(1618884475),
			Token("expires") -> SfInt(1618884775)
		))
		si match
		case Some(si) =>
			val sig = `@signature-params`.signingString(si)
			assertEquals(sig,
				""""@signature-params": ("@target-uri" "@authority" "date" "cache-control" "x-empty-header" \
				  |  "x-example");keyid="test-key-rsa-pss";alg="rsa-pss-sha512";\
				  |  created=1618884475;expires=1618884775""".rfc8792single)
		case None => fail("SigInput is not available")
	}

	val `§2.2.x_Request`: HttpMessage =
		"""POST /path?param=value HTTP/1.1
		  |Host: www.example.com""".stripMargin

	test("§2.2.2 Method") {
		val `req_2.2.2`: Request[H] = toRequest(`§2.1.2_HeaderField`)
		assertEquals(
			`@method`.signingString(`req_2.2.2`),
			expectedHeader("@method", "GET")
		)
		val `req_2.2.x` = toRequest(`§2.2.x_Request`)
		assertEquals(
			`@method`.signingString(`req_2.2.x`),
			expectedHeader("@method", "POST")
		)
	}

	val `§2.2.3_Request_AbsoluteURI`: HttpMessage =
		"""GET http://www.example.com HTTP/1.1""".stripMargin
	val `§2.2.3_Request_AbsoluteURI_2`: HttpMessage =
		"""GET http://www.example.com/a HTTP/1.1""".stripMargin


	test("§2.2.3 Target URI") {
		assertEquals(
			`@target-uri`.signingString(toRequest(`§2.2.x_Request`)),
			expectedHeader("@target-uri", "https://www.example.com/path?param=value")
		)

		assertEquals(
			`@target-uri`.signingString(toRequest(`§2.2.x_Request`).removeHeader("Host")),
			expectedHeader("@target-uri", "https://bblfish.net/path?param=value")
		)

		assertEquals(
			`@target-uri`.signingString(toRequest(`§2.2.3_Request_AbsoluteURI`)),
			//todo: should this really end in a slash??
			expectedHeader(`@target-uri`.lowercaseName, "http://www.example.com")
		)

		assertEquals(
			`@target-uri`.signingString(toRequest(`§2.2.3_Request_AbsoluteURI_2`)),
			expectedHeader(`@target-uri`.lowercaseName, "http://www.example.com/a")
		)
	}

	test("§2.2.4 Authority") {
		val `req_2.2.4` = toRequest(`§2.2.x_Request`)
		assertEquals(
			`@authority`.signingString(`req_2.2.4`),
			expectedHeader("@authority", "www.example.com")
		)
		assertEquals(
			`@authority`.signingString(`req_2.2.4`.removeHeader("Host")),
			expectedHeader("@authority", "bblfish.net")
		)
	}

	test("§2.2.5 Scheme") {
		val `req_2.2.5` = toRequest(`§2.2.x_Request`)
		assertEquals(
			`@scheme`.signingString(`req_2.2.5`),
			expectedHeader("@scheme", "https")
		)
		assertEquals(
			selectorsInSecure.`@scheme`.signingString(`req_2.2.5`),
			expectedHeader("@scheme","http")
		)
	}

	val `§2.2.x_Request_Absolute`: HttpMessage =
		"""POST https://www.example.com/path?param=value HTTP/1.1
		  |Host: www.example.com""".stripMargin

	val `§2.2.6_Request_AbsoluteURI`: HttpMessage =
		"""GET http://www.example.com/a/b/ HTTP/1.1""".stripMargin

	val `§2.2.6_Request_rel`: HttpMessage =
		"""POST /a/b/ HTTP/1.1
		  |Host: www.example.com""".stripMargin

	val `§2.2.6_Request_Options`: HttpMessage =
		"""OPTIONS * HTTP/1.1
		  |Host: server.example.com""".stripMargin

	test("§2.2.6. Request Target") {
		val `req_2.2.6` = toRequest(`§2.2.x_Request`)
		assertEquals(
			`@request-target`.signingString(`req_2.2.6`),
			`request-target`("/path?param=value")
		)
		val reqGETAbsoluteURI = toRequest(`§2.2.x_Request_Absolute`)
		assertEquals(
			`@request-target`.signingString(reqGETAbsoluteURI),
			`request-target`("https://www.example.com/path?param=value")
		)
		val req2 = toRequest(`§2.2.6_Request_rel`)
		assertEquals(
			`@request-target`.signingString(req2),
			`request-target`("/a/b")
		)

		val req3 = toRequest(`§2.2.3_Request_AbsoluteURI_2`)
		assertEquals(
			`@request-target`.signingString(req3),
			`request-target`("http://www.example.com/a")
		)

		assertEquals(
			`@request-target`.signingString(toRequest(`§2.2.6_Request_AbsoluteURI`)),
			`request-target`("http://www.example.com/a/b/")
		)
		//CONNECT: Not sure if this is allowed by Akka. It is not possible to create an HttpRequest with it
		//		val req5 = HttpRequest(CONNECT, Uri("server.example.com:80",Uri.ParsingMode.Relaxed),
		//			Seq(Host("www.example.com")))
		//		assertEquals(
		//			`@request-target`.signingString(req5),
		//			`request-target`("connect /")
		//		)
		val req6 = toRequest(`§2.2.6_Request_Options`)
		assertEquals(
			`@request-target`.signingString(req6),
			`request-target`("*")
		)
	}
	val `§2.2.6_CONNECT`: HttpMessage =
		"""CONNECT www.example.com:80 HTTP/1.1
		  |Host: www.example.com
		  |""".stripMargin


	test("§2.2.6. Request Target for CONNECT (does not work for Akka)".fail) {
		//this does not work for AKKA because akka returns //www.example.com:80
		val reqCONNECT = toRequest(`§2.2.6_CONNECT`)
		assertEquals(
			`@request-target`.signingString(reqCONNECT),
			`request-target`("www.example.com:80")
		)
	}


	test("§2.2.7 Path") {
		assertEquals(
			`@path`.signingString(toRequest(`§2.2.x_Request`)),
			expectedHeader("@path", "/path")
		)
	}

	val `§2.2.8_Request_1`: HttpMessage =
		"""POST /path?param=value&foo=bar&baz=batman HTTP/1.1
		  |Host: www.example.com""".stripMargin
	val `§2.2.8_Request_2`: HttpMessage =
		"""POST /path?queryString HTTP/1.1
		  |Host: www.example.com""".stripMargin
	val `§2.2.8_Request_3`: HttpMessage =
		"""GET /path? HTTP/1.1
		  |Host: www.example.com""".stripMargin

	test("§2.2.8 Query") {
		val `req_2.2.8` = toRequest(`§2.2.8_Request_1`)
		assertEquals(
			`@query`.signingString(`req_2.2.8`),
			expectedHeader("@query", "?param=value&foo=bar&baz=batman")
		)
		val `req_2.2.8a` = toRequest(`§2.2.8_Request_2`)
		assertEquals(
			`@query`.signingString(`req_2.2.8a`),
			expectedHeader("@query", "?queryString")
		)
		//is this correct?
		assertEquals(
			`@query`.signingString(toRequest(`§2.2.3_Request_AbsoluteURI_2`)),
			expectedHeader("@query", "")
		)
		//is this correct?
		assertEquals(
			`@query`.signingString(toRequest(`§2.2.8_Request_3`)),
			expectedHeader("@query", "?")
		)

	}

	val `§2.2.9_Request`: HttpMessage =
		"""POST /path?param=value&foo=bar&baz=batman&qux= HTTP/1.1
		  |Host: www.example.com""".stripMargin

	test("§2.2.9 Query Parameters") {
		val `req_2.2.9` = toRequest(`§2.2.9_Request`)
		assertEquals(
			`@query-params`.signingString(`req_2.2.9`, Params(Token("name") -> SfString("baz"))),
			expectedNameHeader("@query-params", "baz", "batman")
		)
		assertEquals(
			`@query-params`.signingString(`req_2.2.9`, Params(Token("name") -> SfString("qux"))),
			expectedNameHeader("@query-params", "qux", "")
		)
		assertEquals(
			`@query-params`.signingString(`req_2.2.9`, Params(Token("name") -> SfString("param"))),
			expectedNameHeader("@query-params", "param", "value")
		)
	}

	val `§2.2.10_Response`: HttpMessage =
		"""HTTP/1.1 200 OK
		  |Date: Fri, 26 Mar 2010 00:05:00 GMT""".stripMargin

	test("§2.2.10 Status Code") {
		assertEquals(
			`@status`.signingString(toResponse(`§2.2.10_Response`)),
			Success("\"@status\": 200")
		)
	}

	val `§2.2.11_Request`: HttpMessage =
		"""POST /foo?param=value&pet=dog HTTP/1.1
		  |Host: example.com
		  |Date: Tue, 20 Apr 2021 02:07:55 GMT
		  |Content-Type: application/json
		  |Content-Length: 18
		  |Signature-Input: sig1=("@authority" "content-type")\
		  |  ;created=1618884475;keyid="test-key-rsa-pss"
		  |Signature: sig1=:KuhJjsOKCiISnKHh2rln5ZNIrkRvue0DSu5rif3g7ckTbbX7C4\
		  |  Jp3bcGmi8zZsFRURSQTcjbHdJtN8ZXlRptLOPGHkUa/3Qov79gBeqvHNUO4bhI27p\
		  |  4WzD1bJDG9+6ml3gkrs7rOvMtROObPuc78A95fa4+skS/t2T7OjkfsHAm/enxf1fA\
		  |  wkk15xj0n6kmriwZfgUlOqyff0XLwuH4XFvZ+ZTyxYNoo2+EfFg4NVfqtSJch2WDY\
		  |  7n/qmhZOzMfyHlggWYFnDpyP27VrzQCQg8rM1Crp6MrwGLa94v6qP8pq0sQVq2DLt\
		  |  4NJSoRRqXTvqlWIRnexmcKXjQFVz6YSA==:
		  |
		  |{"hello": "world"}""".rfc8792single
	val `§2.2.11_UnsignedResponse`: HttpMessage =
		"""HTTP/1.1 200 OK
		  |Date: Tue, 20 Apr 2021 02:07:56 GMT
		  |Content-Type: application/json
		  |Content-Length: 62
		  |
		  |{"busy": true, "message": "Your call is very important to us"}""".stripMargin

	val `§2.2.11_SignedResponse`: HttpMessage =
		"""HTTP/1.1 200 OK
		  |Date: Tue, 20 Apr 2021 02:07:56 GMT
		  |Content-Type: application/json
		  |Content-Length: 62
		  |Signature-Input: sig1=("content-type" "content-length" "@status" \
		  |  "@request-response";key="sig1");created=1618884475\
		  |  ;keyid="test-key-ecc-p256"
		  |Signature: sig1=:crVqK54rxvdx0j7qnt2RL1oQSf+o21S/6Uk2hyFpoIfOT0q+Hv\
		  |  msYAXUXzo0Wn8NFWh/OjWQOXHAQdVnTk87Pw==:
		  |
		  |{"busy": true, "message": "Your call is very important to us"}""".rfc8792single


	test("§2.2.11 Request-Response Signature Binding") {
		val `req_2.2.11`: Request[H] = toRequest(`§2.2.11_Request`)
		assertEquals(
			`@request-response`.signingString(`req_2.2.11`, Params(Token("key") -> SfString("sig1"))),
			expectedKeyedHeader("@request-response", "sig1",
				""":KuhJjsOKCiISnKHh2rln5ZNIrkRvue0DSu\
				  |  5rif3g7ckTbbX7C4Jp3bcGmi8zZsFRURSQTcjbHdJtN8ZXlRptLOPGHkUa/3Qov79\
				  |  gBeqvHNUO4bhI27p4WzD1bJDG9+6ml3gkrs7rOvMtROObPuc78A95fa4+skS/t2T7\
				  |  OjkfsHAm/enxf1fAwkk15xj0n6kmriwZfgUlOqyff0XLwuH4XFvZ+ZTyxYNoo2+Ef\
				  |  Fg4NVfqtSJch2WDY7n/qmhZOzMfyHlggWYFnDpyP27VrzQCQg8rM1Crp6MrwGLa94\
				  |  v6qP8pq0sQVq2DLt4NJSoRRqXTvqlWIRnexmcKXjQFVz6YSA==:""".rfc8792single)
		)
		val siginput: SigInput = SigInput(
			"""("content-type" "content-length" "@status" \
			  |  "@request-response";key="sig1");created=1618884475\
			  |  ;keyid="test-key-ecc-p256"""".rfc8792single).get

		val `res_2.2.11`: Response[H] = toResponse(`§2.2.11_UnsignedResponse`)
		assertEquals(
			`res_2.2.11`.signingString(siginput,`req_2.2.11`).toAscii,
			Success(
				""""content-type": application/json
				  |"content-length": 62
				  |"@status": 200
				  |"@request-response";key="sig1": :KuhJjsOKCiISnKHh2rln5ZNIrkRvue0DSu\
				  |  5rif3g7ckTbbX7C4Jp3bcGmi8zZsFRURSQTcjbHdJtN8ZXlRptLOPGHkUa/3Qov79\
				  |  gBeqvHNUO4bhI27p4WzD1bJDG9+6ml3gkrs7rOvMtROObPuc78A95fa4+skS/t2T7\
				  |  OjkfsHAm/enxf1fAwkk15xj0n6kmriwZfgUlOqyff0XLwuH4XFvZ+ZTyxYNoo2+Ef\
				  |  Fg4NVfqtSJch2WDY7n/qmhZOzMfyHlggWYFnDpyP27VrzQCQg8rM1Crp6MrwGLa94\
				  |  v6qP8pq0sQVq2DLt4NJSoRRqXTvqlWIRnexmcKXjQFVz6YSA==:
				  |"@signature-params": ("content-type" "content-length" "@status" \
				  |  "@request-response";key="sig1");created=1618884475\
				  |  ;keyid="test-key-ecc-p256"""".rfc8792single
			)
		)
	}

	val `§2.3_Request`: HttpMessage =
		"""GET /foo HTTP/1.1
		  |Host: example.org
		  |Date: Tue, 20 Apr 2021 02:07:55 GMT
		  |X-Example: Example header
		  |        with some whitespace.
		  |X-Empty-Header:
		  |Cache-Control: max-age=60
		  |Cache-Control: must-revalidate""".stripMargin


	test("§2.3 Creating the Signature Input String (spec 04)") {
		/**
		 * [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-04.html#section-2.5 example request in §2.5]] * */
		val expectedSigInStr =
			""""@request-target": /foo
			  |"host": example.org
			  |"date": Tue, 20 Apr 2021 02:07:55 GMT
			  |"cache-control": max-age=60, must-revalidate
			  |"x-empty-header": \
			  |
			  |"x-example": Example header with some whitespace.
			  |"@signature-params": ("@request-target" "host" "date" "cache-control" \
			  |  "x-empty-header" "x-example");created=1618884475;\
			  |  keyid="test-key-rsa-pss"""".rfc8792single

		val siginStr =
			"""("@request-target" "host" "date" "cache-control" \
			  |  "x-empty-header" "x-example");created=1618884475;\
			  |  keyid="test-key-rsa-pss"""".rfc8792single

		val sigInIlist = IList(
			`@request-target`.sf, host.sf, date.sf, `cache-control`.sf,
			`x-empty-header`.sf, `x-example`.sf
		)(
			Token("created") -> SfInt(1618884475),
			Token("keyid") -> sf"test-key-rsa-pss"
		)
		assert(SigInput.valid(sigInIlist))
		val rfcSigInreq = toRequest(`§2.3_Request`)
		val Some(sigInHandBuilt) = SigInput(sigInIlist): @unchecked
		val Some(sigin) = SigInput(siginStr): @unchecked
		assertEquals(rfcSigInreq.signingString(sigin).toAscii, Success(expectedSigInStr))
	}

	test("§2.3 Creating the Signature Input String (spec 07)") {
		val expectedSigInStr =
			""""@method": GET
			  |"@path": /foo
			  |"@authority": example.org
			  |"cache-control": max-age=60, must-revalidate
			  |"x-empty-header": \
			  |
			  |"x-example": Example header with some whitespace.
			  |"@signature-params": ("@method" "@path" "@authority" \
			  |  "cache-control" "x-empty-header" "x-example");created=1618884475\
			  |  ;keyid="test-key-rsa-pss"""".rfc8792single

		val siginStr =
			"""("@method" "@path" "@authority" \
			  |  "cache-control" "x-empty-header" "x-example");created=1618884475\
			  |  ;keyid="test-key-rsa-pss"""".rfc8792single

		val sigInIlist = IList(
			`@method`.sf, `@path`.sf, `@authority`.sf,
			`cache-control`.sf, `x-empty-header`.sf, `x-example`.sf
		)(
			Token("created") -> SfInt(1618884475),
			Token("keyid") -> sf"test-key-rsa-pss"
		)
		assert(SigInput.valid(sigInIlist))
		val rfcSigInreq: Request[H] = toRequest(`§2.3_Request`)
		val Some(sigInHandBuilt) = SigInput(sigInIlist): @unchecked
		val Some(sigin) = SigInput(siginStr): @unchecked
		assertEquals(rfcSigInreq.signingString(sigin).toAscii, Success(expectedSigInStr))
	}

	val `§3.2_Request`: HttpMessage =
		"""GET /foo HTTP/1.1
		  |Host: example.org
		  |Date: Tue, 20 Apr 2021 02:07:55 GMT
		  |X-Example: Example header
		  |        with some whitespace.
		  |X-Empty-Header:
		  |Cache-Control: max-age=60
		  |Cache-Control: must-revalidate
		  |Signature-Input: sig1=("@method" "@path" "@authority" \
		  |  "cache-control" "x-empty-header" "x-example");created=1618884475\
		  |  ;keyid="test-key-rsa-pss"
		  |Signature: sig1=:P0wLUszWQjoi54udOtydf9IWTfNhy+r53jGFj9XZuP4uKwxyJo1\
		  |  RSHi+oEF1FuX6O29d+lbxwwBao1BAgadijW+7O/PyezlTnqAOVPWx9GlyntiCiHzC8\
		  |  7qmSQjvu1CFyFuWSjdGa3qLYYlNm7pVaJFalQiKWnUaqfT4LyttaXyoyZW84jS8gya\
		  |  rxAiWI97mPXU+OVM64+HVBHmnEsS+lTeIsEQo36T3NFf2CujWARPQg53r58RmpZ+J9\
		  |  eKR2CD6IJQvacn5A4Ix5BUAVGqlyp8JYm+S/CWJi31PNUjRRCusCVRj05NrxABNFv3\
		  |  r5S9IXf2fYJK+eyW4AiGVMvMcOg==:""".rfc8792single

	test("§3.2 Verifying a Signature") {
		val req: Request[H] = toRequest(`§3.2_Request`)
		assertIO(
			req.signatureAuthN(sigSuite.keyidFetcher)(HttpSig(Rfc8941.Token("sig1"))),
			PureKeyId("test-key-rsa-pss")
		)
	}

	val `§4.3_Request`: HttpMessage =
		"""GET /foo HTTP/1.1
		  |Host: example.org
		  |Date: Tue, 20 Apr 2021 02:07:55 GMT
		  |X-Example: Example header
		  |        with some whitespace.
		  |X-Empty-Header:
		  |Cache-Control: max-age=60
		  |Cache-Control: must-revalidate
		  |Signature-Input: sig1=("@method" "@path" "@authority" \
		  |  "cache-control" "x-empty-header" "x-example");created=1618884475\
		  |  ;keyid="test-key-rsa-pss"
		  |Forwarded: for=192.0.2.123
		  |Signature: sig1=:P0wLUszWQjoi54udOtydf9IWTfNhy+r53jGFj9XZuP4uKwxyJo1\
		  |  RSHi+oEF1FuX6O29d+lbxwwBao1BAgadijW+7O/PyezlTnqAOVPWx9GlyntiCiHzC8\
		  |  7qmSQjvu1CFyFuWSjdGa3qLYYlNm7pVaJFalQiKWnUaqfT4LyttaXyoyZW84jS8gya\
		  |  rxAiWI97mPXU+OVM64+HVBHmnEsS+lTeIsEQo36T3NFf2CujWARPQg53r58RmpZ+J9\
		  |  eKR2CD6IJQvacn5A4Ix5BUAVGqlyp8JYm+S/CWJi31PNUjRRCusCVRj05NrxABNFv3\
		  |  r5S9IXf2fYJK+eyW4AiGVMvMcOg==:""".stripMargin

	val `§4.3_Enhanced`: HttpMessage =
		"""GET /foo HTTP/1.1
		  |Host: example.org
		  |Date: Tue, 20 Apr 2021 02:07:55 GMT
		  |X-Example: Example header
		  |        with some whitespace.
		  |X-Empty-Header:
		  |Cache-Control: max-age=60
		  |Cache-Control: must-revalidate
		  |Signature-Input: sig1=("@method" "@path" "@authority" \
		  |    "cache-control" "x-empty-header" "x-example")\
		  |    ;created=1618884475;keyid="test-key-rsa-pss", \
		  |  proxy_sig=("signature";key="sig1" "forwarded")\
		  |    ;created=1618884480;keyid="test-key-rsa";alg="rsa-v1_5-sha256"
		  |Signature: sig1=:P0wLUszWQjoi54udOtydf9IWTfNhy+r53jGFj9XZuP4uKwxyJo\
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

	test("§4.3 Multiple Signatures") {
		val req: Request[H] = toRequest(`§4.3_Enhanced`)
		assertIO(
			req.signatureAuthN(sigSuite.keyidFetcher)(HttpSig(Rfc8941.Token("sig1"))),
			PureKeyId("test-key-rsa-pss")
		) *> assertIO(
			req.signatureAuthN(sigSuite.keyidFetcher)(HttpSig(Rfc8941.Token("proxy_sig"))),
			PureKeyId("test-key-rsa")
		)
	}

	val B2_test_request: HttpMessage =
		"""POST /foo?param=value&pet=dog HTTP/1.1
		  |Host: example.com
		  |Date: Tue, 20 Apr 2021 02:07:55 GMT
		  |Content-Type: application/json
		  |Digest: SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=
		  |Content-Length: 18
		  |
		  |{"hello": "world"}""".stripMargin
	val B2_test_response: HttpMessage =
		"""HTTP/1.1 200 OK
		  |Date: Tue, 20 Apr 2021 02:07:56 GMT
		  |Content-Type: application/json
		  |Digest: SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=
		  |Content-Length: 18
		  |
		  |{"hello": "world"}
		  |""".stripMargin

	test("B.2.1 Minimal Signature Using rsa-pss-sha512") {
		val req: Request[H] = toRequest(B2_test_request)
		val Some(sigInput) = SigInput(
			"""();created=1618884475\
			  |  ;keyid="test-key-rsa-pss";alg="rsa-pss-sha512"""".rfc8792single) : @unchecked
		assertEquals(req.signingString(sigInput).toAscii, Success(
			""""@signature-params": ();created=1618884475\
			  |  ;keyid="test-key-rsa-pss";alg="rsa-pss-sha512"""".rfc8792single
		))
		val reqSignedFromSpec = req.addHeader("Signature-Input",
				"""sig1=();created=1618884475\
				  |  ;keyid="test-key-rsa-pss";alg="rsa-pss-sha512"""".rfc8792single
			).addHeader("Signature",
				"""sig1=:HWP69ZNiom9Obu1KIdqPPcu/C1a5ZUMBbqS/xwJECV8bhIQVmE\
				  |  AAAzz8LQPvtP1iFSxxluDO1KE9b8L+O64LEOvhwYdDctV5+E39Jy1eJiD7nYREBgx\
				  |  TpdUfzTO+Trath0vZdTylFlxK4H3l3s/cuFhnOCxmFYgEa+cw+StBRgY1JtafSFwN\
				  |  cZgLxVwialuH5VnqJS4JN8PHD91XLfkjMscTo4jmVMpFd3iLVe0hqVFl7MDt6TMkw\
				  |  IyVFnEZ7B/VIQofdShO+C/7MuupCSLVjQz5xA+Zs6Hw+W9ESD/6BuGs6LF1TcKLxW\
				  |  +5K+2zvDY/Cia34HNpRW5io7Iv9/b7iQ==:""".rfc8792single)
		val aio = for
			fn <- sigSuite.rsaPSSSigner
			signedReq <- req.withSigInput(Rfc8941.Token("sig1"), sigInput, fn)
		yield
			// we cannot verify the signature by comparing it with the one in the spec
			// as rsa-pss singatures change from one moment to the next. We can only verify
			// that the generate signature is valid
			IO(assertEquals(signedReq.headerValue("Signature") != None, true)) *> assertIO(
				signedReq.signatureAuthN(sigSuite.keyidFetcher)(HttpSig(Rfc8941.Token("sig1"))),
				PureKeyId("test-key-rsa-pss")
			)
		//but we also verify that the signature constructed from the spec is valid
		aio.flatten *> assertIO(
			reqSignedFromSpec.signatureAuthN(sigSuite.keyidFetcher)(HttpSig(Rfc8941.Token("sig1"))),
			PureKeyId("test-key-rsa-pss")
		)
	}

	test("B.2.2 Selective Covered Components using rsa-pss-sha512") {
		val req: Request[H] = toRequest(B2_test_request)
		val Some(sigInput) = SigInput(
			"""("@authority" "content-type")\
			  |  ;created=1618884475;keyid="test-key-rsa-pss"""".rfc8792single): @unchecked
		assertEquals(req.signingString(sigInput).toAscii, Success(
			""""@authority": example.com
			  |"content-type": application/json
			  |"@signature-params": ("@authority" "content-type")\
			  |  ;created=1618884475;keyid="test-key-rsa-pss"""".rfc8792single
		))
		val reqSignedFromSpec = req.addHeader("Signature-Input",
			"""sig1=("@authority" "content-type")\
			  |  ;created=1618884475;keyid="test-key-rsa-pss"""".rfc8792single
		).addHeader("Signature",
			"""sig1=:ik+OtGmM/kFqENDf9Plm8AmPtqtC7C9a+zYSaxr58b/E6h81gh\
			  |  JS3PcH+m1asiMp8yvccnO/RfaexnqanVB3C72WRNZN7skPTJmUVmoIeqZncdP2mlf\
			  |  xlLP6UbkrgYsk91NS6nwkKC6RRgLhBFqzP42oq8D2336OiQPDAo/04SxZt4Wx9nDG\
			  |  uy2SfZJUhsJqZyEWRk4204x7YEB3VxDAAlVgGt8ewilWbIKKTOKp3ymUeQIwptqYw\
			  |  v0l8mN404PPzRBTpB7+HpClyK4CNp+SVv46+6sHMfJU4taz10s/NoYRmYCGXyadzY\
			  |  YDj0BYnFdERB6NblI/AOWFGl5Axhhmjg==:""".rfc8792single)
		val aio = for
			fn <- sigSuite.rsaPSSSigner
			signedReq <- req.withSigInput(Rfc8941.Token("sig1"), sigInput, fn)
		yield
			// we cannot verify the signature by comparing it with the one in the spec
		// as rsa-pss singatures change from one moment to the next. We can only verify
		// that the generate signature is valid
			IO(assertEquals(signedReq.headerValue("Signature") != None, true)
			) *> assertIO(
				signedReq.signatureAuthN(sigSuite.keyidFetcher)(HttpSig(Rfc8941.Token("sig1"))),
				PureKeyId("test-key-rsa-pss")
			)
		//but we also verify that the signature constructed from the spec is valid
		aio.flatten *> assertIO(
			reqSignedFromSpec.signatureAuthN(sigSuite.keyidFetcher)(HttpSig(Rfc8941.Token("sig1"))),
			PureKeyId("test-key-rsa-pss")
		)
	}

	test("B.2.3 Full Coverage using rsa-pss-sha512") {
		val req: Request[H] = toRequest(B2_test_request)
		val siginputStr = """("date" "@method" "@path" "@query" \
								  |  "@authority" "content-type" "digest" "content-length")\
								  |  ;created=1618884475;keyid="test-key-rsa-pss"""".rfc8792single
		val Some(sigInput) = SigInput(siginputStr) : @unchecked
		assertEquals(req.signingString(sigInput).toAscii, Success(
			""""date": Tue, 20 Apr 2021 02:07:56 GMT
			  |"@method": POST
			  |"@path": /foo
			  |"@query": ?param=value&pet=dog
			  |"@authority": example.com
			  |"content-type": application/json
			  |"digest": SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=
			  |"content-length": 18
			  |"@signature-params": ("date" "@method" "@path" "@query" \
			  |  "@authority" "content-type" "digest" "content-length")\
			  |  ;created=1618884475;keyid="test-key-rsa-pss"""".rfc8792single
		))
		val reqSignedFromSpec = req.addHeader("Signature-Input","sig1="+siginputStr)
			.addHeader("Signature",
				"""sig1=:JuJnJMFGD4HMysAGsfOY6N5ZTZUknsQUdClNG51VezDgPUOW03\
				  |  QMe74vbIdndKwW1BBrHOHR3NzKGYZJ7X3ur23FMCdANe4VmKb3Rc1Q/5YxOO8p7Ko\
				  |  yfVa4uUcMk5jB9KAn1M1MbgBnqwZkRWsbv8ocCqrnD85Kavr73lx51k1/gU8w673W\
				  |  T/oBtxPtAn1eFjUyIKyA+XD7kYph82I+ahvm0pSgDPagu917SlqUjeaQaNnlZzO03\
				  |  Iy1RZ5XpgbNeDLCqSLuZFVID80EohC2CQ1cL5svjslrlCNstd2JCLmhjL7xV3NYXe\
				  |  rLim4bqUQGRgDwNJRnqobpS6C1NBns/Q==:""".rfc8792single)
		val aio = for
			fn <- sigSuite.rsaPSSSigner
			signedReq <- req.withSigInput(Rfc8941.Token("sig1"), sigInput, fn)
		yield
			// we cannot verify the signature by comparing it with the one in the spec
			// as rsa-pss singatures change from one moment to the next. We can only verify
			// that the generate signature is valid
			IO(assertEquals(signedReq.headerValue("Signature") != None, true)
			) *> assertIO(
				signedReq.signatureAuthN(sigSuite.keyidFetcher)(HttpSig(Rfc8941.Token("sig1"))),
				PureKeyId("test-key-rsa-pss")
			)
		//but we also verify that the signature constructed from the spec is valid
		aio.flatten *> assertIO(
			reqSignedFromSpec.signatureAuthN(sigSuite.keyidFetcher)(HttpSig(Rfc8941.Token("sig1"))),
			PureKeyId("test-key-rsa-pss")
		)
	}

	val B3_Request: HttpMessage =
		"""POST /foo?Param=value&pet=Dog HTTP/1.1
		  |Host: example.com
		  |Date: Tue, 20 Apr 2021 02:07:55 GMT
		  |Content-Type: application/json
		  |Content-Length: 18
		  |
		  |{"hello": "world"}""".stripMargin
	val B3_ProxyEnhanced_Request: HttpMessage =
		"""POST /foo?Param=value&pet=Dog HTTP/1.1
		  |Host: service.internal.example
		  |Date: Tue, 20 Apr 2021 02:07:55 GMT
		  |Content-Type: application/json
		  |Content-Length: 18
		  |Client-Cert: :MIIBqDCCAU6gAwIBAgIBBzAKBggqhkjOPQQDAjA6MRswGQYDVQQKD\
		  |  BJMZXQncyBBdXRoZW50aWNhdGUxGzAZBgNVBAMMEkxBIEludGVybWVkaWF0ZSBDQT\
		  |  AeFw0yMDAxMTQyMjU1MzNaFw0yMTAxMjMyMjU1MzNaMA0xCzAJBgNVBAMMAkJDMFk\
		  |  wEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8YnXXfaUgmnMtOXU/IncWalRhebrXmck\
		  |  C8vdgJ1p5Be5F/3YC8OthxM4+k1M6aEAEFcGzkJiNy6J84y7uzo9M6NyMHAwCQYDV\
		  |  R0TBAIwADAfBgNVHSMEGDAWgBRm3WjLa38lbEYCuiCPct0ZaSED2DAOBgNVHQ8BAf\
		  |  8EBAMCBsAwEwYDVR0lBAwwCgYIKwYBBQUHAwIwHQYDVR0RAQH/BBMwEYEPYmRjQGV\
		  |  4YW1wbGUuY29tMAoGCCqGSM49BAMCA0gAMEUCIBHda/r1vaL6G3VliL4/Di6YK0Q6\
		  |  bMjeSkC3dFCOOB8TAiEAx/kHSB4urmiZ0NX5r5XarmPk0wmuydBVoU4hBVZ1yhk=:
		  |
		  |{"hello": "world"}""".rfc8792single



//	//	test("B.2.3. Full Coverage") {
//	//		//1. build and test Input String
//	//		val sigParametersExpected =
//	//			"""("@request-target" "host" "date" "content-type" \
//	//			  |  "digest" "content-length");created=1618884475\
//	//			  |  ;keyid="test-key-rsa-pss"""".rfc8792single
//	//		val Some(sigIn) = SigInput[HttpMessage](IList(
//	//			`@request-target`.sf, host.sf, date.sf, `content-type`.sf, digest.sf, `content-length`.sf)(
//	//			Token("created") -> SfInt(1618884475),
//	//			Token("keyid") -> sf"test-key-rsa-pss"))
//	//		assertEquals(sigIn.canon, sigParametersExpected)
//	//
//	//		val sigInputStrExpected =
//	//			""""@request-target": post /foo?param=value&pet=dog
//	//			  |"host": example.com
//	//			  |"date": Tue, 20 Apr 2021 02:07:55 GMT
//	//			  |"content-type": application/json
//	//			  |"digest": SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=
//	//			  |"content-length": 18
//	//			  |"@signature-params": ("@request-target" "host" "date" "content-type" \
//	//			  |  "digest" "content-length");created=1618884475\
//	//			  |  ;keyid="test-key-rsa-pss"""".rfc8792single
//	//		assertEquals(rfcAppdxB2Req.signingString(sigIn), Success(sigInputStrExpected))
//	//
//	//		//2. build and test signature
//	//		val signatureStr =
//	//			"""sig1=:QNPZtqAGWN1YMtsLJ1oyQMLg9TuIwjsIBESTo1/YXUsG+6Sl1uKUdT\
//	//			  |  e9xswwrc3Ui3gUd4/tLv48NGih2TRDc1AWbEQDuy6pjroxSPtFjquubqzbszxit1arPNh\
//	//			  |  ONnyR/8yuIh3bOXfc/NYJ3KLNaWR6MKrGinCYKTNwrX/0V67EMdSgd5HHnW5xHFgKfRCj\
//	//			  |  rG3ncV+jbaeSPJ8e96RZgr8slcdwmqXdiwiIBCQDKRIQ3U2muJWvxyjV/IYhCTwAXJaUz\
//	//			  |  sQPKzR5QWelXEVdHyv4WIB2lKaYh7mAsz0/ANxFYRRSp2Joms0OAnIAFX9kKCSp4p15/Q\
//	//			  |  8L9vSIGNpQtw==:""".rfc8792single
//	//		val Success(sig1) = Signature.parse(signatureStr)
//	//		assertEquals(sig1.sigmap.canon, signatureStr)
//	//
//	//		//3. build complete request header and test signature on it
//	//		val specReq = rfcAppdxB2Req.addHeader(
//	//			`Signature-Input`(SigInputs(Token("sig1"), sigIn)))
//	//			.addHeader(Signature(sig1))
//	//		val verifiedKeyId = Await.ready(
//	//			specReq.signatureAuthN(keyidFetcher)(cred("sig1")),
//	//			2.seconds
//	//		)
//	//		// todo: this does not work. Is it the crypto algorith that is wrong or the example in the spec?
//	//		//		assertEquals(
//	//		//			verifiedKeyId.value,
//	//		//			Some(Success(run.cosy.http.auth.KeyidAgent("test-key-rsa-pss")))
//	//		//		)
//	//
//	//		//4. create our own signature and test that.
//	//		//   note: RSASSA returns different signatures at different times. So we run it again
//	//		//   Assuming rsa-pss-sha512 is the same as PS512 used by JWT
//	//
//	//		val newReq = rfcAppdxB2Req.withSigInput(Token("sig1"), sigIn)
//	//			.flatMap(_ (`test-key-rsa-pss-sigdata`))
//	//
//	//		val futureKeyId = newReq.get.signatureAuthN(keyidFetcher)(cred("sig1"))
//	//		val keyIdReady = Await.ready(futureKeyId, 2.seconds)
//	//		assertEquals(
//	//			keyIdReady.value,
//	//			Some(Success(run.cosy.http.auth.KeyidSubj("test-key-rsa-pss", testKeyPSSpub)))
//	//		)
//	//	}
end HttpMessageSigningSuite

class SigningSuiteHelpers(using pemutils: bobcats.util.PEMUtils):
	import run.cosy.http.auth.MessageSignature as MS
	import bobcats.SigningHttpMessages.`test-key-rsa-pss`
	import bobcats.util.PEMUtils.PKCS8_PEM
	def verifierFor(
		spec: Try[SPKIKeySpec[AsymmetricKeyAlg]],
		sig: Signature,
		keyId: SfString
	): IO[MS.SignatureVerifier[IO, Keyidentifier]] =
		for keyspec <- IO.fromTry(spec)
			 verifierFn <- bobcats.Verifier[IO].build(keyspec, sig)
		yield (signingStr: SigningString, signature: SignatureBytes) =>
			verifierFn(signingStr, signature).flatMap(bool =>
				if bool then IO.pure(PureKeyId(keyId.asciiStr))
				else IO.fromTry(Failure(new Throwable("could not verify test-key-rsa-pss sig")))
			)

	def publicKeySpec(keyinfo: bobcats.TestKeyPair) = pemutils.getPublicKeySpec(keyinfo.publicKey, keyinfo.keyAlg)
	def privateKeySpec(keyinfo: bobcats.TestKeyPair) = pemutils.getPrivateKeySpec(keyinfo.privatePk8Key, keyinfo.keyAlg)

	lazy val rsaPSSPubKey: Try[SPKIKeySpec[AsymmetricKeyAlg]] =
		publicKeySpec(bobcats.SigningHttpMessages.`test-key-rsa-pss`)

	lazy val rsaPubKey: Try[SPKIKeySpec[AsymmetricKeyAlg]] =
		publicKeySpec(bobcats.SigningHttpMessages.`test-key-rsa`)

	lazy val rsaPSSPrivKey: Try[PKCS8KeySpec[AsymmetricKeyAlg]]	=
		privateKeySpec(bobcats.SigningHttpMessages.`test-key-rsa-pss`)

	lazy val rsaPSSSigner: IO[ByteVector => IO[ByteVector]] =
		for
			spec <- IO.fromTry(rsaPSSPrivKey)
			fio <- bobcats.Signer[IO].build(spec, bobcats.AsymmetricKeyAlg.`rsa-pss-sha512`)
		yield fio

	/**
	 * emulate fetching the signature verification info for the keyids given in the Spec
	 * */
	def keyidFetcher(keyid: Rfc8941.SfString): IO[MS.SignatureVerifier[IO, Keyidentifier]] =
		keyid.asciiStr match
		case "test-key-rsa-pss" =>
			verifierFor(rsaPSSPubKey, AsymmetricKeyAlg.`rsa-pss-sha512`, keyid)
		case "test-key-rsa" =>
			verifierFor(rsaPubKey, AsymmetricKeyAlg.`rsa-v1_5-sha256`, keyid)
		case x => IO.fromTry(Failure(new Throwable(s"can't get info on sig $x")))

end SigningSuiteHelpers

