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

package run.cosy.http.headers

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMessage, Uri}
import run.cosy.akka.http.headers.{Signature, `Signature-Input`}
import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.SyntaxHelper.*
import run.cosy.http.headers.Rfc8941.{SfDict, SfInt, Token, IList as IL}
import run.cosy.http.utils.StringUtils.*

import java.util.Base64
import scala.collection.immutable.{ArraySeq, ListMap}
import scala.language.existentials
import scala.util.{Success, Try}

class TestSignatureHeadersFn extends munit.FunSuite:

   import Rfc8941.Serialise.given
   import run.cosy.akka.http.headers.given

   import scala.language.implicitConversions

   val ex1 =
     """sig1=( "@request-target"  "host"   "date"    "cache-control" ); \
		  |       keyid="/keys/key#k1"; nonce="randomString";  \
		  |         created=1402170695;    expires=1402170995""".rfc8792single

   val ex2 =
     """sig2=(   "host"   "date"  "cache-control" "@request-target"); \
		  |       created=140217000;    expires=140220000;   \
		  |       keyid="https://alice.pdf/k/clef#"""".rfc8792single

   // missing keyId, should be filtered out
   val ex3 =
     """sig3=("@request-target" "host" "date" "cache-control" "x-empty-header" \
		  |     "x-example"); alg="rsa-pss-sha512"; \
		  |     created=1402170695; expires=1402170995""".rfc8792single

   // broken header, should be filtered out
   val ex4 =
     """sig3=("@request-target" 5 "host" "date" "cache-control" "x-empty-header" \
		  |     "x-example"); keyid="test-key-a"; alg="rsa-pss-sha512"; \
		  |     created=1402170695; expires=1402170995""".rfc8792single

   val expected1 = IL(
     sf"@request-target",
     sf"host",
     sf"date",
     sf"cache-control"
   )(
     Token("keyid")   -> sf"/keys/key#k1",
     Token("nonce")   -> sf"randomString",
     Token("created") -> SfInt("1402170695"),
     Token("expires") -> SfInt("1402170995")
   )

   val expected2 = IL(
     sf"host",
     sf"date",
     sf"cache-control",
     sf"@request-target"
   )(
     Token("created") -> SfInt("140217000"),
     Token("expires") -> SfInt("140220000"),
     Token("keyid")   -> sf"https://alice.pdf/k/clef#"
   )

   // keyid missing
   val expected3 = IL(
     sf"@request-target",
     sf"host",
     sf"date",
     sf"cache-control",
     sf"x-empty-header",
     sf"x-example"
   )(
     Token("alg")     -> sf"rsa-pss-sha512",
     Token("created") -> SfInt("1402170695"),
     Token("expires") -> SfInt("1402170995")
   )

   test("`Signature-Input` with one header") {
     val Success(tsi1) = `Signature-Input`.parse(ex1): @unchecked
     val Some(sig1)    = tsi1.get(Token("sig1")): @unchecked
     assertEquals(sig1.il, expected1)

     val RawHeader(name, value) = `Signature-Input`(tsi1): @unchecked
     assertEquals(name, "Signature-Input")
     val expectedHdr: SfDict = ListMap(Token("sig1") -> expected1)
     assertEquals(value, expectedHdr.canon)

     RawHeader("Signature-Input", ex1) match
        case `Signature-Input`(sis) =>
          assertEquals(sis.si.size, 1)
          assertEquals(sis.si.keys.head, Token("sig1"))
          assertEquals(sis.si.values.head, sig1)
          val sigIn: SigInput = sis.si.values.head
          assert(sigIn.headers.contains("cache-control"))
          assertEquals(sigIn.keyid.get.asciiStr, "/keys/key#k1")
          assertEquals(sigIn.created, Some(1402170695L))
          assertEquals(sigIn.expires, Some(1402170995L))
        case _ => fail
   }

   test("`Signature-Input` with three headers") {
     val sigTxt        = s"$ex1, $ex3,  $ex2"
     val Success(tsi1) = `Signature-Input`.parse(sigTxt): @unchecked
     assertEquals(tsi1.si.size, 3) // filtered out ex3
     val Some(sig1) = tsi1.get(Token("sig1")): @unchecked
     val Some(sig2) = tsi1.get(Token("sig2")): @unchecked
     val Some(sig3) = tsi1.get(Token("sig3")): @unchecked
     assertEquals(sig1.il, expected1)
     assertEquals(sig2.il, expected2)
     assertEquals(sig3.il, expected3)
     val RawHeader(name, value) = `Signature-Input`(tsi1): @unchecked
     assertEquals(name, "Signature-Input")
     val expectedHdr: SfDict =
       ListMap(Token("sig1") -> expected1, Token("sig3") -> expected3, Token("sig2") -> expected2)
     assertEquals(value, expectedHdr.canon)
     RawHeader("Signature-Input", s"$ex1, $ex2, $ex3") match
        case `Signature-Input`(sis) =>
          assertEquals(sis.si.size, 3)
          assertEquals(sis.si.keys.head, Token("sig1"))
          assertEquals(sis.si.keys.tail.head, Token("sig2"))
          assertEquals(sis.si.values.head, sig1)
          assertEquals(sis.si.values.tail.head, sig2)
          val sigIn: SigInput = sis.si.values.head
          assertEquals(sigIn.headers, Seq("@request-target", "host", "date", "cache-control"))
          assertEquals(sigIn.keyid.get.asciiStr, "/keys/key#k1")
          assertEquals(sigIn.created, Some(1402170695L))
          assertEquals(sigIn.expires, Some(1402170995L))
          val sigIn2: SigInput = sis.si.values.tail.head
          assertEquals(sigIn2.headers, Seq("host", "date", "cache-control", "@request-target"))
          assertEquals(sigIn2.keyid.get.asciiStr, "https://alice.pdf/k/clef#")
          assertEquals(sigIn2.created, Some(140217000L))
          assertEquals(sigIn2.expires, Some(140220000L))
        case _ => fail
   }

   val base64Ex1 =
     """K2qGT5srn2OGbOIDzQ6kYT+ruaycnDAAUpKv+ePFfD0RAxn/1BUe\
		  |      Zx/Kdrq32DrfakQ6bPsvB9aqZqognNT6be4olHROIkeV879RrsrObury8L9SCEibe\
		  |      oHyqU/yCjphSmEdd7WD+zrchK57quskKwRefy2iEC5S2uAH0EPyOZKWlvbKmKu5q4\
		  |      CaB8X/I5/+HLZLGvDiezqi6/7p2Gngf5hwZ0lSdy39vyNMaaAT0tKo6nuVw0S1MVg\
		  |      1Q7MpWYZs0soHjttq0uLIA3DIbQfLiIvK6/l0BdWTU7+2uQj7lBkQAsFZHoA96ZZg\
		  |      FquQrXRlmYOh+Hx5D9fJkXcXe5tmAg==""".rfc8792single
   val base64Ex2 =
     """ON3HsnvuoTlX41xfcGWaOEVo1M3bJDRBOp0Pc/O\
		  |       jAOWKQn0VMY0SvMMWXS7xG+xYVa152rRVAo6nMV7FS3rv0rR5MzXL8FCQ2A35DCEN\
		  |       LOhEgj/S1IstEAEFsKmE9Bs7McBsCtJwQ3hMqdtFenkDffSoHOZOInkTYGafkoy78\
		  |       l1VZvmb3Y4yf7McJwAvk2R3gwKRWiiRCw448Nt7JTWzhvEwbh7bN2swc/v3NJbg/w\
		  |       JYyYVbelZx4IywuZnYFxgPl/qvqbAjeEVvaLKLgSMr11y+uzxCHoMnDUnTYhMrmOT\
		  |       4O8lBLfRFOcoJPKBdoKg9U0a96U2mUug1bFOozEVYFg==""".rfc8792single
   val signEx1 = s"""sig1=:$base64Ex1:"""
   val signEx2 = s"""reverse_proxy_sig=:$base64Ex2:, sig1=:$base64Ex1:"""

   test("Signature") {
     import Rfc8941.Token as Tk
     val Success(sigs1) = Signature.parse(signEx1): @unchecked
     assertEquals(sigs1.sigmap.size, 1)
     val Some(sig1Arr) = sigs1.get(Tk("sig1")): @unchecked
     assertEquals(sig1Arr, ArraySeq.from(Base64.getDecoder.nn.decode(base64Ex1).nn))

     val Success(sigs2) = Signature.parse(signEx2): @unchecked
     assertEquals(sigs2.sigmap.size, 2)
     val Some(sig2Arr) = sigs2.get(Tk("sig1")): @unchecked
     assertEquals(sig2Arr, ArraySeq.from(Base64.getDecoder.nn.decode(base64Ex1).nn))
     val Some(sig3Arr) = sigs2.get(Tk("reverse_proxy_sig")): @unchecked
     assertEquals(sig3Arr, ArraySeq.from(Base64.getDecoder.nn.decode(base64Ex2).nn))

   }
