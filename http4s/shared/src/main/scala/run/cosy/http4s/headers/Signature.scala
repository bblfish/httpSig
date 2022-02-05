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

import org.http4s.{Header, ParseFailure, ParseResult}
import org.http4s.util.{Renderer, Writer}
import run.cosy.http.headers.{Rfc8941, SigInputs, Signatures}
import org.typelevel.ci.*
import run.cosy.http.Http
import run.cosy.http4s.Http4sTp

/** see [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#section-4.2 §4.2 The Signature HTTP Field]] */
case class Signature(signatures: Signatures)

object Signature extends run.cosy.http.auth.SignatureMatcher[Http4sTp.type]:
	def parse(s: String): ParseResult[Signature] =
		for dict      <- util.fromParser(Rfc8941.Parser.sfDictionary,
									"Invalid `Signature-Input` header")(s.trim.nn)
			 sigInputs <- Signatures(dict).toRight(ParseFailure(
				 "Invalid `Signature` header",
				 "valid SfDictionary elements are empty"))
		yield new Signature(sigInputs)

	override type SM = Header.Raw
	override def apply(sigs: Signatures): SM =
		Header.Raw(headerInstance.name,headerInstance.value(new Signature(sigs)))
	override def unapply(h: Http.Header[Http4sTp.type]): Option[Signatures] =
		parse(h.value).toOption.map(_.signatures)

	given headerInstance: Header[Signature, Header.Recurring] =
		Header.createRendered(
			ci"Signature",
			_.signatures,
			parse
		)

	given sigInputsRenderer: Renderer[Signatures] with
		import Rfc8941.Serialise.given
		def render(writer: Writer, sigs: Signatures): writer.type =
			writer << sigs.sigmap.asInstanceOf[Rfc8941.SfDict].canon

	given headerSemigroupInstance: cats.Semigroup[Signature] =
		(a, b) => new Signature(a.signatures.append(b.signatures))
end Signature

