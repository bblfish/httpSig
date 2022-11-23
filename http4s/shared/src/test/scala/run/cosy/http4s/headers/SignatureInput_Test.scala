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

import cats.effect.IO
import org.http4s.*
import org.http4s.Header.Raw
import org.http4s.dsl.io.*
import org.http4s.implicits.{*, given}
import run.cosy.http.utils.StringUtils.rfc8792single
import run.cosy.http4s.headers.`Signature-Input`.{*, given}
import org.typelevel.ci.CIStringSyntax
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.headers.Rfc8941.{IList, Parameterized, SfDict, SfInt, Token}
import run.cosy.http.headers.Rfc8941.token2PI
import run.cosy.http.headers.{Rfc8941, SigInput, SigInputs}
import scala.language.implicitConversions

import scala.collection.immutable.ListMap

class SignatureInput_Test extends munit.FunSuite:
   import `Signature-Input_Examples`.*

   // this class uses types from http4s
   case class SigInputExample(ex: `Signature-Input_Example`*):
      def headers: Headers =
        org.http4s.Headers(ex.map(se => Raw(ci"Signature-Input", se.SigInputParamsTxt)))
   end SigInputExample

   test("`§3.2` example of `Signature-Input` header") {
     // types that depend on http4s
     val sig1                                   = SigInputExample(`§3.2`("sig1"))
     val foundHeader: Option[`Signature-Input`] = sig1.headers.get[`Signature-Input`]
     val expected: Option[`Signature-Input`] =
       SigInputs(sig1.ex(0).Sig).map(`Signature-Input`.apply)
     assert(expected.nonEmpty)
     assertEquals(foundHeader, expected)
   }

   test("`§4.1` example of `Signature-Input` header") {
     val sig2                                    = SigInputExample(`§4.1`("sig1"))
     val foundHeader2: Option[`Signature-Input`] = sig2.headers.get[`Signature-Input`]
     val expected2: Option[`Signature-Input`] =
       SigInputs(sig2.ex(0).Sig).map(`Signature-Input`.apply)
     assert(expected2.nonEmpty)
     assertEquals(foundHeader2, expected2)
   }

   test("two `Signature-Input` headers with the same name. Last overwrites first.") {
     // note: the second header with the same name overwrites the first.
     // todo: check if that is a good behavior
     val sig3                                    = SigInputExample(`§3.2`("sig1"), `§4.1`("sig1"))
     val foundHeader3: Option[`Signature-Input`] = sig3.headers.get[`Signature-Input`]
     val expected3: Option[`Signature-Input`] =
       SigInputs(sig3.ex(1).Sig).map(`Signature-Input`.apply)
     assert(expected3.nonEmpty)
     assertEquals(expected3.get.values.si.size, 1)
     assertEquals(foundHeader3, expected3)
   }

   test("`Signature-Input` headers `§3.2` and `§4.1` with different names") {
     val sig4                                    = SigInputExample(`§3.2`("sig1"), `§4.1`("sig2"))
     val foundHeader4: Option[`Signature-Input`] = sig4.headers.get[`Signature-Input`]
     val expected4: Option[`Signature-Input`] =
       SigInputs(sig4.ex(0).Sig ++ sig4.ex(1).Sig)
         .map(`Signature-Input`.apply)
     assert(expected4.nonEmpty)
     assertEquals(expected4.get.values.si.size, 2)
     assertEquals(foundHeader4, expected4)
   }

   test("`Signature-Input` headers from Appendix B.2.1") {
     val sig                                    = SigInputExample(`Appendix B.2.1`("sig1"))
     val foundHeader: Option[`Signature-Input`] = sig.headers.get[`Signature-Input`]
     val expected: Option[`Signature-Input`] =
       SigInputs(sig.ex(0).Sig).map(`Signature-Input`.apply)
     assert(expected.nonEmpty)
     assertEquals(expected.get.values.si.size, 1)
     assertEquals(foundHeader, expected)
   }

end SignatureInput_Test
