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

package run.cosy.akka.http.headers

import _root_.akka.http.scaladsl.model.headers.{GenericHttpCredentials, HttpCredentials}
import run.cosy.http.headers.{HttpSig, Rfc8941}

import scala.util.Try

/** Parameters that come with an `Authorization: HttpSig proof=sig1` header
  *
  * @param proofName
  *   : The name of the signature to look for todo later: see if one can built an extractor for this
  *   for Akka
  */

object HSCredentials:
   def unapply(cred: HttpCredentials): Option[HttpSig] =
     cred match
     case GenericHttpCredentials("HttpSig", _, parameters) =>
       parameters.get("proof").flatMap(str => Try(HttpSig(Rfc8941.Token(str))).toOption)
     case _ => None
