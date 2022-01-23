package run.cosy.akka.http

import akka.http.scaladsl.model
import run.cosy.http.{Http, HttpOps}

object AkkaTp extends Http {
	override type Message = model.HttpMessage
	override type Request = model.HttpRequest
	override type Response = model.HttpResponse
	override type Header = model.HttpHeader

	given httpOps: HttpOps[AkkaTp.type] with {
		type A = AkkaTp.type

		extension[R<:Http.Message[A]](msg: R) {
			def addHeaders(headers: Seq[Http.Header[A]]): R =
			//don't know how to get rid of the asInstanceOf
				msg.withHeaders(msg.headers ++ headers).asInstanceOf[R]

			def removeHeader(name: String): R = msg.removeHeader(name).asInstanceOf[R]
		}

		extension(msg: Http.Message[A]) {
			def headers: Seq[Http.Header[A]] = msg.headers
		}
	}
}


