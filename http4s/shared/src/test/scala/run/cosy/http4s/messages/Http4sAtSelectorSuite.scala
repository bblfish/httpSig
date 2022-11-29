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

import run.cosy.http.Http
import run.cosy.http.messages.{
  AtSelectorSuite,
  AtSelectors,
  TestHttpMsgInterpreter,
  Platform,
  ServerContext
}
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT as H4
import run.cosy.http4s.messages.{Http4sMsgInterpreter, SelectorFnsH4}

class Http4sAtSelectorSuite[F[_]] extends AtSelectorSuite[F, H4]:
   def sel(using ServerContext): AtSelectors[F, H4] =
     new AtSelectors[F, H4](using new SelectorFnsH4[F]) {}

   def interp: TestHttpMsgInterpreter[F, H4] = new Http4sMsgInterpreter[F]
   def platform: Platform                    = Platform.Http4s
