package run.cosy.http4s.headers

import cats.parse.Parser0
import org.http4s.{Header, ParseFailure, ParseResult}
import org.http4s.headers.{`Accept-Language`, `Cache-Control`}
import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.{Rfc8941, SigInput, SigInputs}
import org.typelevel.ci.*
import cats.syntax.all.*
import org.http4s.util.{Renderer, Writer}

import scala.collection.immutable.ListMap

/**
 * [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-the-signature-input-http-fi 4.1 The 'Signature-Input' HTTP header]]
 *
 */
object `Signature-Input`:
	def parse(s: String): ParseResult[`Signature-Input`] =
		for dict     <- fromParser(Rfc8941.Parser.sfDictionary,
				"Invalid `Signature-Input` header")(s)
			sigInputs <- SigInputs.build(dict).toRight(ParseFailure(
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

//copied from org.http4s.ParseResult.fromParser where it is private
def fromParser[A](parser: Parser0[A], errorMessage: => String)(
	s: String
): ParseResult[A] =
	try parser.parseAll(s).leftMap(e => ParseFailure(errorMessage, e.toString))
	catch { case p: ParseFailure => p.asLeft[A] }

case class `Signature-Input`(values: SigInputs)
