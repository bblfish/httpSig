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
import run.cosy.http.messages.AtId.*

object AtIds:
   val `method`         = AtId("@method").get
   val `request-target` = AtId("@request-target").get
   val `target-uri`     = AtId("@target-uri").get
   val `authority`      = AtId("@authority").get
   val `scheme`         = AtId("@scheme").get
   val `path`           = AtId("@path").get
   val `query`          = AtId("@query").get
   val `query-param`    = AtId("@query-param").get
   val `status`         = AtId("@status").get
end AtIds
