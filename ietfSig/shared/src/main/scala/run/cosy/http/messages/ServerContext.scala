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

object ServerContext:
   def apply(defaultHost: String, secure: Boolean): ServerContext =
     new ServerContext(Some(defaultHost), secure, if secure then 443 else 80)

   /** If the server wishes the default host to remain unguessable then use this constructor
     */
   def apply(secure: Boolean): ServerContext =
     new ServerContext(None, secure, if secure then 443 else 80)

   def apply(defaultHost: String, secure: Boolean, port: Int) =
     new ServerContext(Some(defaultHost), secure, port)
end ServerContext

/** the server context may not know the default Host, but it cannot really not know if the server is
  * running http or https...
  */
class ServerContext private (val defaultHost: Option[String], val secure: Boolean, val port: Int)
