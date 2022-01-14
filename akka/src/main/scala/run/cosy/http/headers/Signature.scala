package run.cosy.http.headers

import akka.http.scaladsl.model.headers.{CustomHeader, RawHeader}
import akka.http.scaladsl.model.{HttpHeader, ParsingException}
import run.cosy.akka.http.headers.{BetterCustomHeader, BetterCustomHeaderCompanion}
import run.cosy.http.auth.{HTTPHeaderParseException, SignatureMatcher}
import run.cosy.http.headers
import run.cosy.http.headers.Rfc8941.{Bytes, IList, PItem, SfDict}

import scala.collection.immutable
import scala.collection.immutable.{ArraySeq, ListMap}
import scala.util.{Failure, Success, Try}
import run.cosy.http.headers.Signatures

/**
 * [[https://tools.ietf.org/html/draft-ietf-httpbis-message-signatures-03#section-4.2 §4.2 The Signature HTTP header]] defined in "Signing HTTP Messages" HttpBis RFC.
 *
 * @param text
 */
final case class Signature(sig: Signatures)
	extends BetterCustomHeader[Signature]:
	override val companion = Signature
	override def renderInRequests = true
	override def renderInResponses = true
	override def value: String =
		import Rfc8941.Serialise.given
		sig.sigmap.asInstanceOf[Rfc8941.SfDict].canon


object Signature
	extends BetterCustomHeaderCompanion[Signature]
		with SignatureMatcher:
	override type Header = run.cosy.http.auth.AkkaHttpMessageSignature.HttpHeader
	override type H = run.cosy.http.headers.Signature
	override val name = "Signature"
	def apply(sig: Signatures): H = new run.cosy.http.headers.Signature(sig)
	def unapply(h:  Header): Option[Signatures] =
		h match
		case _: (RawHeader | CustomHeader) if h.lowercaseName == lowercaseName => parse(h.value).toOption
		case _ => None
	def parse(value: String): Try[Signatures] =
		Rfc8941.Parser.sfDictionary.parseAll(value) match
		case Left(e) => Failure(HTTPHeaderParseException(e, value))
		case Right(lm) => Success(Signatures(lm))
end Signature




