package run.cosy.http

import run.cosy.http.Http.Header

trait Http:
	type Message
	type Request <: Message
	type Response <: Message
	type Header
end Http

trait HttpOps[H<:Http]:
	import Http.*
	/** extensions needed to abstract across HTTP implementations for our purposes */
	extension(msg: Message[H]) {
		def headers: Seq[Header[H]]
	}

	extension[R <: Message[H]](msg: R) {
		def addHeaders(headers: Seq[Header[H]]): R
		def addHeader(name: String, value: String): R
		//this is used in tests
		def removeHeader(name: String): R
		//used in tests: return the Optional Value
		def headerValue(name: String): Option[String]
	}
end HttpOps


object Http:
	type Message[H <: Http] = Request[H] | Response[H]

	type Request[H <: Http] = H match
		case GetRequest[req] => req

	type Response[H <: Http] = H match
		case GetResponse[res] => res

	type Header[H <: Http] = H match
		case GetHeader[res] => res

	private type GetMessage[M] = Http { type Message = M }
	private type GetRequest[R] = Http { type Request = R }
	private type GetResponse[R] = Http { type Response = R }
	private type GetHeader[R] = Http { type Header = R }
end Http
