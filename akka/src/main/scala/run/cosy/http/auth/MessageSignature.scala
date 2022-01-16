package run.cosy.http.auth

import akka.http.scaladsl.model.{HttpRequest,HttpResponse}
import akka.http.scaladsl.util.FastFuture
import cats.Applicative
import run.cosy.http.headers.SelectorOps

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try


// selector needs to be filled in
object AkkaHttpMessageSignature extends run.cosy.http.auth.MessageSignature {
	import AkkaHttpMessageSignature.*
//	override type Message = HttpMessage
	override type Request = HttpRequest
	override type Response = HttpResponse
	override type HttpHeader = akka.http.scaladsl.model.HttpHeader
	override val Signature: SignatureMatcher{ type Header = HttpHeader } = run.cosy.http.headers.Signature
	override val `Signature-Input`: SignatureInputMatcher{ type Header = HttpHeader } = run.cosy.akka.http.headers.`Signature-Input`

	extension[R <: Message](msg: R) {
		def addHeaders(headers: Seq[HttpHeader]): R =
			//don't know how to get rid of the asInstanceOf
			msg.withHeaders(msg.headers ++ headers).asInstanceOf[R]
	}

	extension(msg: Message) {
		def headers: Seq[HttpHeader] = msg.headers
	}

}