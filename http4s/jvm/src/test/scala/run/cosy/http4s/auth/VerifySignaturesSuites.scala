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
import cats.MonadError
import cats.effect.{IO, Sync, SyncIO}
import run.cosy.http.HttpOps
import run.cosy.http.auth.TestSignatures.specRequestSigs
import run.cosy.http.auth.*
import run.cosy.http.messages.*
import run.cosy.http4s.Http4sTp
import run.cosy.http4s.Http4sTp.HT
import run.cosy.http4s.messages.{Http4sMsgInterpreter, SelectorFnsH4}

given ServerContext         = ServerContext("bblfish.net", true)
given [F[_]]: ReqFns[F, HT] = new SelectorFnsH4[F]

class H4VerifySigTests extends VerifySignatureTests[IO, HT](
      new Http4sMsgInterpreter[IO]
    ):
   override val thisPlatform: RunPlatform = RunPlatform.JVM
   val msgSig: MessageSignature[IO, HT]   = new MessageSignature[IO, HT]

   import Http4sTp.given

   given ME: cats.effect.Sync[SyncIO] = SyncIO.syncForSyncIO
   given V: bobcats.Verifier[SyncIO]  = Verifier.forSync[SyncIO]
   val signaturesDB                   = new SigSuiteHelpers[SyncIO]

   val selectorDB: ReqComponentDB[IO, HT] = ReqComponentDB(new ReqSelectors[IO, HT], HeaderIds.all)

   // needed for testing signatures

   testSignatures(specRequestSigs, msgSig.SigVerifier(selectorDB, signaturesDB.keyidFetcher))
