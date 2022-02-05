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

import cats.parse.Parser0
import org.http4s.{Header, ParseFailure, ParseResult}
import org.http4s.headers.{`Accept-Language`, `Cache-Control`}
import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.{Rfc8941, SigInput, SigInputs}
import org.typelevel.ci.*
import cats.syntax.all.*
import org.http4s.util.{Renderer, Writer}
import run.cosy.http.Http
import run.cosy.http4s.Http4sTp

import scala.collection.immutable.ListMap

/**
 * [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-the-signature-input-http-fi 4.1 The 'Signature-Input' HTTP header]]
 */
case class `Signature-Input`(values: SigInputs)

object `Signature-Input` extends run.cosy.http.auth.SignatureInputMatcher[Http4sTp.type]:

	override type SI = Header.Raw
	override def apply(name: Rfc8941.Token, sigInput: SigInput): SI =
		Header.Raw(headerInstance.name,headerInstance.value(new `Signature-Input`(SigInputs(name,sigInput))))
	override def unapply(h: Http.Header[Http4sTp.type]): Option[SigInputs] =
		parse(h.value).toOption.map(_.values)

	def parse(s: String): ParseResult[`Signature-Input`] =
		for dict     <- util.fromParser(Rfc8941.Parser.sfDictionary,
				"Invalid `Signature-Input` header")(s.trim.nn)
			sigInputs <- SigInputs(dict).toRight(ParseFailure(
						 "Invalid `Signature-Input` header",
						 "valid SfDictionary elements are empty"))
		yield `Signature-Input`(sigInputs)

	given headerInstance: Header[`Signature-Input`, Header.Recurring] =
		Header.createRendered(
			ci"Signature-Input",
			_.values,
			parse
		)

	given sigInputsRenderer: Renderer[SigInputs] with
		import Rfc8941.Serialise.given
		def render(writer: Writer, sigs: SigInputs): writer.type =
			writer << sigs.si.map { (tk, si) => (tk, si.il) }.asInstanceOf[Rfc8941.SfDict].canon

	given headerSemigroupInstance: cats.Semigroup[`Signature-Input`] =
		(a, b) => `Signature-Input`(a.values.append(b.values))
end `Signature-Input`

object util:
	//copied from org.http4s.ParseResult.fromParser where it is private
	def fromParser[A](parser: Parser0[A], errorMessage: => String)(
		s: String
	): ParseResult[A] =
		try parser.parseAll(s).leftMap(e => ParseFailure(errorMessage, e.toString))
		catch { case p: ParseFailure => p.asLeft[A] }

