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

import cats.data.NonEmptyList
import cats.effect.Async

import java.util.Locale
import scala.collection.immutable.ListMap
//todo: SignatureBytes is less likely to class with objects like Signature
import _root_.run.cosy.http.headers.{HeaderComponent, Rfc8941}
import scodec.bits.ByteVector
import Rfc8941.{Params, SfString}
import scala.util.{Failure, Success, Try}
import run.cosy.platform

/** Becayse this test does not depend on any other HTTP lib implementations, we only need to run it
  * in the test project. Here we test just the header selectors, since they don't depend on any
  * underlying HTTP Framework.
  */
class HeaderSuite extends munit.FunSuite:

   type HttpHeaders = Seq[(String, String)]

   // special headers used in the spec that we won't find elsewhere
   val `x-example`         = HeaderComponent("x-example").get
   val `x-empty-header`    = HeaderComponent("x-empty-header").get
   val `x-ows-header`      = HeaderComponent("x-ows-header").get
   val `x-obs-fold-header` = HeaderComponent("x-obs-fold-header").get
   val `example-dict`      = HeaderComponent("example-dict").get
   val `example-header`      = HeaderComponent("example-header").get
   val `cache-control`     = HeaderComponent("cache-control").get
   val `date`              = HeaderComponent("date").get
   val `host`              = HeaderComponent("host").get
   val VerticalTAB: Char   = "\u000B".head

   val sfTk   = Rfc8941.Token("sf")
   val nameTk = Rfc8941.Token("name")
   val reqTk  = Rfc8941.Token("req")
   val keyTk = Rfc8941.Token("key")
   val bsTk = Rfc8941.Token("bs")

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
   val `§2.1_HeaderField`: HttpHeaders = Seq(
     "Host"              -> "www.example.com",
     "Date"              -> "Sat, 07 Jun 2014 20:51:35 GMT",
     "X-OWS-Header"      -> """     Leading and trailing whitespace.               """.stripMargin,
     "X-Obs-Fold-Header" -> " Obsolete    \u000B line folding".stripMargin,
     "Cache-Control"     -> "max-age=60",
     "Example-Header"    -> "value, with, lots",
     "X-Empty-Header"    -> "",
     "Cache-Control"     -> "   must-revalidate",
     "Example-Dict"      -> "a=1,    b=2;x=1;y=2,   c=(a   b   c) ",
     "Example-Dict"      -> "  d",
     "Example-Header"    -> "of, commas"
   )
   val `§2.1_HeadersWihoutObsLF`: HttpHeaders = `§2.1_HeaderField`.map { (k, v) =>
     (k, v.split(VerticalTAB).map(_.trim).mkString(" "))
   }

   def selectHeaders(header: String, from: Seq[(String, String)]): Try[NonEmptyList[String]] =
      val hl = platform.StringUtil.toLowerCaseInsensitive(header).nn
      from.collect {
        case (hd: String, value) if platform.StringUtil.toLowerCaseInsensitive(hd) == hl => value
      } match
         case Nil          => Failure(SelectorException(s"no headers >>$header<<"))
         case head :: tail => Success(NonEmptyList(head, tail))
   end selectHeaders

   test("§2.1 HTTP Field Examples") {
     val cbase =
       for
          cchdrsLst <- selectHeaders("cache-control", `§2.1_HeaderField`)
          cSel      <- `cache-control`()
          str       <- cSel.signingStr(cchdrsLst)
       yield str
     assertEquals(
       cbase,
       expectedHeader("cache-control", "max-age=60, must-revalidate")
     )

     // note: That date falls on a Saturday, not a Tuesday
     // This is useful test to show that we are not interpreting the strings at this point
     // The header may already have been rejected by the frameworks... We will need
     // to test that for each framework
     assertEquals(
       for
          cchdrsLst <- selectHeaders("date", `§2.1_HeaderField`)
          cSel      <- date()
          str       <- cSel.signingStr(cchdrsLst)
       yield str,
       expectedHeader("date", "Sat, 07 Jun 2014 20:51:35 GMT")
     )

     assertEquals(
       for
          cchdrsLst <- selectHeaders("host", `§2.1_HeaderField`)
          cSel      <- host()
          str       <- cSel.signingStr(cchdrsLst)
       yield str,
       expectedHeader("host", "www.example.com")
     )

     // obviously the above simplifies a lot
     assertEquals(
       baseFor(`x-empty-header`, `§2.1_HeaderField`),
       expectedHeader("x-empty-header", "")
     )
     // here we really need the headers after obs line folding has been removed
     // we continue using that from here.
     assertEquals(
       baseFor(`x-obs-fold-header`, `§2.1_HeadersWihoutObsLF`),
       expectedHeader("x-obs-fold-header", "Obsolete line folding")
     )
     // request tokens make no difference
     assertEquals(
       baseFor(`x-obs-fold-header`, `§2.1_HeadersWihoutObsLF`, Params(reqTk -> true)),
       expectedParamHeader("x-obs-fold-header", ";req","Obsolete line folding" )
     )
     assertEquals(
       baseFor(`x-ows-header`, `§2.1_HeadersWihoutObsLF`),
       expectedHeader("x-ows-header", "Leading and trailing whitespace.")
     )
     assertEquals(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`),
       expectedHeader("example-dict", "a=1,    b=2;x=1;y=2,   c=(a   b   c), d")
     )
     // request tokens make no difference (at this level)
     assertEquals(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`, Params(reqTk -> true)),
       expectedParamHeader(
         "example-dict", ";req",
         "a=1,    b=2;x=1;y=2,   c=(a   b   c), d")
     )
   }

   def baseFor(
       hc: HeaderComponent,
       hdrs: HttpHeaders,
       props: Rfc8941.Params = ListMap()
   ): Try[String] =
     for
        hl  <- selectHeaders(hc.name, hdrs)
        sel <- hc(props)
        str <- sel.signingStr(hl)
     yield str

   test("§2.1.1 Strict Serialization of HTTP Structured Fields") {
     assertEquals(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`, ListMap(sfTk -> true)),
       Success(""""example-dict";sf: a=1, b=2;x=1;y=2, c=(a b c), d""")
     )
     assertEquals(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`,
         ListMap(reqTk -> true, sfTk -> true)
       ),
       Success(""""example-dict";req;sf: a=1, b=2;x=1;y=2, c=(a b c), d""")
     )
     assertEquals(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`,
         ListMap( sfTk -> true, reqTk -> true)
       ),
       Success(""""example-dict";sf;req: a=1, b=2;x=1;y=2, c=(a b c), d""")
     )
    
   }
   def kv(key: String, value: String): String =
     ";"+key+"=\""+value+"\""

   
   test("§2.1.2 Dictionary Structured Field Members") {

     assertEquals(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`, Params(keyTk -> SfString("a"))),
       expectedParamHeader("example-dict", kv("key","a"), "1")
     )
     assertEquals(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`, Params(keyTk -> SfString("d"))),
       expectedParamHeader("example-dict",kv("key","d") , "?1")
     )
     assertEquals(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`, Params(keyTk -> SfString("d"))),
       expectedParamHeader("example-dict",kv("key","d"), "?1")
     )
     assertEquals(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`, Params(keyTk -> SfString("b"))),
       expectedParamHeader("example-dict",kv("key","b"), "2;x=1;y=2")
     )
     assertEquals(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`, Params(keyTk -> SfString("c"))),
       expectedParamHeader("example-dict",kv("key","c"), "(a b c)")
     )
     assertEquals(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`,
         Params(
           keyTk -> SfString("c"),
           reqTk -> true
         )
       ),
       expectedParamHeader("example-dict", kv("key", "c")+";req", "(a b c)")
     )
     assertEquals(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`,
         Params(
           reqTk -> true,
           keyTk -> SfString("c")
         )
       ),
       expectedParamHeader("example-dict", ";req"+kv("key", "c"), "(a b c)")
     )
     failureTest(
       baseFor(
         `example-dict`,
         `§2.1_HeadersWihoutObsLF`,
         Params(Rfc8941.Token("blah") -> Rfc8941.SfInt(4))
       )
     )
     failureTest(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`, Params(keyTk -> Rfc8941.SfInt(3)))
     )
     failureTest(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`, Params(nameTk -> Rfc8941.SfString("q")))
     )
     failureTest(
       baseFor(
         HeaderComponent("dodo").get,
         `§2.1_HeadersWihoutObsLF`
       )
     )
     failureTest(
       baseFor(
         HeaderComponent("dodo").get,
         `§2.1_HeadersWihoutObsLF`,
         Params(keyTk -> Rfc8941.SfString("a"))
       )
     )
     failureTest(
       baseFor(`example-dict`, `§2.1_HeadersWihoutObsLF`, Params(keyTk -> SfString("domino")))
     )
   }
   
   test("§2.1.3. Binary-wrapped HTTP Fields") {
     assertEquals(
       baseFor(`example-header`, `§2.1_HeadersWihoutObsLF`),
       expectedHeader("example-header", "value, with, lots, of, commas")
     )
     assertEquals(
       baseFor(`example-header`, `§2.1_HeadersWihoutObsLF`, Params(bsTk -> true )),
       expectedParamHeader("example-header", ";bs",
         ":dmFsdWUsIHdpdGgsIGxvdHM=:, :b2YsIGNvbW1hcw==:")
     )
     val oneLineExH = Seq("Example-Header"    -> "  value, with, lots, of, commas ")
     assertEquals(
       baseFor(`example-header`, oneLineExH, Params(bsTk -> true)),
       expectedParamHeader("example-header", ";bs",
         ":dmFsdWUsIHdpdGgsIGxvdHMsIG9mLCBjb21tYXM=:")
     )}

   def failureTest[X](shouldFail: Try[X]): Unit =
     assert(shouldFail.isFailure, shouldFail)

end HeaderSuite
