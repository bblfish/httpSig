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

package run.cosy.http4s.messages

import run.cosy.http.messages.{SelectorFns, Selectors, ServerContext, StaticSigInputReqSuite}
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT
import run.cosy.http4s.auth.Http4sMessageSignature
import run.cosy.http4s.messages.SelectorFnsH4

object H4StaticSigInputReqSuite:
   def sel[F[_]] = new SelectorFnsH4[F](using ServerContext("bblfish.net", true))

class H4StaticSigInputReqSuite[F[_]]
    extends StaticSigInputReqSuite[F, HT](
      new Http4sMessageSignature[F](),
      new Selectors[F, HT](using H4StaticSigInputReqSuite.sel[F]),
      new Http4sMsgInterpreter[F]()
    )
