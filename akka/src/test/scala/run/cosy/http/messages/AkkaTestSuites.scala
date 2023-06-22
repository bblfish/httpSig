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

import cats.Id
import run.cosy.akka.http.AkkaTp
import run.cosy.akka.http.AkkaTp.HT
import run.cosy.akka.http.messages.RequestSelectorFnsAkka

given AServerContext             = AServerContext("bblfish.net", true)
given TestHttpMsgInterpreter[HT] = AkkaMsgInterpreter //this needs the above

given ReqFns[HT] = new RequestSelectorFnsAkka

class AkkaHeaderSelectorSuite extends HeaderSuite(
      new ReqSelectors[HT]
    )

class AkkaAtRequestSelectorSuite extends AtRequestSelectorSuite[HT]:
   def sel(sc: ServerContext): AtReqSelectors[HT] =
     new AtReqSelectors[HT](using new RequestSelectorFnsAkka(using sc)) {}
   def interp: TestHttpMsgInterpreter[HT] = AkkaMsgInterpreter
   def platform: HttpMsgPlatform          = HttpMsgPlatform.Akka

class AkkaReqSigSuite extends SigInputReqSuite[HT](
      new ReqComponentDB(
        new ReqSelectors[HT],
        HeaderIds.all
      )
    )

class AkkaStaticSigInputReqSuite extends VerifyBaseOnRequests[HT](
      new ReqSelectors[HT]
    )
