package run.cosy.http4s.headers

import org.http4s.Uri
import run.cosy.http.Http
import run.cosy.http.Http.{Message, Request, Response}
import run.cosy.http.headers.{DictSelector, HeaderSelector, MessageSelector, MessageSelectors}
import run.cosy.http4s.Http4sTp

object Http4sMessageSelector:
	type H = Http4sTp.type
	def request(name: String): MessageSelector[Request[H]] =
		new BasicHeaderSelector[Request[H]] {
			override val lowercaseName: String = name
		}
	def response(name: String): MessageSelector[Response[H]] =
		new BasicHeaderSelector[Response[H]] {
			override val lowercaseName: String = name
		}

	def hdrMessage(name: String): MessageSelector[Message[H]] =
		new BasicHeaderSelector[Message[H]] {
			override val lowercaseName: String = name
		}

	def dictMsg(name: String): DictSelector[Message[H]] =
		new Http4sDictSelector[Message[H]] {
			override def lowercaseName: String = name
		}
end Http4sMessageSelector


class Http4sMessageSelectors(
	val securedConnection: Boolean,
	val defaultHost: Uri.Authority,
	val defaultPort: Int
) extends MessageSelectors[Http4sMessageSelector.H]:
	import Http4sMessageSelector.*

	override lazy 
	val authorization: MessageSelector[Request[H]] = run.cosy.http4s.headers.authorization
	override lazy 
	val date: MessageSelector[Message[H]] = run.cosy.http4s.headers.date
	override lazy 
	val etag: MessageSelector[Response[H]] = run.cosy.http4s.headers.etag
	override lazy 
	val host: MessageSelector[Request[H]] = run.cosy.http4s.headers.host
	override lazy
	val signature: DictSelector[Message[H]] = dictMsg("signature")
	override lazy 
	val digest: MessageSelector[Message[H]] = run.cosy.http4s.headers.digest
	override lazy
	val forwarded: MessageSelector[Message[H]] = hdrMessage("forwarded")
	override lazy
	val `cache-control`: MessageSelector[Message[H]] = hdrMessage("cache-control")
	override lazy
	val `client-cert`: MessageSelector[Message[H]] = hdrMessage("client-cert")
	override lazy 
	val `content-length`: MessageSelector[Message[H]] = run.cosy.http4s.headers.`content-length`
	override lazy 
	val `content-type`: MessageSelector[Message[H]] = run.cosy.http4s.headers.`content-type`

	override lazy val `@request-target`: MessageSelector[Request[H]] =
		run.cosy.http4s.headers.`@request-target`
	override lazy val `@method`: MessageSelector[Request[H]] =
		run.cosy.http4s.headers.`@method`
	override lazy val `@target-uri`: MessageSelector[Request[H]] =
		run.cosy.http4s.headers.`@target-uri`(securedConnection, defaultHost)
	override lazy val `@authority`: MessageSelector[Request[H]] =
		run.cosy.http4s.headers.`@authority`(defaultHost)
	override lazy val `@scheme`: MessageSelector[Request[H]] =
		run.cosy.http4s.headers.`@scheme`(securedConnection)
	override lazy val `@path`: MessageSelector[Request[H]] =
		run.cosy.http4s.headers.`@path`
	override lazy val `@status`: MessageSelector[Response[H]] =
		run.cosy.http4s.headers.`@status`
	override lazy val `@query`: MessageSelector[Request[H]] =
		run.cosy.http4s.headers.`@query`
	override lazy val `@query-params`: MessageSelector[Request[H]] =
		run.cosy.http4s.headers.`@query-params`
	override lazy val `@request-response`:  MessageSelector[Request[H]] =
		run.cosy.http4s.headers.`@request-response`


end Http4sMessageSelectors
