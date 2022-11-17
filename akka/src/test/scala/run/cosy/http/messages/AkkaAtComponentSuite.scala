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

import run.cosy.http.Http
import run.cosy.akka.http.AkkaTp.HT
import run.cosy.akka.http.AkkaTp
import cats.Id
import run.cosy.akka.http.messages.AtAkka
import run.cosy.http.messages.{AtComponentSuite, HttpMsgInterpreter}
import run.cosy.http.messages.{AtSelectors, ServerContext}

class AkkaAtComponentSuite extends AtComponentSuite[Id, AkkaTp.HT]:

   def at(using ServerContext): AtSelectors[cats.Id, AkkaTp.HT] =
     new AtSelectors(new AtAkka())
   def interp: HttpMsgInterpreter[cats.Id, AkkaTp.HT] = AkkaMsgInterpreter
