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

import run.cosy.http.messages.{HeaderSuite, ServerContext}

class H4HeaderSuite[F[_]] extends HeaderSuite(
      // todo: no need for server context here in a header suite. use HeaderSelectorFns instead...
      new run.cosy.http.messages.Selectors(
        using new SelectorFnsH4(using ServerContext("bblfish.net", true))
      ),
      new Http4sMsgInterpreter[F]
    )