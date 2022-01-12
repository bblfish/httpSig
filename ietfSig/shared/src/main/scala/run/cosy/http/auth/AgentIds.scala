package run.cosy.http.auth

import scala.util.Try

trait Agent
trait Keyidentifier:
	def keyId: String

object AgentIds:
	type Uri
	type PublicKey
	type KeyIdAgent = Agent & KeyId & PubKey

	trait KeyId extends Keyidentifier:
		def keyIdUri: Uri

	trait PubKey:
		def pubKey: PublicKey

	case class KeyidSubj(keyId: String, pubKey: PublicKey) extends Agent with Keyidentifier with PubKey
	case class PureKeyId(keyId: String) extends Keyidentifier
	/**
	 * KeyId agents interpret the `keyid`  field of Message Signing as a URI.
	 *
	 * @param keyIdUri
	 * @param pubKey
	 */
//	case class KeyIdAgent(keyIdUri: Uri, pubKey: PublicKey) extends Agent with KeyId with PubKey

	class Anonymous extends Agent
	object WebServerAgent extends Agent
	case class WebIdAgent(uri: Uri) extends Agent

end AgentIds