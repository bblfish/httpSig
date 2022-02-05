/*
 * Copyright 2021 Henry Story
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
