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

package run.cosy.http4s.auth

import run.cosy.http.auth.{SignatureInputMatcher, SignatureMatcher}
import run.cosy.http4s.Http4sTp.H4

object Http4sMessageSignature extends run.cosy.http.auth.MessageSignature[H4]:
   override protected val Signature: SignatureMatcher[H4] = run.cosy.http4s.headers.Signature
   override protected val `Signature-Input`: SignatureInputMatcher[H4] =
     run.cosy.http4s.headers.`Signature-Input`
