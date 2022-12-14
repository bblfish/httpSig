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

import bobcats.Verifier
import cats.effect.{MonadCancel, Sync, SyncIO}
import run.cosy.http.auth.VerifySignatureTests
import run.cosy.http.messages.*
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT
import run.cosy.http4s.messages.SelectorFnsH4

given ServerContext              = ServerContext("bblfish.net", true)
given TestHttpMsgInterpreter[HT] = Http4sMsgInterpreter

given ReqFns[HT] = new SelectorFnsH4

class H4HeaderSuite extends HeaderSuite(
      new ReqSelectors[HT]
    )

class Http4SAtRequestSelectorSuite extends AtRequestSelectorSuite[HT]:
   def sel(using ServerContext): AtReqSelectors[HT] =
     new AtReqSelectors[HT](using new SelectorFnsH4) {}
   def interp: TestHttpMsgInterpreter[HT] = Http4sMsgInterpreter
   def platform: HttpMsgPlatform          = HttpMsgPlatform.Http4s

class H4ReqSigSuite[F[_]] extends SigInputReqSuite[HT](
      new ReqComponentDB(
        new ReqSelectors[HT],
        HeaderIds.all
      )
    )

class H4StaticSigInputReqSuite
    extends VerifyBaseOnRequests[HT](
      new ReqSelectors[HT]
    )
