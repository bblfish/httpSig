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

import run.cosy.http.headers.RFC8941Exception

//used to be ResponseSummary(on: Uri, code: StatusCode, header: Seq[HttpHeader], respTp: ContentType)
//but it would be complicated to adapt for akka and http4s types
case class ResponseSummary(onUri: String, code: String, header: Seq[String], respTp: String)

class AuthExc(msg: String)              extends Throwable(msg, null, true, false)
case class CryptoException(msg: String) extends AuthExc(msg)
case class AuthException(response: ResponseSummary, msg: String) extends AuthExc(msg)
case class InvalidCreatedFieldException(msg: String)             extends AuthExc(msg)
case class InvalidExpiresFieldException(msg: String)             extends AuthExc(msg)
case class UnableToCreateSigHeaderException(msg: String)         extends AuthExc(msg)
case class SelectorException(msg: String)                        extends AuthExc(msg)
case class InvalidSigException(msg: String)                      extends AuthExc(msg)
case class KeyIdException(msg: String)                           extends AuthExc(msg)
case class AttributeException(msg: String)                       extends AuthExc(msg)
case class HTTPHeaderParseException(error: cats.parse.Parser.Error, httpHeader: String)
    extends AuthExc(httpHeader)
