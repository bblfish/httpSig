package run.cosy.akka.http

import akka.http.scaladsl.model
import akka.http.scaladsl.model.headers.RawHeader
import run.cosy.http.{Http, HttpOps}

object AkkaTp extends Http {
	override type Message = model.HttpMessage
	override type Request = model.HttpRequest
	override type Response = model.HttpResponse
	override type Header = model.HttpHeader

	given httpOps: HttpOps[AkkaTp.type] with {
		type A = AkkaTp.type

		extension(msg: Http.Message[A]) {
			def headers: Seq[Http.Header[A]] = msg.headers
		}

		extension[R<:Http.Message[A]](msg: R) {
			def addHeaders(headers: Seq[Http.Header[A]]): R =
			//don't know how to get rid of the asInstanceOf
				msg.withHeaders(msg.headers ++ headers).asInstanceOf[R]

			def addHeader(name: String, value: String): R =
				msg.withHeaders(msg.headers.prepended(RawHeader(name,value))).asInstanceOf[R]

			def removeHeader(name: String): R = msg.removeHeader(name).asInstanceOf[R]

			def headerValue(name: String): Option[String] =
				name.toLowerCase(java.util.Locale.ENGLISH) match
				case "content-type" =>
					if msg.entity.isKnownEmpty then None
					else Option(msg.entity.contentType.toString)
				case "content-length" =>
					if msg.entity.isKnownEmpty then Some("0")
					else msg.entity.contentLengthOption.map(_.toString)
				//todo: need to adapt for multiple headers
				case _ =>
					import scala.jdk.OptionConverters.*
					msg.getHeader(name).toScala.nn.map(_.value.nn)
		}
	}
}


