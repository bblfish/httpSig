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

import run.cosy.http.messages.*
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT
import run.cosy.http4s.messages.SelectorFnsH4

given ServerContext                         = ServerContext("bblfish.net", true)
given [F[_]]: TestHttpMsgInterpreter[F, HT] = new Http4sMsgInterpreter[F]

given [F[_]]: ReqFns[F, HT] = new SelectorFnsH4[F]

class H4HeaderSuite[F[_]] extends HeaderSuite(
      new ReqSelectors[F, HT]
    )

class Http4SAtRequestSelectorSuite[F[_]] extends AtRequestSelectorSuite[F, HT]:
   def sel(using ServerContext): AtReqSelectors[F, HT] =
     new AtReqSelectors[F, HT](using new SelectorFnsH4[F]) {}
   def interp: TestHttpMsgInterpreter[F, HT] = new Http4sMsgInterpreter[F]
   def platform: Platform                    = Platform.Http4s

class H4ReqSigSuite[F[_]] extends SigInputReqSuite[F, HT](
      new ReqComponentDB(
        new ReqSelectors[F, HT],
        HeaderIds.all
      )
    )

class H4StaticSigInputReqSuite[F[_]]
    extends StaticSigInputReqSuite[F, HT](
      new ReqSelectors[F, HT]
    )
