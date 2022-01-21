package run.cosy.akka.http.headers

import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.ContentTypes.NoContentType
import akka.http.scaladsl.model.headers.*
import cats.data.NonEmptyList
import run.cosy.akka.http.headers.BetterCustomHeader
import run.cosy.http.auth.{AttributeMissingException, HTTPHeaderParseException, SelectorException, UnableToCreateSigHeaderException}
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http.headers.Rfc8941.{PItem, Params, Serialise, SfDict, SfString}
import run.cosy.http.headers.*

import java.nio.charset.Charset
import java.util.Locale
import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

/** Selectors that work on headers but take no parameters. */
trait AkkaBasicHeaderSelector[HM <: HttpMessage]
	extends BasicMessageSelector[HM] with AkkaHeaderSelector[HM]:

	override def lowercaseHeaderName: String = lowercaseName
	override protected
	def signingStringValue(msg: HM): Try[String] =
		filterHeaders(msg).map(SelectorOps.collate)

/**
 * Akka's builtin header parsers have specialised parsers for the
 * most well known headers.
 * */
trait TypedAkkaSelector[HM <: HttpMessage, HdrType <: HttpHeader : scala.reflect.ClassTag]
	extends AkkaBasicHeaderSelector[HM]:
	override val lowercaseName: String = akkaCompanion.lowercaseName
	def akkaCompanion: ModeledCompanion[HdrType]
	override
	def filterHeaders(msg: HM): Try[NonEmptyList[String]] =
		val headerValues: Seq[String] = msg.headers[HdrType].map(_.value())
		headerValues match
		case Seq() => Failure(UnableToCreateSigHeaderException(
			s"No headers »$lowercaseHeaderName« in http message"))
		case head::tail => Success(NonEmptyList(head,tail))


trait AkkaHeaderSelector[HM <: HttpMessage] extends HeaderSelector[HM]:
	override
	def filterHeaders(msg: HM): Try[NonEmptyList[String]] =
		val headersValues: Seq[String] = msg.headers
			.filter(_.lowercaseName() == lowercaseHeaderName)
			.map(_.value())
		headersValues match
		case Seq() => Failure(UnableToCreateSigHeaderException(
			s"No headers »$lowercaseHeaderName« in http message"))
		case head::tail => Success(NonEmptyList(head,tail))



/** for all headers for which Akka HTTP does not provide a built-in parser */
trait UntypedAkkaSelector[HM <: HttpMessage]
	extends AkkaBasicHeaderSelector[HM] with AkkaHeaderSelector[HM]

/** todo: the UntypedAkkaSelector inheritance may not be long term */
trait AkkaDictSelector[HM <: HttpMessage]
	extends DictSelector[HM] with AkkaHeaderSelector[HM]:
		override def lowercaseHeaderName: String = lowercaseName

