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

package run.cosy.http.message

import run.cosy.http.Http
import run.cosy.akka.http.AkkaTp.HT
import run.cosy.akka.http.AkkaTp
import cats.Id
import run.cosy.http.Http.Request
import akka.http.scaladsl.model.HttpMethods.*
import akka.http.scaladsl.model.headers.*
import akka.http.scaladsl.model.{
  ContentTypes,
  DateTime,
  HttpEntity,
  HttpMethods,
  HttpRequest,
  HttpResponse,
  MediaTypes,
  StatusCodes,
  Uri
}
import scala.language.implicitConversions
import HttpMessageDB as DB

object AkkaMsgInterpreter extends run.cosy.http.message.HttpMsgInterpreter[Id, AkkaTp.HT]:

   override def asRequest(header: DB.RequestStr): Request[Id, AkkaTp.HT] =
     header match
        case DB.`§2.1_HeaderField` => ???
        case DB.`2.2.1_Method_POST` => HttpRequest(
          HttpMethods.POST,
          Uri("/path?param=value"),
          headers = Seq(Host("www.example.com"))
        )
        case DB.`2.2.1_Method_GET` => HttpRequest(
            HttpMethods.GET,
            Uri("/path?param=value"),
            headers = Seq(Host("www.example.com"))
          )

end AkkaMsgInterpreter
