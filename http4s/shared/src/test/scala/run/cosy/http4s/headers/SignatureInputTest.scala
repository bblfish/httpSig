package run.cosy.http4s.headers

import cats.effect.IO
import org.http4s.*
import org.http4s.Header.Raw
import org.http4s.dsl.io.*
import org.http4s.implicits.{*, given}
import run.cosy.http.utils.StringUtils.rfc8792single
import run.cosy.http4s.headers.`Signature-Input`.{*, given}
import org.typelevel.ci.CIStringSyntax
import run.cosy.http.headers.Rfc8941.SyntaxHelper.sf
import run.cosy.http.headers.Rfc8941.{ IList, Parameterized, SfDict, SfInt, Token}
import run.cosy.http.headers.Rfc8941.token2PI
import run.cosy.http.headers.{Rfc8941, SigInput, SigInputs}
import scala.language.implicitConversions

import scala.collection.immutable.ListMap

class SignatureInputTest extends munit.FunSuite:

	// this class depends only on ietfSig types
	trait SigExample(sigName: String):
		def sigParams: IList
		def paramStr: String
		def SigInputParamsTxt: String = sigName+"="+paramStr
		def Sig: ListMap[Token, Parameterized] = SfDict(Rfc8941.Token(sigName) -> sigParams)
	end SigExample

	class `§3.2`(name: String) extends SigExample(name):
	// place all info that requires types that can be combined in ietfSig here
		val sigParams = IList(
			sf"@method", sf"@path", sf"@authority",sf"date",
			sf"cache-control",sf"x-empty-header", sf"x-example")(
			Token("created") -> SfInt(1618884475L),
			Token("keyid") -> sf"test-key-rsa-pss"
		)
		val paramStr = """("@method" "@path" "@authority" "date" \
		  |  "cache-control" "x-empty-header" "x-example");created=1618884475\
		  |  ;keyid="test-key-rsa-pss"""".rfc8792single
	end `§3.2`


	class `§4.1`(name: String) extends SigExample(name):
	// place all info that requires types that can be combined in ietfSig here
		val sigParams = IList(
			sf"@method",sf"@target-uri", sf"host",sf"date",
			sf"cache-control",sf"x-empty-header",sf"x-example")(
			Token("created") -> SfInt(1618884475L),
			Token("keyid") -> sf"test-key-rsa-pss"
		)
		val paramStr =
			"""("@method" "@target-uri" "host" "date" \
			|  "cache-control" "x-empty-header" "x-example");created=1618884475\
			|  ;keyid="test-key-rsa-pss"""".rfc8792single
	end `§4.1`

	class `Appendix B.2.1`(name: String) extends SigExample(name):
		override def sigParams: IList = IList()(
			Token("created") -> SfInt(1618884475L),
			Token("keyid") -> sf"test-key-rsa-pss",
			Token("alg") -> sf"rsa-pss-sha512"
		)
		override def paramStr: String =
			"""();created=1618884475\
			  |  ;keyid="test-key-rsa-pss";alg="rsa-pss-sha512"""".rfc8792single
	end `Appendix B.2.1`


	// this class uses types from http4s
	case class SigInputExample(ex: SigExample*):
		def headers: Headers = org.http4s.Headers(ex.map(se => Raw(ci"Signature-Input", se.SigInputParamsTxt)))
	end SigInputExample


	test("`§3.2` example of `Signature-Input` header") {
		// types that depend on http4s
		val sig1 = SigInputExample(`§3.2`("sig1"))
		val foundHeader: Option[`Signature-Input`] = sig1.headers.get[`Signature-Input`]
		val expected: Option[`Signature-Input`] = SigInputs.build(sig1.ex(0).Sig).map(`Signature-Input`.apply)
		assert(expected != None)
		assertEquals(foundHeader, expected)
	}
	test("`§4.1` example of `Signature-Input` header") {
		val sig2 =  SigInputExample(`§4.1`("sig1"))
		val foundHeader2: Option[`Signature-Input`] = sig2.headers.get[`Signature-Input`]
		val expected2: Option[`Signature-Input`] = SigInputs.build(sig2.ex(0).Sig).map(`Signature-Input`.apply)
		assert(expected2 != None)
		assertEquals(foundHeader2, expected2)
	}
	test("two `Signature-Input` headers with the same name. Last overwrites first.") {
		//note: the second header with the same name overwrites the first.
		//todo: check if that is a good behavior
		val sig3 =  SigInputExample(`§3.2`("sig1"), `§4.1`("sig1"))
		val foundHeader3: Option[`Signature-Input`] = sig3.headers.get[`Signature-Input`]
		val expected3: Option[`Signature-Input`] = SigInputs.build(sig3.ex(1).Sig).map(`Signature-Input`.apply)
		assert(expected3 != None)
		assertEquals(foundHeader3, expected3)
	}
	test("`Signature-Input` headers `§3.2` and `§4.1` with different names") {
		val sig4 = SigInputExample(`§3.2`("sig1"), `§4.1`("sig2"))
		val foundHeader4: Option[`Signature-Input`] = sig4.headers.get[`Signature-Input`]
		val expected4: Option[`Signature-Input`] =
			SigInputs.build(sig4.ex(0).Sig ++ sig4.ex(1).Sig)
				.map(`Signature-Input`.apply)
		assert(expected4 != None)
		assertEquals(foundHeader4, expected4)
	}
	test("`Signature-Input` headers from Appendix B.2.1") {
		val sig = SigInputExample(`Appendix B.2.1`("sig1"))
		val foundHeader: Option[`Signature-Input`] = sig.headers.get[`Signature-Input`]
		val expected: Option[`Signature-Input`] =
			SigInputs.build(sig.ex(0).Sig).map(`Signature-Input`.apply)
		assert(expected != None)
		assertEquals(foundHeader, expected)
	}

end SignatureInputTest

