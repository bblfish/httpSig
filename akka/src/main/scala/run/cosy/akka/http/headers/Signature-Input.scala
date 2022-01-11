package run.cosy.akka.http.headers

import _root_.akka.http.scaladsl.model.headers.{CustomHeader, RawHeader}
import _root_.akka.http.scaladsl.model.{HttpHeader, ParsingException, Uri}
import cats.parse.Parser
import run.cosy.akka.http.headers.Encoding.UnicodeString
import run.cosy.akka.http.headers.{BetterCustomHeader, BetterCustomHeaderCompanion}
import run.cosy.http.auth.SignatureInputMatcher
import run.cosy.http.headers.*
import run.cosy.http.headers.Rfc8941.{IList, Item, PItem, Parameterized, Params, SfDict, SfInt, SfList, SfString, Token}

import java.security.{PrivateKey, PublicKey, Signature}
import java.time.Instant
import scala.collection.immutable
import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}


/**
 * [[https://tools.ietf.org/html/draft-ietf-httpbis-message-signatures-03#section-4.1 4.1 The 'Signature-Input' HTTP header]]
 * defined in "Signing HTTP Messages" HttpBis RFC.
 * Since version 03 signature algorithms have been re-introduced, but we only implement "hs2019" for simplicity.
 *
 * @param text
 */
final case class `Signature-Input`(sig: SigInputs)
	extends BetterCustomHeader[`Signature-Input`]:
	override val companion = `Signature-Input`
	override def renderInRequests = true
	override def renderInResponses = true
	override def value: String =
		import Rfc8941.Serialise.given
		sig.si.map { (tk, si) => (tk, si.il) }.asInstanceOf[Rfc8941.SfDict].canon
end `Signature-Input`


object `Signature-Input`
	extends BetterCustomHeaderCompanion[`Signature-Input`]
		with SignatureInputMatcher:
	type Header = HttpHeader
	type H = `Signature-Input`
	override val name = "Signature-Input"

	def apply[M](name: Rfc8941.Token, sigInput: SigInput)(using SelectorOps[M]): `Signature-Input` =
		`Signature-Input`(SigInputs(name, sigInput))
	def unapply[M](h: HttpHeader)(using SelectorOps[M]): Option[SigInputs] =
		h match
		case _: (RawHeader | CustomHeader) if h.lowercaseName == lowercaseName => parse(h.value).toOption
		case _ => None
	def parse[M](value: String)(using SelectorOps[M]): Try[SigInputs] =
		Rfc8941.Parser.sfDictionary.parseAll(value) match
		case Left(e) => Failure(HTTPHeaderParseException(e, value))
		case Right(lm) => Success(SigInputs.filterValid(lm))
end `Signature-Input`
