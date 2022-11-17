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

import run.cosy.http.Http
import run.cosy.http.Http.*

trait AtComponents[F[_], H <: Http]:
   trait OnRequest extends AtComponent:
      override type Msg = Http.Request[F, H]

   trait OnResponse extends AtComponent:
      override type Msg = Http.Response[F, H]

   def `@method`: OnRequest

   def `@request-target`: OnRequest

   def `@target-uri`(using ServerContext): OnRequest

   def `@authority`(using sc: ServerContext): OnRequest

   def `@scheme`(using sc: ServerContext): OnRequest

   def `@path`: OnRequest

   def `@query`: OnRequest

   def `@query-param`: OnRequest

   def `@status`: OnResponse
