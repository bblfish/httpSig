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

package run.cosy.http4s.headers

import cats.data.NonEmptyList
import org.http4s.headers.Host
import org.http4s.{Message, Request, Response, Query}
import run.cosy.http.headers.{
  BasicMessageSelector,
  DictSelector,
  HeaderSelector,
  MessageSelector,
  Rfc8941,
  SelectorOps
}

import scala.util.{Failure, Success, Try}
import org.typelevel.ci.*
import run.cosy.http.Http
import run.cosy.http.auth.{
  AttributeMissingException,
  AuthExc,
  SelectorException,
  UnableToCreateSigHeaderException
}
import run.cosy.http.headers.Rfc8941.SfDict
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http4s.Http4sTp.H4
import org.http4s.{Request as H4Request, Message as H4Message, Response as H4Response}

import java.util.Locale

trait BasicHeaderSelector[F[_], HM <: Http.Message[F, H4]]
    extends BasicMessageSelector[HM] with Http4sHeaderSelector[F, HM]:
   def lowercaseHeaderName: String = lowercaseName

   override protected def signingStringValue(msg: HM): Try[String] =
     for headers <- filterHeaders(msg)
     yield SelectorOps.collate(headers)
end BasicHeaderSelector

trait Http4sHeaderSelector[F[_], HM <: Http.Message[F, H4]] extends HeaderSelector[HM]:
   lazy val ciLowercaseName: CIString = CIString(lowercaseHeaderName)

   override def filterHeaders(msg: HM): Try[NonEmptyList[String]] =
      val m = msg.asInstanceOf[H4Message[F]]
      for
         nonEmpty <- m.headers.get(ciLowercaseName)
           .toRight(UnableToCreateSigHeaderException(
             s"""No headers "$lowercaseHeaderName" in http message"""
           )).toTry
      yield nonEmpty.map(_.value)
end Http4sHeaderSelector

trait Http4sDictSelector[F[_], HM <: Http.Message[F, H4]]
    extends DictSelector[HM] with Http4sHeaderSelector[F, HM]:
   override lazy val lowercaseHeaderName: String = lowercaseName

end Http4sDictSelector
