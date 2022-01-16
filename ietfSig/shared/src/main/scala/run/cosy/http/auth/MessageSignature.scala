package run.cosy.http.auth

import cats.MonadError
import cats.effect.kernel.{Clock, MonadCancel}
import cats.syntax.all.*
import run.cosy.http.auth.Agent
import run.cosy.http.headers.Rfc8941.*
import run.cosy.http.headers.{SelectorOps, *}
import scodec.bits.ByteVector

import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.{PrivateKey, PublicKey}
import java.util.Locale
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


trait SignatureInputMatcher {
	type Header
	type H <: Header
	def unapply(h: Header): Option[SigInputs]
	def apply(name: Rfc8941.Token, sigInput: SigInput): H
}


trait SignatureMatcher {
	type Header
	type H <: Header
	def apply(sig: Signatures): H
	def unapply(h: Header): Option[Signatures]
}

object MessageSignature {
	import bobcats.Verifier.{Signature, SigningString}
	type SignatureVerifier[F[_], A] = (SigningString, Signature) => F[A]
	type Signer[F[_]] = SigningString => F[Signature]
}

/**
 * Adds extensions methods to sign HttpMessage-s - be they requests or responses.
 * */
trait MessageSignature {
	import bobcats.Verifier.{Signature, SigningString}
	type Message = Request | Response
	type Request
	type Response
	type HttpHeader
	import MessageSignature.*

	val urlStrRegex = "<(.*)>".r
	//todo: is this the right place
	val `Signature-Input`: SignatureInputMatcher {type Header = HttpHeader}
	val Signature: SignatureMatcher {type Header = HttpHeader}

	/** extensions needed to abstract across HTTP implementations */
	extension(msg: Message) {
		def headers: Seq[HttpHeader]
	}

	extension[R <: Message](msg: R) {
		def addHeaders(headers: Seq[HttpHeader]): R
	}

	/**
	 * [[https://tools.ietf.org/html/draft-ietf-httpbis-message-signatures-03#section-4.1 Message Signatures]]
	 */
	extension [R <: Message](msg: R)(using selectorDB: SelectorOps[R]) {

		/**
		 * Generate a function to create a new HttpRequest with the given Signature-Input header.
		 * Called by the client, building the message.
		 *
		 * @param name     the name of the signature
		 *                 todo: this should probably be done by the code, as it will depend on other
		 *                 signatures present in the header
		 * @param sigInput header describing the headers to sign as per "Signing Http Messages" RFC
		 * @return a function to create a new HttpRequest when given signing function wrapped in a F
		 *         the F can capture an IllegalArgumentException if the required headers are not present
		 *         in the request
		 */
		def withSigInput[F[_]](
			name: Rfc8941.Token, sigInput: SigInput, signerF: Signer[F]
		)(using meF: MonadError[F, Throwable]): F[R] =
			for toSignBytes <- meF.fromTry(signingString(sigInput))
				 (signature: ByteVector) <- signerF(toSignBytes)
			yield msg.addHeaders(Seq(
				`Signature-Input`(name, sigInput),
				Signature(Signatures(name, ArraySeq.unsafeWrapArray(signature.toArray))) //todo: align the types everywhere if poss
			))
		end withSigInput

		/**
		 * Generate the signature string, given the `signature-input` header.
		 * Note, that the headers to be signed, always contains the `signature-input` header itself.
		 *
		 * @param sigInput the sigInput header specifying the
		 * @return signing String for given Signature Input on this http message.
		 *         This string will either need to be verified with a public key against the
		 *         given one, or will need to be signed to be added to the Request.
		 *         In the latter case use the withSigInput method.
		 *         todo: it may be more correct if the result is a byte array, rather than a Unicode String.
		 */
		def signingString(sigInput: SigInput): Try[SigningString] =
			import Rfc8941.Serialise.{*, given}

			@tailrec
			def buildSigString(todo: Seq[Rfc8941.PItem[SfString]], onto: String): Try[String] =
				todo match
				case Seq() => Success(onto)
				case pih::tail =>
					if (pih == `@signature-params`.pitem) then
						val sigp = `@signature-params`.signingString(sigInput)
						Success(if onto == "" then sigp else onto + "\n" + sigp)
					else selectorDB.select(msg, pih) match
						case Success(hdr) => buildSigString(todo.tail, if onto == "" then hdr else onto + "\n" + hdr)
						case f => f
			end buildSigString

			buildSigString(sigInput.headerItems.appended(`@signature-params`.pitem), "")
				.flatMap(string => ByteVector.encodeAscii(string).toTry)
		end signingString

		/**
		 * get the signature data for a given signature name eg `sig1` from
		 * the headers.
		 *
		 * <pre>
		 * Signature-Input: sig1=("@authority" "content-type")\
		 * ;created=1618884475;keyid="test-key-rsa-pss"
		 * Signature: sig1=:KuhJjsOKCiISnKHh2rln5ZNIrkRvu...:
		 * </pre>
		 *
		 * @return a pair of SigInput Data, and the signature bytes
		 *         The SigInput Data tells us what the signature bytes are a signature of
		 *         and how to interpret them, i.e. what the headers are that were signed, where
		 *         the key is and what the signing algorithm used was
		 * */
		def getSignature(name: Rfc8941.Token): Option[(SigInput, Bytes)] =
			msg.headers.collectFirst {
				case `Signature-Input`(inputs) if inputs.get(name).isDefined =>
					inputs.get(name).get
			}.flatMap { siginput =>
				msg.headers.collectFirst {
					case Signature(sigs) if sigs.get(name).isDefined => (siginput, sigs.get(name).get)
				}
			}

		/**
		 * take a function `fetchKeyId` which takes a keyId argument and
		 * fetches in context F that key's data (public key, etc)
		 * allowing it to construct a signature verifier that can return an Agent ID
		 * of type `A`.
		 *
		 * signatureAuthN lifts such a function, into a function which given
		 * a Signature name, will use the information in the Http Message header
		 * to construct the signing string and verify the signature using the verifier
		 * returned by fetchKeyId.
		 *
		 * return an Authenticated Agent of type A
		 *
		 * todo: specify Throwable more precisely in MonadError?
		 * todo: the function `fetchKeyId` returns a F[SignatureVerifier] but
		 *    the signature specification may also set constraints on the type
		 *    of signature allowed. So we are missing here a way to check that the
		 *    constraints are satisfied. Check what constraint verifications are needed!
		 *
		 * @param fetchKeyId function taking a keyId and returning a Verifier function
		 *                   using the public key info from that keyId.
		 * @tparam F The context to fetch the key. Something like Future or IO.
		 * @tparam A The type of agent returned by the successful verification.
		 *           (placing this inside context F should allow the agent A to be
		 *           constructed only on verification of key material)
		 * @return a function which given a particular Http Signature name will return an Agent
		 *         of type A in the context F. Calling this function for a Http Signature name
		 *         (e.g. "sig1") will
		 *     1. find the signature input data (to construct a signature) and the signature bytes
		 *     1. verify the signature is still valid given the clock,
		 *     1. use `fetchKeyId` to construct the needed verifier
		 *     1. Verify the header, and if verified return the Agent object A
		 */
		def signatureAuthN[F[_] : Clock, A](
			fetchKeyId: Rfc8941.SfString => F[SignatureVerifier[F, A]]
		)(using
			ME: MonadError[F, Throwable]
		): HttpSig => F[A] = (httpSig) =>
			for
				(si: SigInput, sig: Bytes) <- ME.fromTry(msg.getSignature(httpSig.proofName)
					.toRight(InvalidSigException(
						s"could not find Signature-Input and Signature for Sig name '${httpSig.proofName}' ")
					).toTry)
				now <- summon[Clock[F]].realTime
				sigStr <- if si.isValidAt(now)
					then ME.fromTry(msg.signingString(si))
					else ME.fromTry(Failure(new Throwable(s"Signature no longer valid at $now"))) //todo exception tuning
				keyId <- ME.fromOption(si.keyid,KeyIdException("keyId missing or badly formatted"))
				signatureVerificiationFn <- fetchKeyId(keyId)
				agent <- signatureVerificiationFn(sigStr, ByteVector(sig.toArray)) //todo: unify bytes
			yield agent
	}

	/* needed for request-response dependencies */
	extension (response: Response)(using
		requestSelDB: SelectorOps[Request],
		responseSelDB: SelectorOps[Response]
	) {

	def signingString(sigInput: SigInput, request: Request): Try[SigningString] =
		import Rfc8941.Serialise.{*, given}

		@tailrec
		def buildSigString(todo: Seq[Rfc8941.PItem[SfString]], onto: String): Try[String] =
			todo match
			case Seq() => Success(onto)
			case pih::tail =>
				if (pih == `@signature-params`.pitem) then
					val sigp = `@signature-params`.signingString(sigInput)
					Success(if onto == "" then sigp else onto + "\n" + sigp)
				else
					val x = responseSelDB.get(pih.item) match
						case Success(selector) => selector.signingString(response, pih.params)
						case Failure(x) => requestSelDB.get(pih.item).flatMap{ selector =>
							if selector.specialForRequests then selector.signingString(request,pih.params)
							else Failure(x)
						}
					x match
					case Success(hdr) => buildSigString(todo.tail, if onto == "" then hdr else onto + "\n" + hdr)
					case f => f
		end buildSigString

		buildSigString(sigInput.headerItems.appended(`@signature-params`.pitem), "")
			.flatMap(string => ByteVector.encodeAscii(string).toTry)
	end signingString

	}

}