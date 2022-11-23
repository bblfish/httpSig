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
import _root_.run.cosy.http.messages.{HeaderSelectors, HttpMsgInterpreter, RequestSelector, Selectors, HttpMessageDB as DB}
import _root_.run.cosy.platform
import cats.data.NonEmptyList
import cats.effect.Async
import munit.CatsEffectSuite
import scodec.bits.ByteVector

import java.util.Locale
import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

/** Becayse this test does not depend on any other HTTP lib implementations, we only need to run it
  * in the test project. Here we test just the header selectors, since they don't depend on any
  * underlying HTTP Framework.
  */
open class HeaderSuite[F[_], H <: Http](
    selectr: HeaderSelectors[F, H],
    interp: HttpMsgInterpreter[F, H]
) extends CatsEffectSuite:

   import _root_.run.cosy.http.headers.Rfc8941
   import Rfc8941.Syntax.*
   import Selectors.CollationTp as Ctp
   type ReqSel = Ctp => RequestSelector[F, H]

   // special headers used in the spec that we won't find elsewhere
   val `x-example`: ReqSel         = c => selectr.requestHeader(sf"x-example", c)
   val `x-empty-header`: ReqSel    = c => selectr.requestHeader(sf"x-empty-header", c)
   val `x-ows-header`: ReqSel      = c => selectr.requestHeader(sf"x-ows-header", c)
   val `x-obs-fold-header`: ReqSel = c => selectr.requestHeader(sf"x-obs-fold-header", c)
   val `example-dict`: ReqSel      = c => selectr.requestHeader(sf"example-dict", c)
   val `example-header`: ReqSel    = c => selectr.requestHeader(sf"example-header", c)
   val `cache-control`: ReqSel     = c => selectr.requestHeader(sf"cache-control", c)
   val `date`: ReqSel              = c => selectr.requestHeader(sf"date", c)
   val `host`: ReqSel              = c => selectr.requestHeader(sf"host", c)
   val VerticalTAB: Char           = "\u000B".head

   val sfTk   = Rfc8941.Token("sf")
   val nameTk = Rfc8941.Token("name")
   val reqTk  = Rfc8941.Token("req")
   val keyTk  = Rfc8941.Token("key")
   val bsTk   = Rfc8941.Token("bs")

//   given ec: ExecutionContext = scala.concurrent.ExecutionContext.global
//   given clock: Clock =
//     Clock.fixed(java.time.Instant.ofEpochSecond(16188845000L), java.time.ZoneOffset.UTC).nn

   def expectedParamHeader(name: String, params: String, value: String) =
     Success(s""""$name"$params: $value""")

   def expectedNameHeader(name: String, nameVal: String, value: String): Success[String] =
     Success("\"" + name + "\";name=\"" + nameVal + "\": " + value)

   def expectedHeader(name: String, value: String) =
     Success(s""""$name": $value""")

   // helper method
   extension (bytes: Try[ByteVector])
     def toAscii: Try[String] = bytes.flatMap(_.decodeAscii.toTry)

   /** example from [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html
     */
   // the example in the spec does not have the `GET`. Added here for coherence
   val `§2.1_HF`: Request[F, H] = interp.asRequest(DB.`§2.1_HeaderField`)

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
       `cache-control`(Ctp.Raw).signingStr(`§2.1_HF`),
       expectedHeader("cache-control", "max-age=60, must-revalidate")
     )

     // note: That date falls on a Saturday, not a Tuesday
     // This is useful test to show that we are not interpreting the strings at this point
     // The header may already have been rejected by the frameworks... We will need
     // to test that for each framework
     assertEquals(
       date(Ctp.Raw).signingStr(`§2.1_HF`),
       expectedHeader("date", "Sat, 07 Jun 2014 20:51:35 GMT")
     )

     assertEquals(
       host(Ctp.Raw).signingStr(`§2.1_HF`),
       expectedHeader("host", "www.example.com")
     )

     // obviously the above simplifies a lot
     assertEquals(
       `x-empty-header`(Ctp.Raw).signingStr(`§2.1_HF`),
       expectedHeader("x-empty-header", "")
     )
     // here we really need the headers after obs line folding has been removed
     // we continue using that from here.
     assertEquals(
       `x-obs-fold-header`(Ctp.Raw).signingStr(`§2.1_HF`),
       expectedHeader("x-obs-fold-header", "Obsolete line folding.")
     )
   }
//    // request tokens make no difference
//    //actually they should not be allowed on requests (or at least not on production) -- they should perhaps
//    // be allowed on reception (lenient in what you accept). But that requires
//    assertEquals(
//      baseFor(`x-obs-fold-header`, `§2.1_HeadersWihoutObsLF`, Params(reqTk -> true)),
//      expectedParamHeader("x-obs-fold-header", ";req", "Obsolete line folding")
//    )
//    assertEquals(
//      baseFor(`x-ows-header`, `§2.1_HeadersWihoutObsLF`),
//      expectedHeader("x-ows-header", "Leading and trailing whitespace.")
//    )
//    assertEquals(
//      baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`),
//      expectedHeader("example-dict", "a=1,    b=2;x=1;y=2,   c=(a   b   c), d")
//    )
//    // request tokens make no difference (at this level)
//    assertEquals(
//      baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`, Params(reqTk -> true)),
//      expectedParamHeader(
//        "example-dict",
//        ";req",
//        "a=1,    b=2;x=1;y=2,   c=(a   b   c), d"
//      )
//    )
//  }

   import Selectors.Sf

   test("§2.1.1 Strict Serialization of Dictionary Structured Header Request") {
     assertEquals(
       `example-dict`(Ctp.Strict(Sf.Dictionary)).signingStr(`§2.1_HF`),
       Success(""""example-dict";sf: a=1, b=2;x=1;y=2, c=(a b c), d""")
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
       `example-dict`(Ctp.DictSel(sf"a")).signingStr(`§2.1_HF`),
       expectedParamHeader("example-dict", kv("key", "a"), "1")
     )
     assertEquals(
       `example-dict`(Ctp.DictSel(sf"d")).signingStr(`§2.1_HF`),
       expectedParamHeader("example-dict", kv("key", "d"), "?1")
     )
     assertEquals(
       `example-dict`(Ctp.DictSel(sf"b")).signingStr(`§2.1_HF`),
       expectedParamHeader("example-dict", kv("key", "b"), "2;x=1;y=2")
     )
     assertEquals(
       `example-dict`(Ctp.DictSel(sf"c")).signingStr(`§2.1_HF`),
       expectedParamHeader("example-dict", kv("key", "c"), "(a b c)")
     )
     //  assertEquals(
     //    baseFor(
     //      `example-dict`,
     //      `§2.1_HF`,
     //      Params(
     //        keyTk -> SfString("c"),
     //        reqTk -> true
     //      )
     //    ),
     //    expectedParamHeader("example-dict", kv("key", "c") + ";req", "(a b c)")
     //  )
     //  assertEquals(
     //    baseFor(
     //      `example-dict`,
     //      `§2.1_HF`,
     //      Params(
     //        reqTk -> true,
     //        keyTk -> SfString("c")
     //      )
     //    ),
     //    expectedParamHeader("example-dict", ";req" + kv("key", "c"), "(a b c)")
     //  )
     //  failureTest(
     //      `example-dict`.,
     //      `§2.1_HF`,
     //      Params(Rfc8941.Token("blah") -> Rfc8941.SfInt(4))
     //    )
     //  )
     //  failureTest(
     //    baseFor(`example-dict`, `§2.1_HF`, Params(keyTk -> Rfc8941.SfInt(3)))
     //  )
     failureTest(
       `example-dict`(Ctp.DictSel(sf"q")).signingStr(`§2.1_HF`)
     )
     failureTest(
       selectr.requestHeader(sf"dodo").signingStr(`§2.1_HF`)
     )
     failureTest(
       selectr.requestHeader(sf"dodo", Ctp.DictSel(sf"a")).signingStr(`§2.1_HF`)
     )
     failureTest(
       selectr.requestHeader(sf"dodo", Ctp.DictSel(sf"domino")).signingStr(`§2.1_HF`)
     )
   }

   test("§2.1.3. Binary-wrapped HTTP Fields") {
     assertEquals(
       `example-header`(Ctp.Raw).signingStr(`§2.1_HF`),
       expectedHeader("example-header", "value, with, lots, of, commas")
     )
     assertEquals(
       `example-header`(Ctp.Bin).signingStr(`§2.1_HF`),
       expectedParamHeader(
         "example-header",
         ";bs",
         ":dmFsdWUsIHdpdGgsIGxvdHM=:, :b2YsIGNvbW1hcw==:"
       )
     )
   }

   def failureTest[X](shouldFail: Try[X]): Unit =
     assert(shouldFail.isFailure, shouldFail)

end HeaderSuite
