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

import run.cosy.http.Http
import run.cosy.http.headers.MessageSelector


// implementations will need to specify default hosts and ports
trait MessageSelectors[H <: Http] {
	import Http.*
	val securedConnection: Boolean

	lazy val authorization: MessageSelector[Request[H]]
	lazy val `cache-control`: MessageSelector[Message[H]]
	lazy val date: MessageSelector[Message[H]]

	lazy val etag: MessageSelector[Response[H]]
	lazy val host: MessageSelector[Request[H]]
	lazy val signature: DictSelector[Message[H]]

	lazy val `content-type`: MessageSelector[Message[H]]
	lazy val `content-length`: MessageSelector[Message[H]]
	lazy val digest: MessageSelector[Message[H]]
	lazy val forwarded: MessageSelector[Message[H]]
	lazy val `client-cert`: MessageSelector[Message[H]]

	lazy val `@request-target`: MessageSelector[Request[H]]
	lazy val `@method`: MessageSelector[Request[H]]
	lazy val `@target-uri`: MessageSelector[Request[H]]
	lazy val `@authority`: MessageSelector[Request[H]]
	lazy val `@scheme`: MessageSelector[Request[H]]
	lazy val `@path`: MessageSelector[Request[H]]
	lazy val `@status`: MessageSelector[Response[H]]
	lazy val `@query`: MessageSelector[Request[H]]
	lazy val `@query-params`: MessageSelector[Request[H]]
	lazy val `@request-response`:  MessageSelector[Request[H]]


	/** Note: @target-uri and @scheme can only be set by application code as a choice needs to be made */
	given requestSelectorOps: SelectorOps[Request[H]] = SelectorOps[Request[H]](
		authorization,  host, forwarded, `client-cert`,
		`@request-target`, `@method`, `@path`, `@query`, `@query-params`,
		`@request-response`, `@authority`,
		//all the below are good for responses too
		digest, `content-length`, `content-type`, signature, date, `cache-control`
	)

	//both: digest, `content-length`, `content-type`, signature, date, `cache-control`

	given responseSelectorOps: SelectorOps[Response[H]] =
		SelectorOps[Response[H]](`@status`, etag,
			//all these are good for requests too
			digest, `content-length`, `content-type`, signature, date, `cache-control`
		)
}
