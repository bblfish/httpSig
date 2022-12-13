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

package run.cosy.http4s.messages

import cats.effect.{IO, SyncIO}
import munit.CatsEffectSuite
import org.http4s.Request
import org.http4s as h4
import org.typelevel.ci.CIString
import run.cosy.http.Http
import run.cosy.http.Http.Request
import run.cosy.http.auth.MessageSignature.SigningF
import run.cosy.http.auth.{MessageSignature, SigSuiteHelpers}
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.headers.SigIn.{Created, KeyId}
import run.cosy.http.headers.{ReqSigInput, Rfc8941}
import run.cosy.http.messages.*
import run.cosy.http.messages.HeaderSelectors.LS
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT
import run.cosy.http4s.messages.SelectorFnsH4

/** We need to have a few basic examples made for people who would be overwhelmingly using one
  * framework, and then need to add a signature to the header.
  */
class TestUsingHttp4SMsgs extends CatsEffectSuite:
   import run.cosy.http4s.Http4sTp.given
   val msgSig = new MessageSignature[HT](using Http4sTp.hOps)
   import msgSig.*
   import Http4sTp.{*, given}

   given ServerContext = ServerContext("bblfish.net", true)

   given ReqFns[HT] = new SelectorFnsH4
   val sel          = new ReqSelectors[HT]
   val signaturesDB = new SigSuiteHelpers[IO]

   test("build a simple http4s request and add a basic signature") {
     val req: h4.Request[IO] = h4.Request[IO](
       h4.Method.GET,
       h4.Uri.unsafeFromString("/card"),
       headers = h4.Headers(h4.headers.Accept(org.http4s.MediaType.application.`rdf+xml`))
     )
     val testkeyrsa = Rfc8941.SfString("test-key-rsa")
     signaturesDB.signerFor(testkeyrsa).flatMap { (singFn: SigningF[IO]) =>
        val newReqIO: IO[Http.Request[HT]] = req.withSigInput[IO](
          Rfc8941.Token("sig1"),
          ReqSigInput(
            sel.`@method`,
            sel.onRequest(HeaderIds.retrofit.`accept`)(LS)
          )(KeyId(sf"test-key-rsa"), Created(123)),
          singFn
        )
        newReqIO.map { newReq =>
          assert(newReq.headers.get(CIString("Signature")).isDefined, newReqIO)
        }
     }
   }

end TestUsingHttp4SMsgs
