package run.cosy.http.headers

import run.cosy.http.headers.Rfc8941.{Bytes, PItem, SfDict}

import scala.None
import scala.collection.immutable.{ArraySeq, ListMap}

/**
 * A Signature is an SfDict, refined to contain only PItems of Arrays of Bytes.
 * We want to keep potential attributes as they could be useful. */
final case class Signatures private(sigmap: ListMap[Rfc8941.Token, PItem[Bytes]]):
	def get(signame: Rfc8941.Token): Option[Bytes] = sigmap.get(signame).map(_.item)
	def append(other: Signatures): Signatures = new Signatures(sigmap ++ other.sigmap)
	//add the signature to the list.
	def add(signame: Rfc8941.Token, signbytes: Bytes) =
		new Signatures(sigmap.updated(signame, PItem(signbytes)))
end Signatures

object Signatures:
	def apply(lm: SfDict): Option[Signatures] =
		val cleaned = filter(lm)
		if cleaned.isEmpty then None
		else Some(new Signatures(cleaned))

	//filter out any that are obviously wrong
	def filter(lm: SfDict): ListMap[Rfc8941.Token, PItem[Bytes]] =
		lm.collect {
			case (sigName, pi@PItem(bytes: ArraySeq[?], attr)) =>
				// if it is an ArraySeq, it is a ByteArraySequence!
				(sigName, pi.asInstanceOf[PItem[Bytes]])
		}

	def apply(signame: Rfc8941.Token, signbytes: Bytes): Signatures =
		new Signatures(ListMap(signame -> PItem(signbytes)))
end Signatures
