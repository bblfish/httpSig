package run.cosy.http4s.auth

import run.cosy.http.headers.{Rfc8941, SelectorOps, SigInput, `@signature-params`}

import java.nio.charset.StandardCharsets
import scala.util.{Success, Try}
import org.http4s.Message
import run.cosy.http.auth.SigningData
import run.cosy.http.headers.Rfc8941.SfString

import scala.annotation.tailrec

object MessageSignature:

	/**
	 * [[https://tools.ietf.org/html/draft-ietf-httpbis-message-signatures-03#section-4.1 Message Signatures]]
	 */
	extension[F[_]] (msg: Message[F])
		/**
		 * Generate a proces to create a new HttpRequest with the given Signature-Input header.
		 * Called by the client, building the message.
		 *
		 * @param sigInput header describing the headers to sign as per "Signing Http Messages" RFC
		 * @return a function to create a new HttpRequest when given signing data wrapped in a Try
		 *         the Try can capture an IllegalArgumentException if the required headers are not present
		 *         in the request
		 */
		def withSigInput(
			name: Rfc8941.Token, sigInput: SigInput
		)(using selector: SelectorOps[Message[F]]): Try[SigningData => Try[msg.Self]] =
			signingString(sigInput).map { sigString =>
				(sigData: SigningData) =>
					sigData.sign(sigString.getBytes(StandardCharsets.US_ASCII)).map { sigbytes =>
						import akka.http.scaladsl.model.{Uri, UriRendering}
						import UriRendering.given

						import scala.jdk.CollectionConverters.given
						msg.addHeaders(Seq(
							`Signature-Input`(name, sigInput),
							Signature(Signatures(name, collection.immutable.ArraySeq.unsafeWrapArray(sigbytes)))
						).asJava)
					}
			}
		end withSigInput

		/**
		 * Generate the signature string, given the `signature-input` header.
		 * This is to be called the server to verify a signature,
		 * but also by `withSigInput` to generate the signature.
		 * Note, that the headers to be signed, always contains the `signature-input` header itself.
		 *
		 * @param sigInput the sigInput header specifying the
		 * @return signing String for given Signature Input on this http requets.
		 *         This string will either need to be verified with a public key against the
		 *         given one, or will need to be signed to be added to the Request.
		 *         In the latter case use the withSigInput method.
		 *         todo: it may be more correct if the result is a byte array, rather than a Unicode String.
		 */
		def signingString(sigInput: SigInput)(using selector: SelectorOps[HttpMessage]): Try[String] =
			import Rfc8941.Serialise.{*, given}

			@tailrec
			def buildSigString(todo: Seq[Rfc8941.PItem[SfString]], onto: String): Try[String] =
				if todo.isEmpty then Success(onto)
				else
					val pih = todo.head
					if (pih == `@signature-params`.pitem) then
						val sigp = `@signature-params`.signingString(sigInput)
						Success(if onto == "" then sigp else onto + "\n" + sigp)
					else selector.select(msg, pih) match
					case Success(hdr) => buildSigString(todo.tail, if onto == "" then hdr else onto + "\n" + hdr)
					case f => f
				end if
			end buildSigString

			buildSigString(sigInput.headerItems.appended(`@signature-params`.pitem), "")
		end signingString


end MessageSignature


