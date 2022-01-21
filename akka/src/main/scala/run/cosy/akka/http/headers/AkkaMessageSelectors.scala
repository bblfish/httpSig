package run.cosy.akka.http.headers

import akka.http.scaladsl.model.ContentTypes.NoContentType
import akka.http.scaladsl.model.headers.{Authorization, Date, ETag, Host, `Cache-Control`}
import akka.http.scaladsl.model.{HttpMessage, HttpRequest, HttpResponse}
import run.cosy.http.auth.{AttributeMissingException, SelectorException, UnableToCreateSigHeaderException}
import run.cosy.http.headers.{BasicMessageHeaderSelector, BasicMessageSelector, DictSelector, MessageSelector, MessageSelectors, Rfc8941}
import run.cosy.http.headers.Rfc8941.Serialise.given

import java.nio.charset.Charset
import java.util.Locale
import scala.util.{Failure, Success, Try}

class AkkaMessageSelectors(
	val securedConnection: Boolean,
	val defaultHost: akka.http.scaladsl.model.Uri.Host,
	val defaultPort: Int
) extends MessageSelectors:
	override type Message = HttpMessage
	override type Request = HttpRequest
	override type Response = HttpResponse

	override lazy val authorization: HeaderSelector[Request] =
		new TypedAkkaSelector[Request, Authorization] {
			def akkaCompanion = Authorization
		}
	override lazy val `cache-control`: HeaderSelector[Message] =
		new TypedAkkaSelector[Message, `Cache-Control`] {
			def akkaCompanion = `Cache-Control`
		}
	override lazy val date: HeaderSelector[Message] =
		new TypedAkkaSelector[Message, Date] {
			def akkaCompanion = Date
		}
	override lazy val etag: HeaderSelector[Response] =
		new TypedAkkaSelector[Response, ETag] {
			def akkaCompanion = ETag
		}
	override lazy val host: HeaderSelector[Request] =
		new TypedAkkaSelector[Request, Host] {
			def akkaCompanion = Host
		}
	override lazy val signature: DictSelector[Message] =
		new AkkaDictSelector[Message] {
			override val lowercaseName: String = "signature"
		}
	override lazy
	val `content-type`: HeaderSelector[Message] =
		new UntypedAkkaSelector[Message] {
			override val lowercaseName: String = "content-type"
			override def signingString(msg: Message, params: Rfc8941.Params): Try[String] =
				if params.isEmpty then
					msg.entity.contentType match
					case NoContentType => Failure(
						UnableToCreateSigHeaderException(s"""No header '$lowercaseName' in request"""))
					case ct => Success(s""""$lowercaseName": $ct""")
				else
					Failure(SelectorException(s"selector $lowercaseName does not take parameters. Received " + params))
		}
	override lazy
	val `content-length`: HeaderSelector[Message] =
		new UntypedAkkaSelector[Message] {
			override val lowercaseName: String = "content-length"
			override
			def signingString(msg: Message, params: Rfc8941.Params): Try[String] =
				if params.isEmpty then
					msg.entity.contentLengthOption match
					case None => Failure(
						UnableToCreateSigHeaderException(s"""No header '$lowercaseName' in request"""))
					case Some(cl) => Success(s""""$lowercaseName": $cl""")
				else
					Failure(SelectorException(s"selector $lowercaseName does not take parameters. Received " + params))
		}
	override lazy val digest: HeaderSelector[Message] =
		new UntypedAkkaSelector[Message] {
			override val lowercaseName: String = "digest"
		}
	override lazy val `@request-target`: BasicMessageSelector[Request] =
		new BasicMessageSelector[Request] {
			override val lowercaseName: String = "@request-target"
			override
			def specialForRequests: Boolean = true

			override protected
			def signingStringValue(req: Request): Try[String] =
				Try(req.uri.toString()) //tests needed with connnect
			//		req.method match
			//		case HttpMethods.CONNECT => Failure(UnableToCreateSigHeaderException("Akka cannot correctly prcess @request-target on CONNECT requests"))
			//		case _ => Success(s""""$lowercaseName": ${req.uri}""")
		}
	override lazy val `@method`: BasicMessageSelector[Request] =
		new BasicMessageSelector[Request]{
			override
			def lowercaseName: String = "@method"
			override def specialForRequests: Boolean = true

			override protected
			def signingStringValue(msg: Request): Try[String] =
				Success(msg.method.value) //already uppercase
	}
	override lazy val `@target-uri`: BasicMessageSelector[Request] =
		new BasicMessageSelector[Request] {
			override
			def lowercaseName: String = "@target-uri"
			override def specialForRequests: Boolean = true
			override protected
			def signingStringValue(msg: Request): Try[String] =
				Success(msg.effectiveUri(securedConnection,defaultHostHeader).toString())
		}

	override lazy val `@authority`: BasicMessageSelector[Request] =
		new BasicMessageSelector[Request] {
			override
			def lowercaseName: String = "@authority"
			override
			def specialForRequests: Boolean = true

			//todo: inefficient as it builds whole URI to extract only a small piece
			override protected
			def signingStringValue(msg: Request): Try[String] = Try(
				msg.effectiveUri(true, defaultHostHeader)
					.authority.toString().toLowerCase(Locale.US).nn //is locale correct?
			)
		}
	private lazy val defaultHostHeader = {
		val p = if defaultPort == 0 then 0
		else if securedConnection & defaultPort == 443 then 0
		else if defaultPort == 80 then 0
		else defaultPort
		Host(defaultHost,p)
	}

	override lazy val `@scheme`: BasicMessageSelector[Request] =
		new BasicMessageSelector[Request] {
			override
			def lowercaseName: String = "@scheme"
			override
			def specialForRequests: Boolean = true

			//todo: inefficient as it builds whole URI to extract only a small piece
			override protected
			def signingStringValue(msg: Request): Try[String] = Success(
				if securedConnection then "https" else "http"
			)
		}
	override lazy val `@path`: BasicMessageSelector[Request] =
		new BasicMessageSelector[Request] {
			override
			def lowercaseName: String = "@path"
			override
			def specialForRequests: Boolean = true

			override protected
			def signingStringValue(msg: Request): Try[String] = Try(
				msg.uri.path.toString()
			)
		}
	override lazy val `@status`: BasicMessageSelector[Response] =
		new BasicMessageSelector[Response] {
			override
			def lowercaseName: String = "@status"

			override protected
			def signingStringValue(msg: Response): Try[String] =
				Try("" + msg.status.intValue())
		}

	override lazy val `@query`: BasicMessageSelector[Request] =
		new BasicMessageSelector[HttpRequest] {
			val ASCII: Charset = Charset.forName("ASCII").nn
			override
			def lowercaseName: String = "@query"
			override
			def specialForRequests: Boolean = true

			override protected
			def signingStringValue(msg: Request): Try[String] =
				Try(msg.uri.queryString(ASCII).map("?" + _).getOrElse(""))
		}

	override lazy val `@query-params`: MessageSelector[Request] =
		new MessageSelector[Request] {
			val nameParam: Rfc8941.Token = Rfc8941.Token("name")
			val ASCII: Charset = Charset.forName("ASCII").nn
			override
			def lowercaseName: String = "@query-params"
			override
			def specialForRequests: Boolean = true

			override
			def signingString(msg: Request, params: Rfc8941.Params): Try[String] =
				params.toSeq match
				case Seq(nameParam -> (value: Rfc8941.SfString)) => Try {
					val queryStr = msg.uri.query().get(value.asciiStr).getOrElse("")
					s""""$lowercaseName";name=${value.canon}: $queryStr"""
				}
				case _ => Failure(
					SelectorException(
						s"selector $lowercaseName only takes ${nameParam.canon} parameters. Received " + params
					))
		}
	override lazy val `@request-response`:  MessageSelector[Request] =
		new MessageSelector[Request] {
			val keyParam: Rfc8941.Token = Rfc8941.Token("key")
			override
			def lowercaseName: String = "@request-response"
			override
			def specialForRequests: Boolean = true

			override
			def signingString(msg: Request, params: Rfc8941.Params): Try[String] =
				params.toSeq match
				case Seq(keyParam -> (value: Rfc8941.SfString)) => signingStringFor(msg, value)
				case _ => Failure(
					SelectorException(
						s"selector $lowercaseName only takes ${keyParam.canon} paramters. Received " + params
					))

			protected
			def signingStringFor(msg: Request, key: Rfc8941.SfString): Try[String] =
				for
					sigsDict <- signature.sfDictParse(msg)
					keyStr <- Try(Rfc8941.Token(key.asciiStr))
					signature <- sigsDict.get(keyStr)
						.toRight(AttributeMissingException(s"could not find signature '$keyStr'"))
						.toTry
				yield
					s""""$lowercaseName";key=${key.canon}: ${signature.canon}"""
		}
end AkkaMessageSelectors

