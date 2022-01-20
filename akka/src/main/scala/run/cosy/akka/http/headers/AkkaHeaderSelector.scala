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

end AkkaBasicHeaderSelector

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
end AkkaHeaderSelector



/** for all headers for which Akka HTTP does not provide a built-in parser */
trait UntypedAkkaSelector[HM <: HttpMessage]
	extends AkkaBasicHeaderSelector[HM] with AkkaHeaderSelector[HM]

/** todo: the UntypedAkkaSelector inheritance may not be long term */
trait AkkaDictSelector[HM <: HttpMessage]
	extends DictSelector[HM] with AkkaHeaderSelector[HM] {
	override def lowercaseHeaderName: String = lowercaseName
}

object authorization extends TypedAkkaSelector[HttpRequest, Authorization]:
	def akkaCompanion = Authorization
end authorization


object `cache-control` extends TypedAkkaSelector[HttpMessage,`Cache-Control`] :
	def akkaCompanion = `Cache-Control`

object date extends TypedAkkaSelector[HttpMessage,Date] :
	def akkaCompanion = Date

object etag extends TypedAkkaSelector[HttpResponse, ETag] :
	def akkaCompanion = ETag

object host extends TypedAkkaSelector[HttpRequest,Host]:
	def akkaCompanion = Host

object signature extends AkkaDictSelector[HttpMessage] {
	override val lowercaseName: String = "signature"

}



/** Content-Type is special in Akka, as it is associated with the entity body,
 * and is not modelled as a header */
object `content-type` extends UntypedAkkaSelector[HttpMessage] :
	override val lowercaseName: String = "content-type"
	override def signingString(msg: HttpMessage,  params: Rfc8941.Params): Try[String] =
		if params.isEmpty then
			msg.entity.contentType match
			case NoContentType => Failure(
				UnableToCreateSigHeaderException(s"""No header '$lowercaseName' in request"""))
			case ct => Success(s""""$lowercaseName": $ct""")
		else
			Failure(SelectorException(s"selector $lowercaseName does not take parameters. Received "+params))
end `content-type`


/**
 * Content-Length is special, and is associated with the entity body
 */
object `content-length` extends UntypedAkkaSelector[HttpMessage] :
	override val lowercaseName: String = "content-length"
	override
	def signingString(msg: HttpMessage,  params: Rfc8941.Params): Try[String] =
		if params.isEmpty then
			msg.entity.contentLengthOption match
			case None => Failure(
				UnableToCreateSigHeaderException(s"""No header '$lowercaseName' in request"""))
			case Some(cl) => Success(s""""$lowercaseName": $cl""")
		else
			Failure(SelectorException(s"selector $lowercaseName does not take parameters. Received "+params))
end `content-length`


// used in "Signing Http Messages"
object digest extends UntypedAkkaSelector[HttpMessage] :
	override val lowercaseName: String = "digest"

/**
 * `@request-target` refers to the full request target of the HTTP request message, as defined in "HTTP Semantics"
 * For HTTP 1.1, the component value is equivalent to the request target portion of the request line.
 * However, this value is more difficult to reliably construct in other versions of HTTP. Therefore,
 * it is NOT RECOMMENDED that this identifier be used when versions of HTTP other than 1.1 might be in use.
 *
 * @see https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-request-target
 */
object `@request-target` extends BasicMessageSelector[HttpRequest] :
	override val lowercaseName: String = "@request-target"
	override
	def specialForRequests: Boolean = true

	override protected
	def signingStringValue(req: HttpRequest): Try[String] =
		Try(req.uri.toString()) //tests needed with connnect
//		req.method match
//		case HttpMethods.CONNECT => Failure(UnableToCreateSigHeaderException("Akka cannot correctly prcess @request-target on CONNECT requests"))
//		case _ => Success(s""""$lowercaseName": ${req.uri}""")
end `@request-target`

/*
 * can also be used in a response, but requires the original request to
 * calculate
 * @see https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-method
 */
object `@method` extends BasicMessageSelector[HttpRequest]:
	override
	def lowercaseName: String = "@method"
	override def specialForRequests: Boolean = true

	override protected
	def signingStringValue(msg: HttpRequest): Try[String] =
		Success(msg.method.value)  //already uppercase
end `@method`

/**
 * in order to give the target URI we need to know if this is an https connection
 * and what the host header is if not specified in the request
 * note: can also be used in a response, but requires the original request to calculate
 *
 * @see https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-target-uri
 **/
case class `@target-uri`(securedConnection: Boolean, defaultHostHeader: Host)
	extends BasicMessageSelector[HttpRequest]:
	override
	def lowercaseName: String = "@target-uri"
	override def specialForRequests: Boolean = true

	override protected
	def signingStringValue(msg: HttpRequest): Try[String] =
		Success(msg.effectiveUri(securedConnection,defaultHostHeader).toString())
end `@target-uri`

/**
 * we may need to know the host name of the server to use as a default
 * note: can also be used in a response, but requires the original request to calculate
 *
 * @see https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-authority
 **/
case class `@authority`(defaultHostHeader: Host) extends BasicMessageSelector[HttpRequest]:
	override
	def lowercaseName: String = "@authority"
	override
	def specialForRequests: Boolean = true

	//todo: inefficient as it builds whole URI to extract only a small piece
	override protected
	def signingStringValue(msg: HttpRequest): Try[String] = Try(
		msg.effectiveUri(true,defaultHostHeader)
			.authority.toString().toLowerCase(Locale.US).nn //is locale correct?
	)
end `@authority`

/**
 * we need to know if the server is running on http or https
 *
 * @see https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-scheme
 **/
case class `@scheme`(secure: Boolean) extends BasicMessageSelector[HttpRequest]:
	override
	def lowercaseName: String = "@scheme"
	override
	def specialForRequests: Boolean = true

	//todo: inefficient as it builds whole URI to extract only a small piece
	override protected
	def signingStringValue(msg: HttpRequest): Try[String] = Success(
		if secure then "https" else "http"
	)
end `@scheme`

/**
 * @see https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-path
 **/
object `@path` extends BasicMessageSelector[HttpRequest]:
	override
	def lowercaseName: String = "@path"
	override
	def specialForRequests: Boolean = true

	override protected
	def signingStringValue(msg: HttpRequest): Try[String] = Try(
		msg.uri.path.toString()
	)
end `@path`

/**
 * @see https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-status-code
 */
object `@status` extends BasicMessageSelector[HttpResponse]:
	override
	def lowercaseName: String = "@status"

	override protected
	def signingStringValue(msg: HttpResponse): Try[String] =
		Try(""+msg.status.intValue())
end `@status`

/** @see https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-query */
object `@query` extends BasicMessageSelector[HttpRequest]:
	val ASCII: Charset = Charset.forName("ASCII").nn
	override
	def lowercaseName: String = "@query"
	override
	def specialForRequests: Boolean = true

	override protected
	def signingStringValue(msg: HttpRequest): Try[String] =
		Try(msg.uri.queryString(ASCII).map("?"+_).getOrElse(""))
end `@query`

/**
 * @see https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-query-parameters
 */
object `@query-params` extends MessageSelector[HttpRequest]:
	val nameParam: Rfc8941.Token = Rfc8941.Token("name")
	val ASCII: Charset = Charset.forName("ASCII").nn
	override
	def lowercaseName: String = "@query-params"
	override
	def specialForRequests: Boolean = true

	override
	def signingString(msg: HttpRequest, params: Rfc8941.Params): Try[String] =
		params.toSeq match
		case Seq(nameParam -> (value: Rfc8941.SfString)) => Try{
			val queryStr = msg.uri.query().get(value.asciiStr).getOrElse("")
			s""""$lowercaseName";name=${value.canon}: $queryStr"""
		}
		case _ => Failure(
			SelectorException(
				s"selector $lowercaseName only takes ${nameParam.canon} parameters. Received "+params
			))
end `@query-params`

object `@request-response` extends MessageSelector[HttpRequest]:
	val keyParam: Rfc8941.Token = Rfc8941.Token("key")
	override
	def lowercaseName: String = "@request-response"
	override
	def specialForRequests: Boolean = true

	override
	def signingString(msg: HttpRequest, params: Rfc8941.Params): Try[String] =
		params.toSeq match
		case Seq(keyParam -> (value: Rfc8941.SfString)) => signingStringFor(msg, value)
		case _ => Failure(
			SelectorException(
				s"selector $lowercaseName only takes ${keyParam.canon} paramters. Received "+params
			))

	protected
	def signingStringFor(msg: HttpRequest, key: Rfc8941.SfString): Try[String] =
		for
			sigsDict <- signature.sfDictParse(msg)
			keyStr <- Try(Rfc8941.Token(key.asciiStr))
			signature <- sigsDict.get(keyStr)
				.toRight(AttributeMissingException(s"could not find signature '$keyStr'"))
				.toTry
		yield
			s""""$lowercaseName";key=${key.canon}: ${signature.canon}"""
end `@request-response`


/** Note: @target-uri and @scheme can only be set by application code as a choice needs to be made */
given akkaRequestSelectorOps: SelectorOps[HttpRequest] =
	SelectorOps[HttpRequest](
		authorization,  host,
		`@request-target`, `@method`, `@path`, `@query`, `@query-params`,
		`@request-response`,
			//all the below are good for responses too
		digest, `content-length`, `content-type`, signature, date, `cache-control`
	)

//both: digest, `content-length`, `content-type`, signature, date, `cache-control`

given akkaResponseSelectorOps: SelectorOps[HttpResponse] =
	SelectorOps[HttpResponse](`@status`, etag,
		//all these are good for requests too
		digest, `content-length`, `content-type`, signature, date, `cache-control`
	)
