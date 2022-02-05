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

package run.cosy.http4s.headers

import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.SyntaxHelper.sf
import run.cosy.http.headers.Rfc8941.{IList, PItem, Parameterized, SfDict, SfInt, Token}
import run.cosy.http.utils.StringUtils.{base64Decode, rfc8792single}

import scala.collection.immutable.ListMap
import scala.language.implicitConversions

//
//All classes here depends only on ietfSig types
//
trait `Signature-Input_Example`(sigName: String):
	def sigParams: IList
	def paramStr: String
	def SigInputParamsTxt: String = sigName + "=" + paramStr
	def Sig: ListMap[Token, Parameterized] = SfDict(Rfc8941.Token(sigName) -> sigParams)
end `Signature-Input_Example`

trait Signature_Example:
	val name: String
	val sigName: String
	val lspace: Int = 0
	val rspace: Int = 0
	val `before=space`: Int = 0 //this is illegal space,
	val `after=space`: Int = 0 //this is illegal space

	def illegalSpace: Boolean = `after=space` != 0 || `before=space` != 0
	def bytes: String
	def SigString: String = (" " * lspace) + sigName + (" " * `before=space`) +
		"=" + (" " * `after=space`) + ":" + bytes + ":" + (" " * rspace)
	def Signature: SfDict = SfDict(Rfc8941.Token(sigName) -> sigParams)
	def sigParams: Parameterized = PItem[Rfc8941.Bytes](bytes.base64Decode)
end Signature_Example

object Signature_Examples:
	case class `§3.2`(
		override val sigName: String,
		override val lspace: Int = 0,
		override val rspace: Int = 0,
		override val `before=space`: Int = 0,
		override val `after=space`: Int = 0
	) extends Signature_Example:
		val name = "§3.2"
		val bytes =
			"""P0wLUszWQjoi54udOtydf9IWTfNhy+r53jGFj9XZuP4uKwxyJo1\
			  |  RSHi+oEF1FuX6O29d+lbxwwBao1BAgadijW+7O/PyezlTnqAOVPWx9GlyntiCiHzC8\
			  |  7qmSQjvu1CFyFuWSjdGa3qLYYlNm7pVaJFalQiKWnUaqfT4LyttaXyoyZW84jS8gya\
			  |  rxAiWI97mPXU+OVM64+HVBHmnEsS+lTeIsEQo36T3NFf2CujWARPQg53r58RmpZ+J9\
			  |  eKR2CD6IJQvacn5A4Ix5BUAVGqlyp8JYm+S/CWJi31PNUjRRCusCVRj05NrxABNFv3\
			  |  r5S9IXf2fYJK+eyW4AiGVMvMcOg==""".rfc8792single
	end `§3.2`

	case class `§4.3_second`(
		override val sigName: String,
		override val lspace: Int = 0,
		override val rspace: Int = 0,
		override val `before=space`: Int = 0,
		override val `after=space`: Int = 0
	) extends Signature_Example:
		val name = "§4.3"
		val bytes =
			"""cjGvZwbsq9JwexP9TIvdLiivxqLINwp/ybAc19KOSQuLvtmMt3EnZxNiE+797dXK2cj\
			  |PPUFqoZxO8WWx1SnKhAU9SiXBr99NTXRmA1qGBjqus/1Yxwr8keB8xzFt4inv3J3zP0\
			  |k6TlLkRJstkVnNjuhRIUA/ZQCo8jDYAl4zWJJjppy6Gd1XSg03iUa0sju1yj6rcKbMA\
			  |BBuzhUz4G0u1hZkIGbQprCnk/FOsqZHpwaWvY8P3hmcDHkNaavcokmq+3EBDCQTzgwL\
			  |qfDmV0vLCXtDda6CNO2Zyum/pMGboCnQn/VkQ+j8kSydKoFg6EbVuGbrQijth6I0dDX\
			  |2/HYcJg==""".rfc8792single
	end `§4.3_second`
end Signature_Examples

object `Signature-Input_Examples`:
	class `§3.2`(name: String) extends `Signature-Input_Example`(name) :
		// place all info that requires types that can be combined in ietfSig here
		val sigParams = IList(
			sf"@method", sf"@path", sf"@authority", sf"date",
			sf"cache-control", sf"x-empty-header", sf"x-example")(
			Token("created") -> SfInt(1618884475L),
			Token("keyid") -> sf"test-key-rsa-pss"
		)
		val paramStr =
			"""("@method" "@path" "@authority" "date" \
			  |  "cache-control" "x-empty-header" "x-example");created=1618884475\
			  |  ;keyid="test-key-rsa-pss"""".rfc8792single
	end `§3.2`

	class `§4.1`(name: String) extends `Signature-Input_Example`(name) :
		// place all info that requires types that can be combined in ietfSig here
		val sigParams = IList(
			sf"@method", sf"@target-uri", sf"host", sf"date",
			sf"cache-control", sf"x-empty-header", sf"x-example")(
			Token("created") -> SfInt(1618884475L),
			Token("keyid") -> sf"test-key-rsa-pss"
		)
		val paramStr =
			"""("@method" "@target-uri" "host" "date" \
			  |  "cache-control" "x-empty-header" "x-example");created=1618884475\
			  |  ;keyid="test-key-rsa-pss"""".rfc8792single
	end `§4.1`

	class `Appendix B.2.1`(name: String) extends `Signature-Input_Example`(name) :
		override def sigParams: IList = IList()(
			Token("created") -> SfInt(1618884475L),
			Token("keyid") -> sf"test-key-rsa-pss",
			Token("alg") -> sf"rsa-pss-sha512"
		)
		override def paramStr: String =
			"""();created=1618884475\
			  |  ;keyid="test-key-rsa-pss";alg="rsa-pss-sha512"""".rfc8792single
	end `Appendix B.2.1`
end `Signature-Input_Examples`

