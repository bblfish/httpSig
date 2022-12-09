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

/** The Signature to look at. Can be passed in an Authorization header. In the example below the
  * proof name would be "sig1".
  * {{{
  *    GET /comments/ HTTP/1.1
  *    Authorization: HttpSig proof=sig1
  *    Accept: text/turtle, application/ld+json;q=0.9
  *    Signature-Input: sig1=("@request-target" "authorization");keyid="/keys/alice#"
  *    Signature: sig1=:jnwCuSDVKd8royZnKgm0GBQzLc...:
  * }}}
  *
  * @param proofName
  *   the name of the proof to look at
  */
case class HttpSig(proofName: Rfc8941.Token)

object HttpSig:
   @throws[ParsingException]
   def apply(token: String): HttpSig = new HttpSig(Rfc8941.Token(token))
