package run.cosy.http.headers

import run.cosy.http.auth.{InvalidSigException, SelectorException}

import scala.collection.immutable.ListMap
import scala.util.{Failure, Try}


/**
 * Header info specifying properties of a header for Signing Http Messages,
 * and how to create them
 *
 * @tparam HttpMsg the type of the Http Message (req or response) to look at when
 *                 constructing the signing strings for that header
 * */
trait MessageSelector[-HttpMessage]:
	lazy val sf = Rfc8941.SfString(lowercaseName)
	lazy val special: Boolean = lowercaseName.startsWith("@")
	def lowercaseName: String
	/* Is this a special @message selector that must be used on requests even if signing a response?
	 * By default no.*/
	def specialForRequests: Boolean = false

	/**
	 * @return a signing string for this header for a given HttpMessage
	 *         Fail if the header does not exist or is malformed
	 * */
	def signingString(msg: HttpMessage, params: Rfc8941.Params = ListMap()): Try[String]
end MessageSelector

/** Selectors that don't look at headers, but at message properties such as Http Request,
 * but that don't require parameters */
trait BasicMessageSelector[HttpMessage] extends MessageSelector[HttpMessage]:
	/**
	 * @return a signing string for this header for a given HttpMessage
	 *         Fail if the header does not exist or is malformed
	 **/
	def signingString(msg: HttpMessage, params: Rfc8941.Params=ListMap()): Try[String] =
		if params.isEmpty then signingStringValue(msg).map(fullSigStr)
		else Failure(
			SelectorException(s"selector $lowercaseName does not take parameters. Received "+params)
		)
	/** to implement in subclasses **/
	protected def signingStringValue(msg: HttpMessage): Try[String]
	protected def fullSigStr(str: String): String =
		"\""+lowercaseName+"\": "+str
end BasicMessageSelector


trait DictSelector[HM] extends MessageSelector[HM]:
	val keyParam = Rfc8941.Token("key")
	/**
	 *
	 * @param params the parameters passed to this request
	 * @return a signing string for the selected entry of the sf-dictionary encoded header.
	 *         Fail if the header does not exist or is malformed
	 **/
	override
	def signingString(msg: HM, params: Rfc8941.Params=ListMap()): Try[String] =
		params.toSeq match
		case Seq() => signingStringFor(msg)
		case Seq(keyParam -> (tk: Rfc8941.SfString)) => signingStringFor(msg, tk)
		case _ => Failure(InvalidSigException(s"""Dictionary Selector params »${params}« is malformed """))

	protected
	def signingStringFor(msg: HM): Try[String]

	protected
	def signingStringFor(hm: HM, item: Rfc8941.SfString): Try[String]

//	override def valid(params: Rfc8941.Params): Boolean =
//		params.keys.isEmpty || (params.size == 1 && params.keys.head == keyParam)
end DictSelector


object `@signature-params` {
	def onlyForRequests: Boolean = false
	val lowercaseName = "@signature-params"
	val pitem = Rfc8941.PItem(Rfc8941.SfString(lowercaseName))
	def signingString(sigInput: SigInput): String = s""""@signature-params": ${sigInput.canon}"""
}

/**
 *
 * @param selectorDB a map from selector names (eg. `@status` or `date`) to the selector for those messages
 * @tparam HM The Type of the HttpMessage for the platform
 */
case class SelectorOps[HM] private (selectorDB: Map[Rfc8941.SfString, MessageSelector[HM]]):

	import Rfc8941.Serialise.given

	/** add new selectors to this one */
	def append(selectors: MessageSelector[HM]*): SelectorOps[HM] =
		val pairs = for (selector <- selectors)
			yield (Rfc8941.SfString(selector.lowercaseName) -> selector)
		SelectorOps(selectorDB ++ pairs.toSeq)

//	/**
//	 * Is the selector valid? I.e. do we have a selector and if it has parameters are they allowed?
//	 * @param selector e.g. `date`, `@query;name="q"` or `"x-dictionary";key="a"`
//	 * @return true if valid
//	 * todo: this does not seem to be used! check why? Remove
//	 */
//	def valid(selector: Rfc8941.PItem[Rfc8941.SfString]): Boolean =
//		selectorDB.get(selector.item).map(messageSelector =>
//			messageSelector.valid(selector.params)
//		).getOrElse(false)

	def select(msg: HM, selectorInfo: Rfc8941.PItem[Rfc8941.SfString]): Try[String] =
		for
			selector <- get(selectorInfo.item)
			str <- selector.signingString(msg, selectorInfo.params)
		yield str
	end select

	def get(selectorName: Rfc8941.SfString): Try[MessageSelector[HM]] =
		selectorDB.get(selectorName.item)
			.toRight(
				InvalidSigException(s"Header ${selectorName.item} is not supported")
			).toTry

end SelectorOps

object SelectorOps:
	def apply[Msg](msgSelectors: MessageSelector[Msg]*): SelectorOps[Msg] =
		val pairs = for (selector <- msgSelectors) yield Rfc8941.SfString(selector.lowercaseName) -> selector
		new SelectorOps(Map(pairs *))


