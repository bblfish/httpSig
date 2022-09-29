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

import run.cosy.http.HttpOps
import run.cosy.http.auth.SigningSuiteHelpers
import run.cosy.http4s.Http4sTp
import cats.effect.IO

class Http4sMessageSigningSuiteJS[F[_]] extends run.cosy.http4s.auth.Http4sMessageSigningSuite[F]:
   given pem: bobcats.util.PEMUtils             = bobcats.util.WebCryptoPEMUtils
   override given ops: HttpOps[Http4sTp.type]   = Http4sTp.httpOps
   override given sigSuite: SigningSuiteHelpers = new SigningSuiteHelpers
