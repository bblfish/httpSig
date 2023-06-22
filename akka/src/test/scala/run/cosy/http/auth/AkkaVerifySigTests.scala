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

import cats.effect.{IO, Sync, SyncIO}
import cats.{Id, MonadError}
import bobcats.Verifier
import run.cosy.akka.http.AkkaTp
import run.cosy.akka.http.AkkaTp.HT
import run.cosy.http.auth.SignatureTests.specRequestSigs
import run.cosy.http.auth.VerifySignatureTests
import run.cosy.http.messages.*
import run.cosy.akka.http.messages.RequestSelectorFnsAkka

given AServerContext = AServerContext("bblfish.net", true)
given ReqFns[HT]     = new RequestSelectorFnsAkka
import scala.concurrent.Future

class AkkaVerifySigTests extends VerifySignatureTests[HT](AkkaMsgInterpreter):
   override val thisPlatform: RunPlatform = RunPlatform.JVM
   val msgSig: MessageSignature[HT]       = new MessageSignature[HT]

   import AkkaTp.given

   given ME: cats.effect.Sync[SyncIO] = SyncIO.syncForSyncIO
   given V: bobcats.Verifier[SyncIO]  = Verifier.forSync[SyncIO]
   val signaturesDB                   = new SigSuiteHelpers[SyncIO]

   val selectorDB: ReqComponentDB[HT] = ReqComponentDB(new ReqSelectors[HT], HeaderIds.all)

   testSignatures[SyncIO](
     specRequestSigs,
     SigVerifier(selectorDB, signaturesDB.keyidFetcher)
   )
