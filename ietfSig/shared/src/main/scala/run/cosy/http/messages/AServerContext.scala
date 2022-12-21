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

package run.cosy.http.messages

object AServerContext:
   def apply(defaultHost: String, secure: Boolean): AServerContext =
     new AServerContext(Some(defaultHost), secure, if secure then 443 else 80)

   /** If the server wishes the default host to remain unguessable then use this constructor
     */
   def apply(secure: Boolean): AServerContext =
     new AServerContext(None, secure, if secure then 443 else 80)

   def apply(defaultHost: String, secure: Boolean, port: Int): AServerContext =
     new AServerContext(Some(defaultHost), secure, port)

end AServerContext

/** the server context may not know the default Host, but it cannot really not know if the server is
  * running http or https...
  */
sealed trait ServerContext

/** The Server Context is usually needed on the server */
class AServerContext private (
    val defaultHost: Option[String],
    val secure: Boolean,
    val port: Int
) extends ServerContext

/** a client will most often not need to send a ServerContext as it has to build a full URL...
  * (Exceptions to this could arise more frequently with p2p http where a server could change role
  * to a client not knowing what the server was... But then it would not be a good idea to sign the
  * headers that need a server context.) An None[ServerContext] would have been an equivalent way to
  * model this.
  */
object NoServerContext extends ServerContext
