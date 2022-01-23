package run.cosy.http.auth

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.util.FastFuture
import cats.Applicative
import run.cosy.akka.http.AkkaTp
import run.cosy.http.Http.{Header, Message}
import run.cosy.http.headers.SelectorOps

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try


// selector needs to be filled in
object AkkaHttpMessageSignature extends run.cosy.http.auth.MessageSignature[AkkaTp.type] {
	type H = AkkaTp.type
	import AkkaHttpMessageSignature.*
//	override type Message = HttpMessage

	override protected
	val Signature: SignatureMatcher[AkkaTp.type] = run.cosy.http.headers.Signature
	override protected
	val `Signature-Input`: SignatureInputMatcher[AkkaTp.type] = run.cosy.akka.http.headers.`Signature-Input`


}