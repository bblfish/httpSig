package run.cosy.http4s.auth

import run.cosy.http.auth.{SignatureInputMatcher, SignatureMatcher}
import run.cosy.http4s.Http4sTp

object Http4sMessageSignature extends run.cosy.http.auth.MessageSignature[Http4sTp.type] {
	type H = Http4sTp.type
	override protected
	val Signature: SignatureMatcher[H] = run.cosy.http4s.headers.Signature
	override protected
	val `Signature-Input`: SignatureInputMatcher[H] = run.cosy.http4s.headers.`Signature-Input`
}
