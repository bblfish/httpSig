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

package run.cosy.akka.http.headers

import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.ContentTypes.NoContentType
import akka.http.scaladsl.model.headers.*
import cats.data.NonEmptyList
import run.cosy.akka.http.headers.BetterCustomHeader
import run.cosy.http.Http
import run.cosy.http.auth.{AttributeMissingException, HTTPHeaderParseException, SelectorException, UnableToCreateSigHeaderException}
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http.headers.Rfc8941.{PItem, Params, Serialise, SfDict, SfString}
import run.cosy.http.headers.*

import java.nio.charset.Charset
import java.util.Locale
import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}
import cats.Id
import run.cosy.akka.http.AkkaTp.H4
import akka.http.scaladsl.model as ak
import cats.effect.*

/** Selectors that work on headers but take no parameters. */
trait AkkaBasicHeaderSelector[HM <: Http.Message[IO,H4]]
    extends BasicMessageSelector[HM] with AkkaHeaderSelector[HM]:

   override def lowercaseHeaderName: String = lowercaseName
   override protected def signingStringValue(msg: HM): Try[String] =
     filterHeaders(msg).map(SelectorOps.collate)

/** Akka's builtin header parsers have specialised parsers for the most well known headers.
  */
trait TypedAkkaSelector[HM <: Http.Message[IO,H4], HdrType <: HttpHeader: scala.reflect.ClassTag]
    extends AkkaBasicHeaderSelector[HM]:
   override val lowercaseName: String = akkaCompanion.lowercaseName
   def akkaCompanion: ModeledCompanion[HdrType]
   override def filterHeaders(msg: HM): Try[NonEmptyList[String]] =
      val m = msg.asInstanceOf[ak.HttpMessage]
      val headerValues: Seq[String] = m.headers[HdrType].map(_.value())
      headerValues match
         case Seq() => Failure(UnableToCreateSigHeaderException(
             s"No headers »$lowercaseHeaderName« in http message"
           ))
         case head :: tail => Success(NonEmptyList(head, tail))

//todo: the type IO is arbitrary for Akka. Currently using it because of tests, which is not right
trait AkkaHeaderSelector[HM <: Http.Message[IO,H4]] extends HeaderSelector[HM]:
   override def filterHeaders(msg: HM): Try[NonEmptyList[String]] =
      val m = msg.asInstanceOf[ak.HttpMessage]
      val headersValues: Seq[String] = m.headers
        .filter(_.lowercaseName() == lowercaseHeaderName)
        .map(_.value())
      headersValues match
         case Seq() => Failure(UnableToCreateSigHeaderException(
             s"No headers »$lowercaseHeaderName« in http message"
           ))
         case head :: tail => Success(NonEmptyList(head, tail))

/** for all headers for which Akka HTTP does not provide a built-in parser */
trait UntypedAkkaSelector[HM <: Http.Message[IO, H4]]
    extends AkkaBasicHeaderSelector[HM] with AkkaHeaderSelector[HM]

/** todo: the UntypedAkkaSelector inheritance may not be long term */
trait AkkaDictSelector[HM <: Http.Message[IO, H4]]
    extends DictSelector[HM] with AkkaHeaderSelector[HM]:
   override def lowercaseHeaderName: String = lowercaseName
