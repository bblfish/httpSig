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

package run.cosy.akka.http.messages

import cats.Id
import run.cosy.akka.http.AkkaTp
import run.cosy.akka.http.AkkaTp.HT
import run.cosy.http.Http.{Request, Response}
import run.cosy.http.headers.Rfc8941
import run.cosy.http.messages.Component.{nameTk, reqTk}
import run.cosy.http.headers.Rfc8941.{Item, Params, SfString}
import run.cosy.http.messages.AtSelector
import run.cosy.http.messages.{AtComponents, ServerContext}

class AtComponentsAkka(using ServerContext) extends AtComponents[Id, AkkaTp.HT]:
   import scala.language.implicitConversions

   override def method(onReq: Boolean = false): AtSelector[Request[Id, HT]] =
     `@method`(onReq).get

   override def authority(onReq: Boolean = false): AtSelector[Request[Id, AkkaTp.HT]] =
     `@authority`()(onReq).get

   override def requestTarget(onReq: Boolean = false): AtSelector[Request[Id, AkkaTp.HT]] =
     `@request-target`(onReq).get

   override def path(onReq: Boolean = false): AtSelector[Request[Id, AkkaTp.HT]] =
     `@path`(onReq).get

   override def query(onReq: Boolean = false): AtSelector[Request[Id, AkkaTp.HT]] =
     `@query`(onReq).get

   override def queryParam(
       name: String,
       onReq: Boolean = false
   ): AtSelector[Request[Id, AkkaTp.HT]] =
     `@query-param`(toP(onReq) + (nameTk -> SfString(name))).get

   override def scheme(onReq: Boolean = false): AtSelector[Request[Id, AkkaTp.HT]] =
     `@scheme`()(onReq).get

   override def targetUri(onReq: Boolean = false): AtSelector[Request[Id, AkkaTp.HT]] =
     new `@target-uri`()(onReq).get

   /** this appears on response only */
   override def status(): AtSelector[Response[cats.Id, AkkaTp.HT]] =
     `@status`().get

end AtComponentsAkka
