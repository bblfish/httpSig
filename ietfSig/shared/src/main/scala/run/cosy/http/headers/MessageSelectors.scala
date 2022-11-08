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
trait MessageSelectors[F[_], H <: Http]:
   import Http.*
   val securedConnection: Boolean

   lazy val authorization: MessageSelector[Request[F, H]]
   lazy val `cache-control`: MessageSelector[Message[F, H]]
   lazy val date: MessageSelector[Message[F, H]]

   lazy val etag: MessageSelector[Response[F, H]]
   lazy val host: MessageSelector[Request[F, H]]
   lazy val signature: DictSelector[Message[F, H]]

   lazy val `content-type`: MessageSelector[Message[F, H]]
   lazy val `content-length`: MessageSelector[Message[F, H]]
   lazy val digest: MessageSelector[Message[F, H]]
   lazy val forwarded: MessageSelector[Message[F, H]]
   lazy val `client-cert`: MessageSelector[Message[F, H]]

   lazy val `@method`: MessageSelector[Request[F, H]]
   lazy val `@target-uri`: MessageSelector[Request[F, H]]
   lazy val `@authority`: MessageSelector[Request[F, H]]
   lazy val `@scheme`: MessageSelector[Request[F, H]]
   lazy val `@request-target`: MessageSelector[Request[F, H]]
   lazy val `@path`: MessageSelector[Request[F, H]]
   lazy val `@query`: MessageSelector[Request[F, H]]
   lazy val `@query-param`: MessageSelector[Request[F, H]]
   lazy val `@status`: MessageSelector[Response[F, H]]

   /** Note: @target-uri and @scheme can only be set by application code as a choice needs to be
     * made
     */
   given requestSelectorOps: SelectorOps[Request[F, H]] = SelectorOps[Request[F, H]](
     authorization,
     host,
     forwarded,
     `client-cert`,
     `@request-target`,
     `@method`,
     `@path`,
     `@query`,
     `@query-param`,
     `@authority`,
     // all the below are good for responses too
     digest,
     `content-length`,
     `content-type`,
     signature,
     date,
     `cache-control`
   )

   // both: digest, `content-length`, `content-type`, signature, date, `cache-control`

   given responseSelectorOps: SelectorOps[Response[F, H]] =
     SelectorOps[Response[F, H]](
       `@status`,
       etag,
       // all these are good for requests too
       digest,
       `content-length`,
       `content-type`,
       signature,
       date,
       `cache-control`
     )

end MessageSelectors
