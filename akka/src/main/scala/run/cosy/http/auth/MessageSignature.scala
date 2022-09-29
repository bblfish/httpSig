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

package run.cosy.http.auth

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.util.FastFuture
import cats.Applicative
import run.cosy.akka.http.AkkaTp.H4
import run.cosy.http.Http.{Header, Message}
import run.cosy.http.headers.SelectorOps

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

// selector needs to be filled in
object AkkaHttpMessageSignature extends run.cosy.http.auth.MessageSignature[H4]:
   import AkkaHttpMessageSignature.*
//	override type Message = HttpMessage

   override protected val Signature: SignatureMatcher[H4] =
     run.cosy.akka.http.headers.Signature
   override protected val `Signature-Input`: SignatureInputMatcher[H4] =
     run.cosy.akka.http.headers.`Signature-Input`
