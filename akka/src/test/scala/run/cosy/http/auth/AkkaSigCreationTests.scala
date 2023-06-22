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

import bobcats.Verifier
import cats.effect.{IO, SyncIO}
import run.cosy.akka.http.AkkaTp
import run.cosy.akka.http.AkkaTp.HT
import run.cosy.akka.http.messages.{RequestSelectorFnsAkka, SelectorAkka}
import run.cosy.http.auth.{RunPlatform, SigSuiteHelpers, SigVerifier}
import run.cosy.http.messages.*

//todo: this double use of "using" is really not good
class AkkaSigCreationTests extends SigCreationTest[HT](
      AkkaMsgInterpreter,
      new ReqSelectors(using new RequestSelectorFnsAkka(using AServerContext("bblfish.net", true)))
    ):
   given ME: cats.effect.Sync[SyncIO] = SyncIO.syncForSyncIO

   override val thisPlatform: RunPlatform = RunPlatform.JVM

   given V: bobcats.Verifier[SyncIO] = Verifier.forSync[SyncIO]

   val selectorDB: ReqComponentDB[HT] = ReqComponentDB(new ReqSelectors[HT], HeaderIds.all)

   // needed for testing signatures

   testSignatures[IO](signingTests, SigVerifier(selectorDB, signaturesDB[IO].keyidFetcher))
