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

//todo: SignatureBytes is less likely to class with objects like Signature
import _root_.run.cosy
import _root_.run.cosy.http.Http
import _root_.run.cosy.http.Http.Request
import _root_.run.cosy.http.headers.Rfc8941
import _root_.run.cosy.http.headers.Rfc8941.Token
import _root_.run.cosy.http.messages.{HeaderSelectors, RequestSelector, Selectors, TestHttpMsgInterpreter, HttpMessageDB as DB}
import _root_.run.cosy.platform
import cats.data.NonEmptyList
import cats.effect.Async
import munit.CatsEffectSuite
import run.cosy.http.auth.ParsingExc
import scodec.bits.ByteVector

import java.util.Locale
import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

object HeaderSuite:
  import HeaderId.*
    // special headers used in the spec that we won't find elsewhere
   val `x-example`: OldId = HeaderId("x-example").toTry.get
   val `x-empty-header`: OldId = HeaderId("x-empty-header").toTry.get
   val `x-ows-header`: OldId = HeaderId("x-ows-header").toTry.get
   val `x-obs-fold-header`: OldId = HeaderId("x-obs-fold-header").toTry.get
   val `example-dict`: DictId = HeaderId.dict("example-dict").toTry.get
   val `example-header`: OldId = HeaderId("example-header").toTry.get
   val VerticalTAB: Char           = "\u000B".head


/**
  * This tests headers, which can be run without an underlying http implementation.
  * Running on underlying implementations is still useful because those may or may not
  * interpret the headers before the client receives them.
  */
open class HeaderSuite[F[_], H <: Http](
    selectr: HeaderSelectors[F, H],
    interp: TestHttpMsgInterpreter[F, H]
) extends CatsEffectSuite:

   import _root_.run.cosy.http.headers.Rfc8941
   import Rfc8941.Syntax.*
   import Selectors as Ctp
   
   val exHd = HeaderSuite
   val hds = HeaderIds
   import HeaderId.{OldId,DictId,ListId,ItemId}
   
   // special headers used in the spec that we won't find elsewhere
   val `x-example` = selectr.onRequest(exHd.`x-example`)
   val `x-empty-header` = selectr.onRequest(exHd.`x-empty-header`)
   val `x-ows-header` = selectr.onRequest(exHd.`x-ows-header`)
   val `x-obs-fold-header` = selectr.onRequest(exHd.`x-obs-fold-header`)
   val `example-dict` = selectr.onRequest(exHd.`example-dict`)
   val `example-header` = selectr.onRequest(exHd.`example-header`)
   val `cache-control` = selectr.onRequest(hds.retrofit.`cache-control`)
   val date =  selectr.onRequest(hds.Response.`date`)
   val host = selectr.onRequest(hds.retrofit.`host`)
   import exHd.VerticalTAB
   
   val sfTk   = Rfc8941.Token("sf")
   val nameTk = Rfc8941.Token("name")
   val reqTk  = Rfc8941.Token("req")
   val keyTk  = Rfc8941.Token("key")
   val bsTk   = Rfc8941.Token("bs")


   def expectedParamHeader(name: String, params: String, value: String) =
     Right(s""""$name"$params: $value""")

   def expectedNameHeader(name: String, nameVal: String, value: String): Right[ParsingExc, String] =
     Right("\"" + name + "\";name=\"" + nameVal + "\": " + value)

   def expectedHeader(name: String, value: String) =
     Right(s""""$name": $value""")

   // helper method
   extension (bytes: Try[ByteVector])
     def toAscii: Try[String] = bytes.flatMap(_.decodeAscii.toTry)

   /** example from [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html
     */
   // the example in the spec does not have the `GET`. Added here for coherence
   lazy val `§2.1_HF`: Request[F, H] = interp.asRequest(DB.`§2.1_HeaderField`)

   //  val `§2.1_HeadersWihoutObsLF`: HttpHeaders = `§2.1_HeaderField`.map { (k, v) =>
   //    (k, v.split(VerticalTAB).map(_.trim).mkString(" "))
   //  }

   //  def selectHeaders(header: String, from: Seq[(String, String)]): Try[NonEmptyList[String]] =
   //     val hl = platform.StringUtil.toLowerCaseInsensitive(header).nn
   //     from.collect {
   //       case (hd: String, value) if platform.StringUtil.toLowerCaseInsensitive(hd) == hl => value
   //     } match
   //        case Nil          => Failure(SelectorException(s"no headers >>$header<<"))
   //        case head :: tail => Success(NonEmptyList(head, tail))
   //  end selectHeaders

   test("§2.1 HTTP Raw Field Examples") {
     assertEquals(
       `cache-control`(Selectors.Raw).signingStr(`§2.1_HF`),
       expectedHeader("cache-control", "max-age=60, must-revalidate")
     )

     // note: That date falls on a Saturday, not a Tuesday
     // This is useful test to show that we are not interpreting the strings at this point
     // The header may already have been rejected by the frameworks... We will need
     // to test that for each framework
     assertEquals(
        // the next comment line won't compile, which is good.
        //date(Selectors.DictSel).signingStr(`§2.1_HF`)
        date(Selectors.Raw).signingStr(`§2.1_HF`),
        expectedHeader("date", "Sat, 07 Jun 2014 20:51:35 GMT")
     )
     
     assertEquals(
        date(Selectors.Bin).signingStr(`§2.1_HF`),
        expectedParamHeader("date",";bs", ":U2F0LCAwNyBKdW4gMjAxNCAyMDo1MTozNSBHTVQ=:")
     )

     assertEquals(
       host(Selectors.Raw).signingStr(`§2.1_HF`),
       expectedHeader("host", "www.example.com")
     )

     // obviously the above simplifies a lot
     assertEquals(
       `x-empty-header`(Selectors.Raw).signingStr(`§2.1_HF`),
       expectedHeader("x-empty-header", "")
     )
     // here we really need the headers after obs line folding has been removed
     // we continue using that from here.
     assertEquals(
       `x-obs-fold-header`(Selectors.Raw).signingStr(`§2.1_HF`),
       expectedHeader("x-obs-fold-header", "Obsolete line folding.")
     )
   }

   import Selectors.SelFormat

   test("§2.1.1 Strict Serialization of Dictionary Structured Header Request") {
     assertEquals(
       `example-dict`(Selectors.Strict).signingStr(`§2.1_HF`),
       Right(""""example-dict";sf: a=1, b=2;x=1;y=2, c=(a b c), d""")
     )
   }

   test("§2.1.1 Strict Serialization of Dictionary Structured Header Responses".ignore) {
     //we ignore because  we don't yet have the setup to look at respons headers.
   }

   def kv(key: String, value: String): String =
     ";" + key + "=\"" + value + "\""

   test("§2.1.2 Dictionary Structured Field Members") {
     import Rfc8941.Token as Tk
     assertEquals(
       `example-dict`(Selectors.DictSel(sf"a")).signingStr(`§2.1_HF`),
       expectedParamHeader("example-dict", kv("key", "a"), "1")
     )
     assertEquals(
       `example-dict`(Selectors.DictSel(sf"d")).signingStr(`§2.1_HF`),
       expectedParamHeader("example-dict", kv("key", "d"), "?1")
     )
     assertEquals(
       `example-dict`(Selectors.DictSel(sf"b")).signingStr(`§2.1_HF`),
       expectedParamHeader("example-dict", kv("key", "b"), "2;x=1;y=2")
     )
     assertEquals(
       `example-dict`(Selectors.DictSel(sf"c")).signingStr(`§2.1_HF`),
       expectedParamHeader("example-dict", kv("key", "c"), "(a b c)")
     )
     failureTest(
       `example-dict`(Selectors.DictSel(sf"q")).signingStr(`§2.1_HF`)
     )
     failureTest(
       selectr.onRequest(HeaderId("dodo").toTry.get)(Selectors.Raw).signingStr(`§2.1_HF`)
     )
     failureTest(
       selectr.onRequest(HeaderId.dict("dodo").toTry.get)(Selectors.DictSel(sf"a")).signingStr(`§2.1_HF`)
     )
     failureTest(
       selectr.onRequest(HeaderId.dict("dodo").toTry.get)(Selectors.DictSel(sf"domino")).signingStr(`§2.1_HF`)
     )
   }

   test("§2.1.3. Binary-wrapped HTTP Fields") {
     assertEquals(
       `example-header`(Selectors.Raw).signingStr(`§2.1_HF`),
       expectedHeader("example-header", "value, with, lots, of, commas")
     )
     assertEquals(
       `example-header`(Selectors.Bin).signingStr(`§2.1_HF`),
       expectedParamHeader(
         "example-header",
         ";bs",
         ":dmFsdWUsIHdpdGgsIGxvdHM=:, :b2YsIGNvbW1hcw==:"
       )
     )
   }

   def failureTest[X](shouldFail: Either[ParsingExc, X]): Unit =
     assert(shouldFail.isLeft, shouldFail)

end HeaderSuite
