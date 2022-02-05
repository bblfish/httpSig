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

package run.cosy.http.headers

import scala.util.control.NoStackTrace

//todo for Scala 3.1.1 one could change back to using NoStackTrace https://github.com/lampepfl/dotty/issues/13608
class RFC8941Exception(message: String) extends Throwable(message, null, true, false)

case class NumberOutOfBoundsException(num: Number) extends RFC8941Exception("num=" + num)

//mutated from akka.http.scaladsl.model.ParsingException
//todo: is the mutation good?
case class ParsingException(str: String, detail: String) extends RFC8941Exception(str)
