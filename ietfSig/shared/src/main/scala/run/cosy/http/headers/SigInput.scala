package run.cosy.http.headers

import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.{IList, Item, PItem, Parameterized, SfDict, SfInt, SfString, Token}

import java.time.Instant
import scala.collection.immutable.ListMap
import scala.concurrent.duration.FiniteDuration

/**
 * SigInputs are Maps from Signature Names to SigInput entries that this
 * server understands.
 *
 * @param value
 * @return
 */
final case class SigInputs private (val si: ListMap[Rfc8941.Token, SigInput]) extends AnyVal {
	def get(key: Rfc8941.Token): Option[SigInput] = si.get(key)
	def append(more: SigInputs): SigInputs = new SigInputs(si ++ more.si)
	def append(key: Rfc8941.Token, sigInput: SigInput): SigInputs = new SigInputs(si + (key -> sigInput))
}

object SigInputs:
	/* create a SigInput with a single element */
	def apply(name: Rfc8941.Token, siginput: SigInput) =
		new SigInputs(ListMap(name -> siginput))
	/**
	 * Filter out the inputs that this framework does not accept.
	 * */
	def filterValid(lm: SfDict): SigInputs = SigInputs(lm.collect {
		case (sigName, SigInput(sigInput)) => (sigName, sigInput)
	})


/**
 * A SigInput is a valid Signature-Input build on an Rfc8941 Internal List.
 * restricted to those this server can understand.
 * For example:
 * <pre>
 *  Signature-Input: sig1=("@method" "@target-uri" "host" "date" \
 *      "cache-control" "x-empty-header" "x-example");created=1618884475\
 *      ;keyid="test-key-rsa-pss"
 * </pre>
 * todo: An improved version would be more lenient, allowing opt-in refinements.
 *
 * As a Validated data structure, we can keep all the data present in a header for a particular
 * signature, as that is needed to verify the signature itself. Indeed extra attributes will be
 * vital to verify a signature, since the data from this header is part of the signature
 *
 * @param il
 */
final case class SigInput private(val il: IList) extends AnyVal {

	import Rfc8941.Serialise.given
	import SigInput.*

	//todo: verify that collecting only SfStrings is ok
	def headers: Seq[String] = il.items.collect { case PItem(SfString(str), _) => str }
	def headerItems: Seq[PItem[SfString]] = il.items.map(_.asInstanceOf[PItem[SfString]])

	def keyid: Option[Rfc8941.SfString] =
		il.params.get(keyidTk) match
			case Some(s: SfString) => Some(s)
			case _ => None

	def alg: Option[String] = il.params.get(algTk).collect { case SfString(str) => str }
	def nonce: Option[String] = il.params.get(nonceTk).collect { case SfString(str) => str }
	def isValidAt(i: FiniteDuration, shift: Long = 0): Boolean =
		created.map(_ - shift <= i.toSeconds).getOrElse(true) &&
			expires.map(_ + shift >= i.toSeconds).getOrElse(true)
	def created: Option[Long] = il.params.get(createdTk).collect { case SfInt(time) => time }
	def expires: Option[Long] = il.params.get(expiresTk).collect { case SfInt(time) => time }
	def canon: String = il.canon

}

object SigInput {
	/**
	 * registered metadata parameters for Signature specifications as per
	 * [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html#name-initial-contents-2 §6.2.2 of 07 spec]].
	 */
	val algTk = Token("alg")
	val createdTk = Token("created")
	val expiresTk = Token("expires")
	val keyidTk = Token("keyid")
	val nonceTk = Token("nonce")

	val registeredParams = Seq(algTk, createdTk, expiresTk, keyidTk, nonceTk)

	val Empty = ListMap.empty[Token, Item]

	/** parse the string to a SigInput */
	def apply(innerListStr: String): Option[SigInput] =
		Rfc8941.Parser.innerList.parseAll(innerListStr).toOption.flatMap(SigInput.apply)

	def apply(il: IList): Option[SigInput] =
		if valid(il) then Some(new SigInput(il)) else None

	//this is really functioning as a constructor in pattern matching contexts
	def unapply(pzd: Parameterized): Option[SigInput] =
		pzd match
		case il: IList => Some(new SigInput(il))
		case _ => None

	/**
	 * A Valid SigInput IList has an Internal list of parameterized SfStrings
	 * */
	def valid[H](il: IList): Boolean =
		def headersOk = il.items.forall { pit =>
			pit.item.isInstanceOf[SfString] //todo: one could check the parameters follow a pattern...
		}
		def paramsOk = il.params.forall {
			case (`keyidTk`, item: SfString) => true
			case (`createdTk`, _: SfInt) | (`expiresTk`, _: SfInt) => true
			case (`algTk`, _: SfString) | (`nonceTk`, _: SfString) => true
			// we are lenient on non-registered params
			case (attr, _) => !registeredParams.contains(attr)
		}
		headersOk && paramsOk
	end valid

}
