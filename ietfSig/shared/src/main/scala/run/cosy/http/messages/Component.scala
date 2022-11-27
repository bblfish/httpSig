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

import run.cosy.http.headers.Rfc8941

import scala.collection.immutable.ListMap
import scala.util.Try
import run.cosy.http.Http

/** Components such as `@query`` or `content-type` represent the names of constructors for
  * selectors. The parameters provide arguments to the construction of the selectors. There are two
  * types of selectors depending on whether they select data from a request or a response.
  * Individual components determine what type of selector is returned.
  *
  * Note: It is tempting to map the params to arguments, and pass them to well typed functions such
  * as found in Selectors. But then we would loose the order of the
  */
trait Component:

   val name: String
   lazy val lowercaseName: String = run.cosy.platform.StringUtil.toLowerCaseInsensitive(name).nn
   def paramsValid(params: Rfc8941.Params): Unit | String
end Component

/** Component for verifying signatures on request objects. The logic for this is now in
  * RequestSelectorDB So the question remains if it would be clearer to specify the code for each
  * component in a class like this?
  */
trait RequestComponent[F[_], H <: Http] extends Component:
   /** Create a selector with from this Component with the given parameters. If the parameters are
     * not legal for this component return a Failure
     */
   def apply(params: Rfc8941.Params = ListMap()): Try[RequestSelector[F, H]]

/** Component for verifying signatures on */
trait ResponseComponent extends Component
