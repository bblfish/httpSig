package run.cosy.akka.http.headers

import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.ContentTypes.NoContentType
import akka.http.scaladsl.model.headers.*
import run.cosy.akka.http.headers.BetterCustomHeader
import run.cosy.http.auth.{SelectorException, UnableToCreateSigHeaderException}
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http.headers.Rfc8941.{PItem, Params, Serialise, SfDict, SfString}
import run.cosy.http.headers.*

import java.nio.charset.Charset
import java.util.Locale
import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}


/* helper functions */
object Selector:
	/** @return the collated cleaned up values of the headers with the same name */
	def collate(values: Seq[HttpHeader], name: String): Try[String] =
		values match
		case Nil => Failure(UnableToCreateSigHeaderException(s"""No header '$name' in request"""))
		case nonNil => Success(
			nonNil.map( //remove obsolete line folding and trim
				_.value.split('\n').map(_.trim).mkString(" ")
			).mkString(", ")
		)
end Selector

/** Selectors that work on headers but take no parameters. */
trait AkkaBasicHeaderSelector[HM <: HttpMessage]
	extends run.cosy.http.headers.MessageSelector[HM]:
	/**
	 * default for non-rfc 8941 headers.
	 * */
	override def signingString(msg: HM,  params: Rfc8941.Params): Try[String] =
		if params.isEmpty then
			Selector.collate(filterHeaders(msg),lowercaseName).map(headerName + _)
		else
			Failure(SelectorException(s"selector $lowercaseName does not take parameters. Received "+params))

	/** the lowercase name followed by colon and 1 space */
	final def headerName: String = s""""$lowercaseName": """
	/** Each Selector works on particular headers */
	protected def filterHeaders(msg: HM): Seq[HttpHeader]
end AkkaBasicHeaderSelector

/**
 * Akka's builtin header parsers have specialised parsers for the
 * most well known headers.
 * */
trait TypedAkkaSelector[HM <: HttpMessage, HdrType <: HttpHeader : scala.reflect.ClassTag]
	extends AkkaBasicHeaderSelector[HM]:
	override val lowercaseName: String = akkaCompanion.lowercaseName
	def akkaCompanion: ModeledCompanion[HdrType]
	override protected
	def filterHeaders(msg: HM): Seq[HttpHeader] = msg.headers[HdrType]

/** for all headers for which Akka HTTP does not provide a built-in parser */
trait UntypedAkkaSelector[HM <: HttpMessage] extends AkkaBasicHeaderSelector[HM]:
	override protected
	def filterHeaders(msg: HM): Seq[HttpHeader] =
		msg.headers.filter(_.lowercaseName() == lowercaseName)
end UntypedAkkaSelector

/** todo: the UntypedAkkaSelector inheritance may not be long term */
trait AkkaDictSelector[HM <: HttpMessage] extends DictSelector[HM]:
	override
	def signingStringFor(msg: HM): Try[String] = sfDictParse(msg).map{dict =>
		headerName + dict.canon
	}
	override
	def signingStringFor(msg: HM, key: Rfc8941.SfString): Try[String] =
		for
			dict <- sfDictParse(msg)
			value <- dict.get(Rfc8941.Token(key.asciiStr)).toRight(UnableToCreateSigHeaderException(
				s"could not find $key in header [$dict]")
			).toTry
		yield headerName(key) + value.canon

	def sfDictParse(msg: HM): Try[SfDict] =
		for
			headers <- filterHeaders(msg) match
				case Seq() => Failure(
					UnableToCreateSigHeaderException(s"No headers »$lowercaseName« in http message"))
				case nonempty =>
					Success(nonempty)
			str <- Selector.collate(headers,lowercaseName)
			sfDict <- parse(str)
		yield
			sfDict


	protected //todo this is a duplicate fnct
	def filterHeaders(msg: HM): Seq[HttpHeader] =
		msg.headers.filter(_.lowercaseName() == lowercaseName)

	//note: the reason the token must be surrounded by quotes `"` is because a Token may end with `:`
	final def headerName(key: Rfc8941.SfString): String = s""""$lowercaseName";key=${key.canon}: """
	final def headerName: String = s""""$lowercaseName": """

	def parse(hdrStr: String): Try[SfDict] =
		Rfc8941.Parser.sfDictionary.parseAll(hdrStr) match
		case Left(err) => Failure(
			UnableToCreateSigHeaderException(
				s"parsing problem on header [$hdrStr] caused by $err"))
		case Right(sfDict) => Success(sfDict)
end AkkaDictSelector

object authorization extends TypedAkkaSelector[HttpRequest, Authorization]:
	def akkaCompanion = Authorization
end authorization


object `cache-control` extends TypedAkkaSelector[HttpMessage,`Cache-Control`] :
	def akkaCompanion = `Cache-Control`

object date extends TypedAkkaSelector[HttpMessage,Date] :
	def akkaCompanion = Date

object etag extends TypedAkkaSelector[HttpResponse, ETag] :
	def akkaCompanion = ETag

object host extends TypedAkkaSelector[HttpRequest,Host] :
	def akkaCompanion = Host

object signature extends AkkaDictSelector[HttpMessage] :
	override val lowercaseName: String = "signature"


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
	override protected
	def signingStringValue(req: HttpRequest): Try[String] =
		Try(req.uri.toRelative.toString()) //tests needed with connnect
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

	override protected
	def signingStringValue(msg: HttpRequest): Try[String] = Try(
		msg.uri.path.toString()
	)
end `@path`

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

	override protected
	def signingStringValue(msg: HttpRequest): Try[String] =
		Try(msg.uri.queryString(ASCII).map("?"+_).getOrElse(""))
end `@query`

object `@query-params` extends MessageSelector[HttpRequest]:
	val nameParam: Rfc8941.Token = Rfc8941.Token("name")
	val ASCII: Charset = Charset.forName("ASCII").nn
	override
	def lowercaseName: String = "@query-params"

	override
	def signingString(msg: HttpRequest, params: Rfc8941.Params): Try[String] =
		params.toSeq match
		case Seq(nameParam -> (value: Rfc8941.SfString)) => signingStringFor(msg, value)
		case _ => Failure(
			SelectorException(
				s"selector $lowercaseName only takes ${nameParam.canon} paramters. Received "+params
			))

	protected
	def signingStringFor(msg: HttpRequest, key: Rfc8941.SfString): Try[String] =
		Try{
			val queryStr = msg.uri.query().get(key.asciiStr).getOrElse("")
			s""""$lowercaseName";name=${key.canon}: $queryStr"""
		}
end `@query-params`


/** Note: @target-uri and @scheme can only be set by application code as a choice needs to be made */
given akkaRequestSelectorOps: SelectorOps[HttpRequest] =
	SelectorOps[HttpRequest](
		authorization,  host,
		`@request-target`, `@method`, `@path`, `@query`, `@query-params`,
		//all the below are good for responses too
		digest, `content-length`, `content-type`, signature, date, `cache-control`
	)

//both: digest, `content-length`, `content-type`, signature, date, `cache-control`

given akkaResponseSelectorOps: SelectorOps[HttpResponse] =
	SelectorOps[HttpResponse](`@status`, etag,
		//all these are good for requests too
		digest, `content-length`, `content-type`, signature, date, `cache-control`
	)
