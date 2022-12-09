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

package run.cosy.http4s.auth

import bobcats.Verifier
import cats.effect.{Async, IO, kernel}
import run.cosy.http.auth.TestSignatures.specRequestSigs
import run.cosy.http.auth.{MessageSignature, RunPlatform, SigSuiteHelpers, VerifySignatureTests}
import run.cosy.http.messages.*
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT
import run.cosy.http4s.messages.{Http4sMsgInterpreter, SelectorFnsH4}

given ServerContext                         = ServerContext("bblfish.net", true)
given [F[_]]: ReqFns[F, HT]                 = new SelectorFnsH4[F]

// to get this to work, we need to first work out how to generically switch between IO and SyncIO
class H4VerifySigTests extends VerifySignatureTests[IO, HT](
      new Http4sMsgInterpreter[IO]
    ):
   override val thisPlatform: RunPlatform = RunPlatform.BrowserJS
   val msgSig: MessageSignature[IO, HT]   = new MessageSignature[IO, HT]
   import Http4sTp.given

   given ME: cats.effect.Async[IO] = IO.asyncForIO
   given V: bobcats.Verifier[IO]   = Verifier.forAsync[IO]
   val signaturesDB                = new SigSuiteHelpers[IO]

   val selectorDB: ReqComponentDB[IO, HT] = ReqComponentDB(new ReqSelectors[IO, HT], HeaderIds.all)

   testSignatures(specRequestSigs, msgSig.SigVerifier(selectorDB, signaturesDB.keyidFetcher))
