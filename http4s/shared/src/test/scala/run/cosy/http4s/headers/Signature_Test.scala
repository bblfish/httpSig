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
import org.typelevel.ci.CIStringSyntax
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.headers.Rfc8941.{IList, Parameterized, SfDict, SfInt, Token, token2PI}
import run.cosy.http.headers.{Rfc8941, SigInput, SigInputs, Signatures}
import run.cosy.http.utils.StringUtils.rfc8792single
import run.cosy.http4s.headers.`Signature-Input`.{*, given}
import org.typelevel.ci.*

import scala.language.implicitConversions

class Signature_Test extends munit.FunSuite:

   import `Signature_Examples`.*
   import `Signature-Input_Examples` as SigInpEx

   case class SigExample(ex: Signature_Example*):
      def headers: Headers = org.http4s.Headers(ex.map(se => Raw(ci"Signature", se.SigString)))
   end SigExample

   for
      ex <- Seq(
        `§3.2`("sig1"),
        `§3.2`("sig1", 3, 1, 2),
        `§3.2`("sig1", 4, 0, 7),
        `§3.2`("sig1", 4, 5),
        `§4.3_second`("sig1"),
        `§4.3_second`("sig1", 2, 5),
        `§4.3_second`("sig1", 4, 7, 0, 1)
      )
   do
      test(s"${ex.name} `Signature` header with ${ex.lspace} ${ex.rspace} spaces around sig" +
        s" and  ${ex.`before=space`} ${ex.`after=space`} on each side of = " +
        s" (is${if ex.illegalSpace then " not " else " "}legal )") {
        val sig1                     = SigExample(ex)
        val found: Option[Signature] = sig1.headers.get[Signature]

        if ex.illegalSpace then assert(found.isEmpty)
        else
           val expected: Option[Signatures] = Signatures(sig1.ex(0).Signature)
           assertEquals(found, expected.map(s => new Signature(s)))
      }

   for
      (ex1, ex2) <- Seq(`§3.2`("sig1"), `§3.2`("sig1", 3, 1), `§3.2`("sig1", 4, 0, 7))
        .zip(Seq(
          `§4.3_second`("sig2", 4, 5),
          `§4.3_second`("sig2"),
          `§4.3_second`("sig2", 2, 5, 0, 5)
        ))
   do
      test(
        s"${ex1.name} spaced ${ex1.lspace}-${ex1.rspace} and ${ex1.`before=space`}=${ex1.`after=space`}" +
          s" with ${ex2.name} spaced ${ex2.lspace}-${ex2.rspace} and ${ex2.`before=space`}=${ex2.`after=space`}"
      ) {
        val sigs                     = SigExample(ex1, ex2)
        val found: Option[Signature] = sigs.headers.get[Signature]
        if ex1.illegalSpace || ex2.illegalSpace then
           assert(found.isEmpty && ex2 == `§4.3_second`("sig2", 2, 5, 0, 5))
        else
           assertEquals(
             found.get.signatures.sigmap.values.size,
             2
           ) // when it fails it fails on both
           val expected1: Option[Signatures] = Signatures(sigs.ex(0).Signature)
           val expected2: Option[Signatures] = Signatures(sigs.ex(1).Signature)
           val combinedExpected              = expected1.get.append(expected2.get)
           assertEquals(found, Some(new Signature(combinedExpected)))
           Headers(
             Raw(ci"Signature-Input", SigInpEx.`§3.2`("sig1").SigInputParamsTxt),
             Raw(ci"Signature", ex1.SigString + ", " + ex2.SigString),
             Raw(ci"Signature-Input", SigInpEx.`§4.1`("sig2").SigInputParamsTxt)
           ).get[Signature] match
              case Some(sig) => assertEquals(sig, new Signature(combinedExpected))
              case None      => fail("Signature header missing")
      }

end Signature_Test
