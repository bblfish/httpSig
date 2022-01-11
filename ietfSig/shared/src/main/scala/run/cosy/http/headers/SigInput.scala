package run.cosy.http.headers

import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.{IList, Item, PItem, Parameterized, SfDict, SfInt, SfString, Token}

import java.time.Instant
import scala.collection.immutable.ListMap

/**
 * SigInputs are Maps from Signature Names to SigInput entries that this
 * server understands.
 *
 * @param value
 * @return
 */
final case class SigInputs private(val si: ListMap[Rfc8941.Token, SigInput]) extends AnyVal {
	def get(key: Rfc8941.Token): Option[SigInput] = si.get(key)
	def append(more: SigInputs): SigInputs = new SigInputs(si ++ more.si)
	def append(key: Rfc8941.Token, sigInput: SigInput): SigInputs = new SigInputs(si + (key -> sigInput))
}

object SigInputs:
	/* create a SigInput with a single element */
	def apply[M](name: Rfc8941.Token, siginput: SigInput)(using SelectorOps[M]) =
		new SigInputs(ListMap(name -> siginput))

	/**
	 * Filter out the inputs that this framework does not accept.
	 * */
	def filterValid[M](lm: SfDict)(using SelectorOps[M]): SigInputs = SigInputs(lm.collect {
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

	def keyid: Rfc8941.SfString = il.params.get(keyidTk).get.asInstanceOf[Rfc8941.SfString]

	def alg: Option[String] = il.params.get(algTk).collect { case SfString(str) => str }
	def nonce: Option[String] = il.params.get(nonceTk).collect { case SfString(str) => str }
	def isValidAt(i: Instant, shift: Long = 0): Boolean =
		created.map(_ - shift <= i.getEpochSecond).getOrElse(true) &&
			expires.map(_ + shift >= i.getEpochSecond).getOrElse(true)
	def created: Option[Long] = il.params.get(createdTk).collect { case SfInt(time) => time }
	def expires: Option[Long] = il.params.get(expiresTk).collect { case SfInt(time) => time }
	def canon: String = il.canon

}

object SigInput {
	/** registered metadata parameters as per [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-04.html#section-5.2.2 §5.2.2]].
	 * To avoid them being confused with pattern matches variables enclose in ` `. */
	val algTk = Token("alg")
	val createdTk = Token("created")
	val expiresTk = Token("expires")
	val keyidTk = Token("keyid")
	val nonceTk = Token("nonce")

	val registeredParams = Seq(algTk, createdTk, expiresTk, keyidTk, nonceTk)

	val Empty = ListMap.empty[Token, Item]

	def apply[M](innerListStr: String)(using SelectorOps[M]): Option[SigInput] =
		Rfc8941.Parser.innerList.parseAll(innerListStr).toOption.flatMap(il => SigInput[M](il))

	def apply[M](il: IList)(using SelectorOps[M]): Option[SigInput] =
		if valid(il) then Some(new SigInput(il)) else None

	//this is really functioning as a constructor in pattern matching contexts
	def unapply[M](pzd: Parameterized)(using SelectorOps[M]): Option[SigInput] =
		pzd match
		case il: IList if valid(il) => Some(new SigInput(il))
		case _ => None


	/**
	 * A Valid SigInput IList has an Internal list of parameterized SfStrings which must contain
	 * a keyid attribute.
	 * */
	def valid[H](il: IList)(using o: SelectorOps[H]): Boolean =
		def headersOk = il.items.forall { pit =>
			pit.item match
			case str: SfString => o.valid(pit.asInstanceOf[PItem[SfString]]) // todo: remove asInstanceOf?
			case _ => false
		}
		def paramsOk = il.params.forall {
			case (`keyidTk`, item: SfString) => true
			case (`createdTk`, _: SfInt) | (`expiresTk`, _: SfInt) => true
			case (`algTk`, _: SfString) | (`nonceTk`, _: SfString) => true
			// we are lenient on non-registered params
			case (attr, _) => !registeredParams.contains(attr)
		}
		def keyIdExists = il.params.get(`keyidTk`).isDefined
		keyIdExists && headersOk && paramsOk
	end valid

}
