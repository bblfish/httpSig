package run.cosy.http.headers

import run.cosy.http.headers.Rfc8941.{Bytes, PItem, SfDict}

import scala.collection.immutable.{ArraySeq, ListMap}

/**
 * A Signature is an SfDict, refined to contain only PItems of Arrays of Bytees.
 * We want to keep potential attributes as they could be useful. */
final class Signatures private(val sigmap: ListMap[Rfc8941.Token, PItem[Bytes]]) extends AnyVal :

	def get(signame: Rfc8941.Token): Option[Bytes] = sigmap.get(signame).map(_.item)

	//add the signature to the list.
	def add(signame: Rfc8941.Token, signbytes: Bytes) =
		new Signatures(sigmap.updated(signame, PItem(signbytes)))
end Signatures

object Signatures:
	def apply(lm: SfDict): Signatures = new Signatures(filterValid(lm))

	def filterValid(lm: SfDict): ListMap[Rfc8941.Token, PItem[Bytes]] = lm.collect {
		case (sigName, pi@PItem(bytes: ArraySeq[?], attr)) =>
			// if it is an ArraySeq, it is a ByteArraySequence!
			(sigName, pi.asInstanceOf[PItem[Bytes]])
	}

	def apply(signame: Rfc8941.Token, signbytes: Bytes): Signatures =
		new Signatures(ListMap(signame -> PItem(signbytes)))
end Signatures
