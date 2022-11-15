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

package run.cosy.akka.http.message

import cats.Id
import run.cosy.akka.http.AkkaTp
import run.cosy.akka.http.AkkaTp.HT
import run.cosy.http.Http.Request
import run.cosy.http.headers.Rfc8941.Params
import run.cosy.http.headers.{AtComponents, AtSelector, Rfc8941}

object AtComponentsAkka extends AtComponents[Id, AkkaTp.HT]:
   import run.cosy.http.headers.Component.*

   override def method(onReq: Boolean): AtSelector[Request[Id, HT]] =
      val p: Rfc8941.Params = if onReq == true then Params(reqTk -> true) else Params()
      `@method`(p).get
end AtComponentsAkka
