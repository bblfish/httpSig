package run.cosy.http.auth

import akka.http.scaladsl.util.FastFuture
import cats.Applicative
import run.cosy.http.headers.SelectorOps

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try


// selector needs to be filled in
object AkkaHttpMessageSignature extends run.cosy.http.auth.MessageSignature {
	import AkkaHttpMessageSignature.*
	override type HttpMessage = akka.http.scaladsl.model.HttpMessage
	override type HttpHeader = akka.http.scaladsl.model.HttpHeader
	override val Signature: SignatureMatcher{ type Header = HttpHeader } = run.cosy.http.headers.Signature
	override val `Signature-Input`: SignatureInputMatcher{ type Header = HttpHeader } = run.cosy.akka.http.headers.`Signature-Input`

	extension(msg: HttpMessage)(using selector: SelectorOps[HttpMessage]) {
		def addHeaders(headers: Seq[HttpHeader]): HttpMessage =
			msg.withHeaders(msg.headers ++ headers)
		def headers: Seq[HttpHeader] = msg.headers
	}

}