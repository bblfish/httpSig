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
import run.cosy.http.messages.{AtComponentSuite, AtSelectors, HttpMsgInterpreter, ServerContext}
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT as H4
import run.cosy.http4s.messages.{Http4sAt, Http4sMsgInterpreter}

class Http4sAtComponentSuite[F[_]] extends AtComponentSuite[F, H4]:
   def at(using ServerContext): AtSelectors[F, H4] =
     new AtSelectors(new Http4sAt[F])

   def interp: HttpMsgInterpreter[F, H4] = new Http4sMsgInterpreter[F]
