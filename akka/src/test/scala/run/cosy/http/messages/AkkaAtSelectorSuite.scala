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
import run.cosy.akka.http.messages.SelectorFnsAkka
import run.cosy.http.messages.{AtSelectorSuite, TestHttpMsgInterpreter}
import run.cosy.http.messages.{AtSelectors, ServerContext}

class AkkaAtSelectorSuite extends AtSelectorSuite[Id, AkkaTp.HT]:

   def sel(using ServerContext): AtSelectors[Id, HT] =
     new AtSelectors[cats.Id, HT](using new SelectorFnsAkka()) {}

   def interp: TestHttpMsgInterpreter[cats.Id, HT] = AkkaMsgInterpreter
   def platform: Platform                          = Platform.Akka
