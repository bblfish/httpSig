package run.cosy.akka.http.headers

import akka.http.scaladsl.model.headers.*
import akka.http.scaladsl.model.*
import run.cosy.akka.http.headers.BetterCustomHeader
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http.headers.Rfc8941.{PItem, Params, Serialise, SfDict, SfString}
import run.cosy.http.headers.{DictSelector, ListSelector, Rfc8941, SelectorOps, UnableToCreateSigHeaderException}

import java.util.Locale
import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

trait AkkaHeaderSelector extends run.cosy.http.headers.HeaderSelector[HttpMessage] :
	/**
	 * default for non-rfc 8941 headers.
	 * */
	override def signingString(msg: HttpMessage): Try[String] =
		collate(filterHeaders(msg)).map(headerName + _)
	/** @return the collated cleaned up values of the headers with the same name */
	protected final def collate(values: Seq[HttpHeader]): Try[String] =
		values match
		case Nil => Failure(UnableToCreateSigHeaderException(s"""No header '$lowercaseName' in request"""))
		case nonNil => Success(
			nonNil.map( //remove obsolete line folding and trim
				_.value.split('\n').map(_.trim).mkString(" ")
			).mkString(", ")
		)
	/** the lowercase name followed by colon and 1 space */
	final def headerName: String = s""""$lowercaseName": """
	/** override this method. */
	protected def filterHeaders(msg: HttpMessage): Seq[HttpHeader]

/**
 * Akka's builtin header parsers have specialised parsers for the
 * most well known headers.
 * */
trait TypedAkkaSelector[HdrType <: HttpHeader : scala.reflect.ClassTag] extends AkkaHeaderSelector :
	override val lowercaseName: String = akkaCompanion.lowercaseName
	def akkaCompanion: ModeledCompanion[HdrType]
	protected def filterHeaders(msg: HttpMessage): Seq[HttpHeader] = msg.headers[HdrType]

/** for all headers for which Akka HTTP does not provide a built-in parser */
trait UntypedAkkaSelector extends AkkaHeaderSelector :
	protected def filterHeaders(msg: HttpMessage): Seq[HttpHeader] =
		msg.headers.filter(_.lowercaseName() == lowercaseName)

/** todo: the UntypedAkkaSelector inheritance may not be long term */
trait AkkaDictSelector extends DictSelector[HttpMessage] with UntypedAkkaSelector :
	override def signingString(msg: HttpMessage): Try[String] = sfDictParse(msg).map(headerName + _.canon)
	def sfDictParse(msg: HttpMessage): Try[SfDict] =
		for {
			headers <- filterHeaders(msg) match {
			case Seq() => Failure(UnableToCreateSigHeaderException(s"No headers »$lowercaseName« in request"))
			case nonempty => Success(nonempty)
			}
			str <- collate(headers)
			sfDict <- parse(str)
		} yield sfDict
	def parse(hdrStr: String): Try[SfDict] =
		Rfc8941.Parser.sfDictionary.parseAll(hdrStr) match
		case Left(err) => Failure(
			UnableToCreateSigHeaderException(
				s"parsing problem on header [$hdrStr] caused by $err"))
		case Right(sfDict) => Success(sfDict)
	override def signingString(msg: HttpMessage, key: Rfc8941.Token): Try[String] =
		for {
			dict <- sfDictParse(msg)
			value <- dict.get(key).toRight(UnableToCreateSigHeaderException(
				s"could not find $key in header [$dict]")
			).toTry
		} yield headerName + value.canon

/**
 * Wait to implement:
 * It feels like [[https://www.rfc-editor.org/rfc/rfc8941#name-lists RFC8941 §3.1 Lists]] may be
 * a better fit for what is intended here. Inner Lists on headers require there to be only 1 header of a given
 * name as multiple lists cannot be split. Or perhaps the same process would work there too?
 * Let us wait to see how the spec evolves.
 */
trait AkkaListSelector extends ListSelector[HttpHeader] with UntypedAkkaSelector :
	override def signingString(msg: HttpHeader, prefix: Rfc8941.SfInt): Try[String] = ???
//	override def signingString(msg: HttpHeader): Try[String] = ???

object authorization extends TypedAkkaSelector[Authorization] :
	def akkaCompanion = Authorization

object `cache-control` extends TypedAkkaSelector[`Cache-Control`] :
	def akkaCompanion = `Cache-Control`

object date extends TypedAkkaSelector[Date] :
	def akkaCompanion = Date

object etag extends TypedAkkaSelector[ETag] :
	def akkaCompanion = ETag

object host extends TypedAkkaSelector[Host] :
	def akkaCompanion = Host

object signature extends AkkaDictSelector :
	override val lowercaseName: String = "signature"

/** Content-Type is special in Akka, as it is associated with the entity body,
 * and is not modelled as a header */
object `content-type` extends UntypedAkkaSelector :
	override val lowercaseName: String = "content-type"
	override def signingString(msg: HttpMessage): Try[String] =
		import akka.http.scaladsl.model.ContentTypes.NoContentType
		msg.entity.contentType match
		case NoContentType => Failure(
			UnableToCreateSigHeaderException(s"""No header '$lowercaseName' in request"""))
		case ct => Success(s""""$lowercaseName": $ct""")

/**
 * Content-Length is special, and is associated with the entity body
 */
object `content-length` extends UntypedAkkaSelector :
	override val lowercaseName: String = "content-length"
	override def signingString(msg: HttpMessage): Try[String] =
		msg.entity.contentLengthOption match
		case None => Failure(
			UnableToCreateSigHeaderException(s"""No header '$lowercaseName' in request"""))
		case Some(cl) => Success(s""""$lowercaseName": $cl""")


object `digest` extends UntypedAkkaSelector :
	override val lowercaseName: String = "digest"

/**
 * `@request-target` refers to the full request target of the HTTP request message, as defined in "HTTP Semantics"
 * For HTTP 1.1, the component value is equivalent to the request target portion of the request line.
 * However, this value is more difficult to reliably construct in other versions of HTTP. Therefore,
 * it is NOT RECOMMENDED that this identifier be used when versions of HTTP other than 1.1 might be in use.
 * @see https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-request-target
 */
object `@request-target` extends AkkaHeaderSelector :
	override val lowercaseName: String = "@request-target"
	override def signingString(msg: HttpMessage): Try[String] =
		msg match
		case req: HttpRequest =>
			req.method match
			case HttpMethods.CONNECT => Failure(UnableToCreateSigHeaderException("Akka cannot correctly prcess @request-target on CONNECT requests"))
			case _ => Success(s""""$lowercaseName$": ${req.uri}""")
		case _: HttpResponse => Failure(
			UnableToCreateSigHeaderException("cannot build @request-target for response message"))
	/** this is perhaps the only object where this mathod is not used */
	override protected def filterHeaders(msg: HttpMessage): Seq[HttpHeader] = ???

given akkaSelectorOps: SelectorOps[HttpMessage] =
	SelectorOps(authorization, `cache-control`, `content-type`, `content-length`,
		date, `digest`, etag, host, `@request-target`)
