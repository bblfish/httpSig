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
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http.headers.SigInput
import scala.util.{Try, Success, Failure}
import cats.data.NonEmptyList
import run.cosy.http.headers.SigInput
import run.cosy.http.auth.InvalidSigException

object `@signature-params`:
   def onlyForRequests: Boolean               = false
   val lowercaseName                          = "@signature-params"
   val pitem                                  = Rfc8941.PItem(Rfc8941.SfString(lowercaseName))
   def signingStr(sigInput: SigInput): String = s""""@signature-params": ${sigInput.canon}"""
   def paramStr(sigInput: SigInput): String   = sigInput.il.params.canon
