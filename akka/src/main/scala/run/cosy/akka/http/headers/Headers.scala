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

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.{
  CustomHeader,
  ModeledCustomHeader,
  ModeledCustomHeaderCompanion,
  RawHeader
}
import run.cosy.akka.http.headers.{BetterCustomHeader, BetterCustomHeaderCompanion}

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.Charset
import java.util.Locale
import scala.io.Codec
import scala.language.{existentials, implicitConversions}
import scala.util.{Failure, Success, Try}

object Encoding:
   opaque type UnicodeString = String
   opaque type UrlEncoded    = String
   val utf8 = Charset.forName("UTF-8")

   implicit def toUnicode(str: String): UnicodeString = str

   extension (string: String)
      def asClean: UnicodeString = string
      def asEncoded: UrlEncoded  = string

   extension (clean: UnicodeString)
      def toString: String      = clean
      def urlEncode: UrlEncoded = URLEncoder.encode(clean, utf8).nn

   extension (encoded: UrlEncoded)
      def decode: Try[UnicodeString] = Try(URLDecoder.decode(encoded, utf8).nn)
      def onTheWire: String          = encoded

/** To be extended by companion object of a custom header extending [[ModeledCustomHeader]].
  * Implements necessary apply and unapply methods to make the such defined header feel "native".
  */
abstract class BetterCustomHeaderCompanion[H <: BetterCustomHeader[H]]:
   def name: String
   def lowercaseName: String = name.toLowerCase(Locale.ROOT).nn

   final implicit val implicitlyLocatableCompanion: BetterCustomHeaderCompanion[H] = this

/** Support class for building user-defined custom headers defined by implementing `name` and
  * `value`. By implementing a [[BetterCustomHeader]] instead of [[CustomHeader]] directly, all
  * needed unapply methods are provided for this class, such that it can be pattern matched on from
  * [[RawHeader]] and the other way around as well.
  */
abstract class BetterCustomHeader[H <: BetterCustomHeader[H]]
    extends CustomHeader:
   this: H =>
   final override def lowercaseName = name.toLowerCase(Locale.ROOT).nn
   final override def name          = companion.name
   def companion: BetterCustomHeaderCompanion[H]
