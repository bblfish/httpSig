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

import org.http4s.{Request, Headers}
import org.http4s.headers.Host

//odds and ends for tests when things don't seem to work
class Http4sTests extends munit.FunSuite:

   test("Host header selection") {
     val req = Request(headers =
       Headers(
         "Host"          -> "www.example.com",
         "Date"          -> "Sat, 07 Jun 2014 20:51:35 GMT",
         "Cache-Control" -> "max-age=60",
         "Cache-Control" -> "   must-revalidate"
       )
     )
     assertEquals(req.headers.get[Host], Some(Host("www.example.com")))
   }
