package run.cosy.http.auth

import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.HttpMethods.{CONNECT, GET, OPTIONS, POST}
import akka.http.scaladsl.model.headers.*
import akka.http.scaladsl.util.FastFuture
import cats.effect.Async
//todo: SignatureBytes is less likely to class with objects like Signature
import bobcats.Verifier.{SigningString, Signature as SignatureBytes}
import bobcats.util.BouncyJavaPEMUtils
import bobcats.{AsymmetricKeyAlg, SPKIKeySpec, SigningHttpMessages}
import munit.CatsEffectSuite
import run.cosy.akka.http.headers.{`Signature-Input`, akkaRequestSelectorOps, akkaResponseSelectorOps}
import run.cosy.http.auth.AgentIds.PureKeyId
import run.cosy.http.headers.*
import run.cosy.http.headers.Rfc8941.*
import run.cosy.http.headers.Rfc8941.SyntaxHelper.*
import scodec.bits.ByteVector
//import com.nimbusds.jose.JWSAlgorithm
//import run.cosy.akka.http.JW2JCA
import cats.effect.IO
import run.cosy.akka.http.headers.*
import run.cosy.http.utils.StringUtils.*

import java.nio.charset.StandardCharsets
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.security.{KeyFactory, MessageDigest, Signature as JSignature}
import java.time.Clock
import java.util.Base64
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

class AkkaHttpMessageSigningSuite extends CatsEffectSuite {

	// special headers used in the spec that we won't find elsewhere

	/** example from [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-04.html#section-b.2
	 * Appendix B.2.]] of the spec */
	val rfcAppdxB2Req = HttpRequest(method = HttpMethods.POST,
		uri = Uri("/foo?param=value&pet=dog"),
		headers = Seq(
			Host(Uri.Host("example.com"), 0),
			Date(DateTime(2021, 04, 20, 02, 07, 55)),
			RawHeader("Digest", "SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE="),
		),
		entity = HttpEntity(MediaTypes.`application/json`.toContentType, """{"hello": "world"}""")
	)
	val rfcCanonReq: HttpRequest = HttpRequest(
		method = HttpMethods.GET,
		uri = Uri("/foo"),
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
		),
	)
	val `@targetUriTst` = `@target-uri`(true,Host("bblfish.net"))
	val `@schemeSecure` = `@scheme`(true)
	val `@schemeInSecure` = `@scheme`(false)
	val `@authorityTst` = `@authority`(Host("bblfish.net"))

	object `x-example` extends UntypedAkkaSelector[HttpRequest]:
		override val lowercaseName: String = "x-example"
	object `x-empty-header` extends UntypedAkkaSelector[HttpRequest]:
		override val lowercaseName: String = "x-empty-header"
	object `x-ows-header` extends UntypedAkkaSelector[HttpRequest]:
		override val lowercaseName: String = "x-ows-header"
	object `x-obs-fold-header` extends UntypedAkkaSelector[HttpRequest]:
		override val lowercaseName: String = "x-obs-fold-header"
	object `x-dictionary` extends AkkaDictSelector[HttpMessage]:
		override val lowercaseName: String = "x-dictionary"

	given specialRequestSelectorOps: run.cosy.http.headers.SelectorOps[HttpRequest] =
		akkaRequestSelectorOps.append(
			`x-example`, `x-empty-header`, `x-ows-header`, `x-obs-fold-header`, `x-dictionary`,
			`@targetUriTst`, `@schemeSecure`, `@authorityTst`
		)
	given specialResponseSelectorOps: run.cosy.http.headers.SelectorOps[HttpResponse] =
		akkaResponseSelectorOps.append(`x-dictionary`)

	given ec: ExecutionContext = scala.concurrent.ExecutionContext.global
	given clock: Clock = Clock.fixed(java.time.Instant.ofEpochSecond(16188845000), java.time.ZoneOffset.UTC).nn
	def expectedKeyedHeader(name: String, key: String, value: String) = Success("\"" + name + "\";key=\"" + key + "\": " + value)
	def expectedNameHeader(name: String, nameVal: String, value: String) = Success("\"" + name + "\";name=\"" + nameVal + "\": " + value)
	def expectedHeader(name: String, value: String) = Success("\"" + name + "\": " + value)


	test("§2.1.2 HTTP Field Examples") {
		import run.cosy.http.auth.AkkaHttpMessageSignature.signingString
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
		rfcCanonReq.signingString(signatureInputFail) match {
			case Failure(InvalidSigException(msg)) if msg.contains("x-not-implemented") => assert(true)
			case x => fail("this should have failed because header x-not-implemented is not supported")
		}

		//fail because one of the headers is missing
		rfcCanonReq.removeHeader("X-Empty-Header").signingString(signatureInput) match
			case Failure(UnableToCreateSigHeaderException(msg)) if msg.contains("x-empty-header") => assert(true)
			case x => fail("not failing in the right way? received:"+x.toString)

		//fail because keyid is not specified (note: this is no longer required in the spec,..., check)
		val ilNoKeyId = IList( date.sf, host.sf)()
		val Some(signatureInputNoKeyId) = SigInput(ilNoKeyId): @unchecked
		assert(rfcCanonReq.signingString(signatureInputNoKeyId).isSuccess)

	}

	test("§2.1.3 Dictionary Structured Field Members") {
		assertEquals(`x-dictionary`.signingStringFor(rfcCanonReq),
			expectedHeader("x-dictionary", "a=1, b=2;x=1;y=2, c=(a b c)"))
		assertEquals(`x-dictionary`.signingString(rfcCanonReq),
			expectedHeader("x-dictionary", "a=1, b=2;x=1;y=2, c=(a b c)"))
		assertEquals(`x-dictionary`.signingString(rfcCanonReq,Rfc8941.Params()),
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
		val si: Option[SigInput] = SigInput(IList(`@targetUriTst`.sf, `@authorityTst`.sf, date.sf, `cache-control`.sf,
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

	val `req_2.2.2` = HttpRequest(POST, Uri("/path?param=value"),
		headers = Seq(Host("www.example.com"))
	)
	val `req_2.2.3`: HttpRequest = `req_2.2.2`
	val `req_2.2.4`: HttpRequest = `req_2.2.2`
	val `req_2.2.5`: HttpRequest = `req_2.2.2`
	val `req_2.2.6`: HttpRequest = `req_2.2.2`
	val `req_2.2.7`: HttpRequest = `req_2.2.2`
	val `req_2.2.8`: HttpRequest = HttpRequest( POST,
			Uri("/path?param=value&foo=bar&baz=batman"
		).withHost("www.example.com")
	)
	val `req_2.2.8a`: HttpRequest = HttpRequest( POST,
			Uri("/path?queryString"
		).withHost("www.example.com")
	)

	test("§2.2.2 Method") {
		assertEquals(
			`@method`.signingString(`req_2.2.2`),
			expectedHeader("@method", "POST")
		)
	}

	test("§2.2.3 Target URI") {
		assertEquals(
			`@targetUriTst`.signingString(`req_2.2.3`),
			expectedHeader("@target-uri","https://www.example.com/path?param=value")
		)

		assertEquals(
			`@targetUriTst`.signingString(`req_2.2.3`.removeHeader("Host")),
			expectedHeader("@target-uri","https://bblfish.net/path?param=value")
		)

		assertEquals(
			`@targetUriTst`.signingString(HttpRequest(GET, Uri("http://www.example.com"))),
			//todo: should this really end in a slash??
			expectedHeader(`@targetUriTst`.lowercaseName,"http://www.example.com")
		)

		assertEquals(
			`@targetUriTst`.signingString(HttpRequest(GET, Uri("http://www.example.com/a/"))),
			expectedHeader(`@targetUriTst`.lowercaseName, "http://www.example.com/a/")
		)
	}

	test("§2.2.4 Authority") {
		assertEquals(
			`@authorityTst`.signingString(`req_2.2.4`),
			expectedHeader("@authority","www.example.com")
		)
		assertEquals(
			`@authorityTst`.signingString(`req_2.2.4`.removeHeader("Host")),
			expectedHeader("@authority","bblfish.net")
		)
	}

	test("§2.2.5 Scheme") {
		assertEquals(
			`@schemeSecure`.signingString(`req_2.2.5`),
			expectedHeader("@scheme","https")
		)
		assertEquals(
			`@schemeInSecure`.signingString(`req_2.2.5`),
			expectedHeader("@scheme","http")
		)

	}

	def `request-target`(value: String): Success[String] = expectedHeader("@request-target", value)
	test("§2.2.6. Request Target") {
		import akka.http.scaladsl.model.HttpMethods.*

		assertEquals(
			`@request-target`.signingString(`req_2.2.6`),
			`request-target`("/path?param=value")
		)
		val reqGETAbsoluteURI = HttpRequest(GET, Uri("https://www.example.com/path?param=value"))
		assertEquals(
			`@request-target`.signingString(reqGETAbsoluteURI),
			`request-target`("/path?param=value")
		)
		val req2 = HttpRequest(POST, Uri("/a/b"),
			Seq(Host("www.example.com"))
		)
		assertEquals(
			`@request-target`.signingString(req2),
			`request-target`("/a/b")
		)

		val req3 = HttpRequest(GET, Uri("http://www.example.com/a/"))
		assertEquals(
			`@request-target`.signingString(req3),
			`request-target`("/a/")
		)

		assertEquals(
			`@request-target`.signingString(HttpRequest(GET, Uri("http://www.example.com/a"))),
			`request-target`("/a")
		)
		//CONNECT: Not sure if this is allowed by Akka. It is not possible to create an HttpRequest with it
//		val req5 = HttpRequest(CONNECT, Uri("server.example.com:80",Uri.ParsingMode.Relaxed),
//			Seq(Host("www.example.com")))
//		assertEquals(
//			`@request-target`.signingString(req5),
//			`request-target`("connect /")
//		)
		val req6 = HttpRequest(OPTIONS, Uri("*"), headers = Seq(Host("server.example.com")))
		assertEquals(
			`@request-target`.signingString(req6),
			`request-target`("*")
		)
	}

	test("§2.2.6. Request Target for CONNECT (does not work for Akka)".fail) {
		//this does not work for AKKA because akka returns //www.example.com:80

		val reqCONNECT = HttpRequest(CONNECT,
			Uri("", Uri.Authority(Uri.Host("www.example.com"), 80), Uri.Path.Empty, None, None)
		).withHeaders(Host(Uri.Host("www.example.com")))

		assertEquals(
			`@request-target`.signingString(reqCONNECT),
			`request-target`("www.example.com:80")
		)
	}

	test("§2.2.7 Path") {
		assertEquals(
			`@path`.signingString(`req_2.2.7`),
			expectedHeader("@path","/path")
		)
	}

	test("§2.2.8 Query") {
		assertEquals(
			`@query`.signingString(`req_2.2.8`),
			expectedHeader("@query","?param=value&foo=bar&baz=batman")
		)
		assertEquals(
			`@query`.signingString(`req_2.2.8a`),
			expectedHeader("@query","?queryString")
		)
		//is this correct?
		assertEquals(
			`@query`.signingString(HttpRequest(GET, Uri("http://www.example.com/a"))),
			expectedHeader("@query","")
		)
		//is this correct?
		assertEquals(
			`@query`.signingString(HttpRequest(GET, Uri("http://www.example.com/a?"))),
			expectedHeader("@query","?")
		)

	}

	test("§2.2.9 Query Parameters") {
		assertEquals(
			`@query-params`.signingString(`req_2.2.8`,Params(Token("name") -> SfString("baz"))),
			expectedNameHeader("@query-params","baz","batman")
		)
		assertEquals(
			`@query-params`.signingString(`req_2.2.8`,Params(Token("name") -> SfString("qux"))),
			expectedNameHeader("@query-params","qux","")
		)
		assertEquals(
			`@query-params`.signingString(`req_2.2.8`,Params(Token("name") -> SfString("param"))),
			expectedNameHeader("@query-params","param","value")
		)

	}

	test("§2.2.10 Status Code") {
		assertEquals(
			`@status`.signingString(HttpResponse(StatusCodes.OK).withHeaders(Date(DateTime.now))),
			Success("\"@status\": 200")
		)
	}

	test("§2.2.11 Request-Response Signature Binding") {

	}



	test("§2.3 Creating the Signature Input String") {
		import run.cosy.http.auth.AkkaHttpMessageSignature.signingString
		/**
		 * [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-04.html#section-2.5 example request in §2.5]] * */
		val rfcSigInreq = HttpRequest(
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

		val Some(sigInHandBuilt) = SigInput(sigInIlist): @unchecked
		val Some(sigin) = SigInput(siginStr): @unchecked
		assertEquals(rfcSigInreq.signingString(sigin).toAscii, Success(expectedSigInStr))
	}

	extension (bytes: Try[ByteVector])
		def toAscii: Try[String] = bytes.flatMap(_.decodeAscii.toTry)

	//
	//	//
	//	// signature tests
	//	//
	//
	//	import TestMessageSigningRFCFn.*
	//
	//	test("do the keys parse without throwing an exception?") {
	//		testKeyRSApub
	//		testKeyRSAPriv
	//		testKeyPSSpub
	//		testKeyPSSpriv
	//	}
	//
	//	test("4.3 Multiple Signatures - spec test") {
	//		import java.util.Base64
	//
	//		// let's test the [[https://github.com/httpwg/http-extensions/issues/1493#issuecomment-827103670 fix to version 04. of the spec]]
	//		val sigInputStr: String =
	//			""""signature";key="sig1": \
	//			  |  :YlizxWySaL8RiCQOWpl/8TBLlinl/O9K5n+WCYladKkRfmZ4wdo42ikCrepkIoPd\
	//			  |  csPIx5wYc53Kpq6PLmv3fRk/+BtFSJNdrfClMEg4kX8utYuMQhBHSLiEiDRjNSTWX\
	//			  |  Wk8hwutEGijbna3FvBVzy1oa5tT08w/ffN7d/6dup1FWVt90KhK1Cx3RkQveMxAKC\
	//			  |  3HH6Q26lAgJ54MyLqLrLXcjQKWhjWzkMkVjho4JLy87GTU6k4eIQxB4xDHbavbJKE\
	//			  |  jS0Vlg2pqmcUkGVdL4zQ3NOOttIlKC1HL1SodXNd7UBM0C0R1GGqEi4Lsm9UKWuQP\
	//			  |  vPFTW7qIgvjAthv/lA==:
	//			  |"x-forwarded-for": 192.0.2.123
	//			  |"@signature-params": ("signature";key="sig1" "x-forwarded-for")\
	//			  |  ;created=1618884480;keyid="test-key-rsa";alg="rsa-v1_5-sha256"""".rfc8792single
	//		val proxySig: String =
	//			"""FSqkfwt17TLKqjjWrW9vf6a/r3329amAO3e7ByjkT60jjFTq4xdO74\
	//			  |    JTHrpz6DMSlQOKmhIiz8mq7T5SYOjfUZrKXpbP6jUFTStUa4HvNNvjhZc1jiHk9\
	//			  |    IhGGPPeOdRcTrzjDxSS+2l7G3nSpJ4t2LjtLnEPa1FIldgnJqwIa0SCiEPWFmnJ\
	//			  |    fTdc4VW2ngvYhuKUKFz/Jyx0GfmKQ4lAWxQwVtqRSURTscdY3VvxR+GpydqF8gQ\
	//			  |    U8iYslRHlRxBh+29cADAHVnT5j1iBkVdAfLS59xCYLnUc3UG7UfxU6kU1QgJ+2n\
	//			  |    A5NNQsxXeREcCFTe2FnjOy2atxG8bm+O8ZcA==""".rfc8792single
	//
	//		val sigBytes = Base64.getDecoder.decode(proxySig)
	//
	//		val javaSig = sha256rsaSig()
	//		javaSig.initVerify(testKeyRSApub)
	//		javaSig.update(sigInputStr.getBytes(StandardCharsets.US_ASCII))
	//		assert(javaSig.verify(sigBytes.toArray))
	//	}
	//
	//
	//	import run.cosy.http.headers.Rfc8941.Serialise.given
	//
	//	import scala.concurrent.duration.given
	//
	test("Appendix B.2.1. Minimal Signature Header using rsa-pss-sha512") {
		import run.cosy.http.auth.AkkaHttpMessageSignature.{signatureAuthN, signingString}
		import run.cosy.http.headers.Rfc8941.Serialise.given

		//1. build and test Input String
		val sigInputStrExpected =
			""""@signature-params": ();created=1618884475;keyid="test-key-rsa-pss"\
			  |  ;alg="rsa-pss-sha512"""".rfc8792single
		val Some(sigIn) = SigInput(IList()(
			Token("created") -> SfInt(1618884475),
			Token("keyid") -> sf"test-key-rsa-pss",
			Token("alg") -> sf"rsa-pss-sha512"
		)): @unchecked
		assertEquals(rfcAppdxB2Req.signingString(sigIn).toAscii, Success(sigInputStrExpected))

		//2. build and test signature
		val signatureStr =
			"""sig1=:HWP69ZNiom9Obu1KIdqPPcu/C1a5ZUMBbqS/xwJECV8bhIQVmE\
			  |  AAAzz8LQPvtP1iFSxxluDO1KE9b8L+O64LEOvhwYdDctV5+E39Jy1eJiD7nYREBgx\
			  |  TpdUfzTO+Trath0vZdTylFlxK4H3l3s/cuFhnOCxmFYgEa+cw+StBRgY1JtafSFwN\
			  |  cZgLxVwialuH5VnqJS4JN8PHD91XLfkjMscTo4jmVMpFd3iLVe0hqVFl7MDt6TMkw\
			  |  IyVFnEZ7B/VIQofdShO+C/7MuupCSLVjQz5xA+Zs6Hw+W9ESD/6BuGs6LF1TcKLxW\
			  |  +5K+2zvDY/Cia34HNpRW5io7Iv9/b7iQ==:""".rfc8792single
		val sigs: Try[Signatures] = run.cosy.http.headers.Signature.parse(signatureStr)
		assertEquals(sigs.get.sigmap.canon, signatureStr)

		//3. build complete request header and test signature on it
		val specReq = rfcAppdxB2Req.addHeader(
			`Signature-Input`(run.cosy.http.headers.SigInputs(Token("sig1"), sigIn)))
			.addHeader(Signature(sigs.get))

		import run.cosy.http.auth.AkkaHttpMessageSignature.given
		import run.cosy.http.auth.AkkaHttpMessageSigningSuite.{cred, keyidFetcher}

		import concurrent.duration.Duration
		val verifiedKeyId: IO[Keyidentifier] =
			specReq.signatureAuthN[IO, Keyidentifier](keyidFetcher)(cred("sig1"))
		assertIO(verifiedKeyId, PureKeyId("test-key-rsa-pss"))
		//		// todo: this does not work. Is it the crypto algorith that is wrong or the example in the spec?
		//		//		assertEquals(
		//		//			verifiedKeyId.value,
		//		//			Some(Success(run.cosy.http.auth.KeyidAgent("test-key-rsa-pss")))
		//		//		)
		//
		//		//4. create our own signature and test that.
		//		//   note: RSASSA returns different signatures at different times. So we run it again
		//		//   Assuming rsa-pss-sha512 is the same as PS512 used by JWT
		//
		//		val newReq = rfcAppdxB2Req.withSigInput(Token("sig1"), sigIn)
		//			.flatMap(_ (`test-key-rsa-pss-sigdata`))
		//
		//		val futureKeyId = newReq.get.signatureAuthN(keyidFetcher)(cred("sig1"))
		//		val keyIdReady = Await.ready(futureKeyId, 2.seconds)
		//		assertEquals(
		//			keyIdReady.value,
		//			Some(Success(run.cosy.http.auth.KeyidSubj("test-key-rsa-pss", testKeyPSSpub)))
		//		)
	}
	//
	//	test("B.2.2 Header Coverage - Spec Test") {
	//		import java.util.Base64
	//		val sigInputStr =
	//			""""host": example.com
	//			  |"date": Tue, 20 Apr 2021 02:07:55 GMT
	//			  |"content-type": application/json
	//			  |"@signature-params": ("host" "date" "content-type");created=1618884475;keyid="test-key-rsa-pss"""".stripMargin
	//		val signature = "NtIKWuXjr4SBEXj97gbick4O95ff378I0CZOa2VnIeEXZ1itzAdqTp" +
	//			"SvG91XYrq5CfxCmk8zz1Zg7ZGYD+ngJyVn805r73rh2eFCPO+ZXDs45Is/Ex8srzGC9sf" +
	//			"VZfqeEfApRFFe5yXDmANVUwzFWCEnGM6+SJVmWl1/jyEn45qA6Hw+ZDHbrbp6qvD4N0S9" +
	//			"2jlPyVVEh/SmCwnkeNiBgnbt+E0K5wCFNHPbo4X1Tj406W+bTtnKzaoKxBWKW8aIQ7rg9" +
	//			"2zqE1oqBRjqtRi5/Q6P5ZYYGGINKzNyV3UjZtxeZNnNJ+MAnWS0mofFqcZHVgSU/1wUzP" +
	//			"7MhzOKLca1Yg=="
	//		println(signature)
	//		val sigBytes = Base64.getDecoder.decode(signature)
	//
	//		val javaSig = `rsa-pss-sha512`()
	//		javaSig.initVerify(testKeyPSSpub)
	//		javaSig.update(sigInputStr.getBytes(StandardCharsets.US_ASCII))
	//		//todo:		assert(javaSig.verify(sigBytes.toArray))
	//	}
	//
	//	test("B.2.2. Header Coverage") {
	//		//1. build and test Input String
	//		val sigParametersExpected =
	//			"""("host" "date" "content-type");created=1618884475\
	//			  |  ;keyid="test-key-rsa-pss"""".rfc8792single
	//		val Some(sigIn) = SigInput[HttpMessage](IList(host.sf, date.sf, `content-type`.sf)(
	//			Token("created") -> SfInt(1618884475),
	//			Token("keyid") -> sf"test-key-rsa-pss"))
	//		assertEquals(sigIn.canon, sigParametersExpected)
	//
	//		val sigInputStrExpected =
	//			""""host": example.com
	//			  |"date": Tue, 20 Apr 2021 02:07:55 GMT
	//			  |"content-type": application/json
	//			  |"@signature-params": ("host" "date" "content-type");created=1618884475\
	//			  |  ;keyid="test-key-rsa-pss"""".rfc8792single
	//		assertEquals(rfcAppdxB2Req.signingString(sigIn), Success(sigInputStrExpected))
	//
	//		//2. build and test signature
	//		val signatureStr =
	//			"""sig1=:NtIKWuXjr4SBEXj97gbick4O95ff378I0CZOa2VnIeEXZ1itzAdqTp\
	//			  |  SvG91XYrq5CfxCmk8zz1Zg7ZGYD+ngJyVn805r73rh2eFCPO+ZXDs45Is/Ex8srzGC9sf\
	//			  |  VZfqeEfApRFFe5yXDmANVUwzFWCEnGM6+SJVmWl1/jyEn45qA6Hw+ZDHbrbp6qvD4N0S9\
	//			  |  2jlPyVVEh/SmCwnkeNiBgnbt+E0K5wCFNHPbo4X1Tj406W+bTtnKzaoKxBWKW8aIQ7rg9\
	//			  |  2zqE1oqBRjqtRi5/Q6P5ZYYGGINKzNyV3UjZtxeZNnNJ+MAnWS0mofFqcZHVgSU/1wUzP\
	//			  |  7MhzOKLca1Yg==:""".rfc8792single
	//		val Success(sig1) = Signature.parse(signatureStr)
	//		assertEquals(sig1.sigmap.canon, signatureStr)
	//
	//		//3. build complete request header and test signature on it
	//		val specReq: HttpRequest = rfcAppdxB2Req.addHeader(
	//			`Signature-Input`(SigInputs(Token("sig1"), sigIn)))
	//			.addHeader(Signature(sig1))
	//		//verify that the new req still has the same signing string as expected
	//		assertEquals(specReq.signingString(sigIn), Success(sigInputStrExpected))
	//
	//		//		println(specReq.documented)
	//		val verifiedKeyId = Await.ready(
	//			specReq.signatureAuthN(keyidFetcher)(cred("sig1")),
	//			2.seconds
	//		)
	//		// todo: this does not work. Is it the crypto algorith that is wrong or the example in the spec?
	//		//		assertEquals(
	//		//			verifiedKeyId.value,
	//		//			Some(Success(run.cosy.http.auth.KeyidAgent("test-key-rsa-pss",testKeyPSSpub)))
	//		//		)
	//
	//		//4. create our own signature and test that.
	//		//   note: RSASSA returns different signatures at different times. So we run it again
	//		//   Assuming rsa-pss-sha512 is the same as PS512 used by JWT
	//
	//		val newReq = rfcAppdxB2Req.withSigInput(Token("sig1"), sigIn)
	//			.flatMap(_ (`test-key-rsa-pss-sigdata`))
	//
	//		val futureKeyId = newReq.get.signatureAuthN(keyidFetcher)(cred("sig1"))
	//		val keyIdReady = Await.ready(futureKeyId, 2.seconds)
	//		assertEquals(
	//			keyIdReady.value,
	//			Some(Success(run.cosy.http.auth.KeyidSubj("test-key-rsa-pss", testKeyPSSpub)))
	//		)
	//	}
	//
	//	test("B.2.3. Full Coverage") {
	//		//1. build and test Input String
	//		val sigParametersExpected =
	//			"""("@request-target" "host" "date" "content-type" \
	//			  |  "digest" "content-length");created=1618884475\
	//			  |  ;keyid="test-key-rsa-pss"""".rfc8792single
	//		val Some(sigIn) = SigInput[HttpMessage](IList(
	//			`@request-target`.sf, host.sf, date.sf, `content-type`.sf, digest.sf, `content-length`.sf)(
	//			Token("created") -> SfInt(1618884475),
	//			Token("keyid") -> sf"test-key-rsa-pss"))
	//		assertEquals(sigIn.canon, sigParametersExpected)
	//
	//		val sigInputStrExpected =
	//			""""@request-target": post /foo?param=value&pet=dog
	//			  |"host": example.com
	//			  |"date": Tue, 20 Apr 2021 02:07:55 GMT
	//			  |"content-type": application/json
	//			  |"digest": SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=
	//			  |"content-length": 18
	//			  |"@signature-params": ("@request-target" "host" "date" "content-type" \
	//			  |  "digest" "content-length");created=1618884475\
	//			  |  ;keyid="test-key-rsa-pss"""".rfc8792single
	//		assertEquals(rfcAppdxB2Req.signingString(sigIn), Success(sigInputStrExpected))
	//
	//		//2. build and test signature
	//		val signatureStr =
	//			"""sig1=:QNPZtqAGWN1YMtsLJ1oyQMLg9TuIwjsIBESTo1/YXUsG+6Sl1uKUdT\
	//			  |  e9xswwrc3Ui3gUd4/tLv48NGih2TRDc1AWbEQDuy6pjroxSPtFjquubqzbszxit1arPNh\
	//			  |  ONnyR/8yuIh3bOXfc/NYJ3KLNaWR6MKrGinCYKTNwrX/0V67EMdSgd5HHnW5xHFgKfRCj\
	//			  |  rG3ncV+jbaeSPJ8e96RZgr8slcdwmqXdiwiIBCQDKRIQ3U2muJWvxyjV/IYhCTwAXJaUz\
	//			  |  sQPKzR5QWelXEVdHyv4WIB2lKaYh7mAsz0/ANxFYRRSp2Joms0OAnIAFX9kKCSp4p15/Q\
	//			  |  8L9vSIGNpQtw==:""".rfc8792single
	//		val Success(sig1) = Signature.parse(signatureStr)
	//		assertEquals(sig1.sigmap.canon, signatureStr)
	//
	//		//3. build complete request header and test signature on it
	//		val specReq = rfcAppdxB2Req.addHeader(
	//			`Signature-Input`(SigInputs(Token("sig1"), sigIn)))
	//			.addHeader(Signature(sig1))
	//		val verifiedKeyId = Await.ready(
	//			specReq.signatureAuthN(keyidFetcher)(cred("sig1")),
	//			2.seconds
	//		)
	//		// todo: this does not work. Is it the crypto algorith that is wrong or the example in the spec?
	//		//		assertEquals(
	//		//			verifiedKeyId.value,
	//		//			Some(Success(run.cosy.http.auth.KeyidAgent("test-key-rsa-pss")))
	//		//		)
	//
	//		//4. create our own signature and test that.
	//		//   note: RSASSA returns different signatures at different times. So we run it again
	//		//   Assuming rsa-pss-sha512 is the same as PS512 used by JWT
	//
	//		val newReq = rfcAppdxB2Req.withSigInput(Token("sig1"), sigIn)
	//			.flatMap(_ (`test-key-rsa-pss-sigdata`))
	//
	//		val futureKeyId = newReq.get.signatureAuthN(keyidFetcher)(cred("sig1"))
	//		val keyIdReady = Await.ready(futureKeyId, 2.seconds)
	//		assertEquals(
	//			keyIdReady.value,
	//			Some(Success(run.cosy.http.auth.KeyidSubj("test-key-rsa-pss", testKeyPSSpub)))
	//		)
	//	}
}

object AkkaHttpMessageSigningSuite {
	//	java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider)
	//
	//	lazy val testKeyRSApub = {
	//		java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider)
	//		import java.math.BigInteger
	//		import java.security.spec.RSAPublicKeySpec
	//		val pkcs1PublicKey = org.bouncycastle.asn1.pkcs.RSAPublicKey.getInstance(testKeyRSApubStr.base64Decode.unsafeArray).nn
	//		val modulus = pkcs1PublicKey.getModulus.nn
	//		val publicExponent = pkcs1PublicKey.getPublicExponent.nn
	//		val keySpec = new RSAPublicKeySpec(modulus, publicExponent)
	//		KeyFactory.getInstance("RSA").nn.generatePublic(keySpec).nn
	//	}
	//	lazy val testKeyRSAPriv = {
	//		KeyFactory.getInstance("RSA").nn
	//			.generatePrivate(new PKCS8EncodedKeySpec(testKeyRSAprivStr.base64Decode.unsafeArray))
	//	}
	//	lazy val testKeyPSSpub = KeyFactory.getInstance("RSA").nn
	//		.generatePublic(new X509EncodedKeySpec(testKeyPSSPubStr.base64Decode.unsafeArray))
	//	lazy val testKeyPSSpriv = KeyFactory.getInstance("RSASSA-PSS").nn
	//		.generatePrivate(new PKCS8EncodedKeySpec(testKeyPSSPrivStr.base64Decode.unsafeArray)).nn
	//
	//	/** Sigdata should always be new too in multithreaded environements, as it uses stateful signatures. */
	//	def `test-key-rsa-pss-sigdata`: SigningData = bobcats.PKCS8KeySpec(
	//		scodec.bits.ByteVector.fromBase64Descriptive(testKeyPSSpriv), )
	//		SigningData(testKeyPSSpriv, `rsa-pss-sha512`())
	//	def `test-key-rsa-sigdata`: SigningData = SigningData(testKeyRSAPriv, sha256rsaSig())

	import run.cosy.http.auth.MessageSignature as MS

	/**
	 * emulate fetching the signature verification info for the keyids given in the Spec
	 * */
	def keyidFetcher(keyid: Rfc8941.SfString): IO[MS.SignatureVerifier[IO, Keyidentifier]] =
		keyid.asciiStr match
		case "test-key-rsa-pss" =>
			import SigningHttpMessages.`test-key-rsa-pss` //todo: this is not the keyid as the hash spec is missing, rename in bobcats
			for {
				keyspec <- IO.fromTry(BouncyJavaPEMUtils.getPublicKeyFromPEM(`test-key-rsa-pss`.publicKey, `test-key-rsa-pss`.keyAlg))
				verifierFn <- bobcats.Verifier[IO].build(keyspec, AsymmetricKeyAlg.`rsa-pss-sha512`)
			} yield {
				(signingStr: SigningString, signature: SignatureBytes) =>
					verifierFn(signingStr, signature).flatMap(bool =>
						if bool then IO.pure(PureKeyId(keyid.asciiStr))
						else IO.fromTry(Failure(new Throwable("could not verify test-key-rsa-pss sig")))
					)
			}
		//		case "test-key-rsa" =>
		//		FastFuture.successful(keyidVerifier(keyid, testKeyRSApub, sha256rsaSig()))
		case x => IO.fromTry(Failure(new Throwable(s"can't get info on sig $x")))


	//	/** one always has to create a new signature on each verification if in multi-threaded environement */
	//	def `rsa-pss-sha512`(): JSignature =
	//		//	   also tried see [[https://tools.ietf.org/html/rfc7518 JSON Web Algorithms (JWA) RFC]]
	//		//		com.nimbusds.jose.crypto.impl.RSASSA.getSignerAndVerifier(JWSAlgorithm.PS512, new BouncyCastleProvider() )
	//		val rsapss = JSignature.getInstance("RSASSA-PSS")
	//		import java.security.spec.{MGF1ParameterSpec, PSSParameterSpec}
	//		val pssSpec = new PSSParameterSpec(
	//			"SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 512 / 8, 1)
	//		println("PSSParameterSpec=" + pssSpec)
	//		rsapss.setParameter(pssSpec)
	//		rsapss
	//	//todo: remove dependence on JW2JCA
	//	def sha256rsaSig(): JSignature = JW2JCA.getSignerAndVerifier("SHA256withRSA").get
	//	/** we are using [[https://github.com/solid/authentication-panel/blob/main/proposals/HttpSignature.md HttpSig]]
	//	 * extension to verify Signing HTTP Messages */

	def cred(signame: String) = HttpSig(Rfc8941.Token(signame))


}
