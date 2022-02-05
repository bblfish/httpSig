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

   case class KeyidSubj(keyId: String, pubKey: PublicKey) extends Agent with Keyidentifier
       with PubKey
   case class PureKeyId(keyId: String) extends Keyidentifier

   /** KeyId agents interpret the `keyid` field of Message Signing as a URI.
     *
     * @param keyIdUri
     * @param pubKey
     */
//	case class KeyIdAgent(keyIdUri: Uri, pubKey: PublicKey) extends Agent with KeyId with PubKey

   class Anonymous                 extends Agent
   object WebServerAgent           extends Agent
   case class WebIdAgent(uri: Uri) extends Agent

end AgentIds
