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

import cats.data.NonEmptyList
import run.cosy.http.Http

import scala.util.Try
import scala.collection.immutable.{ArraySeq, ListMap}
import scala.util.{Failure, Success, Try}
import run.cosy.http.headers.Rfc8941.Serialise.given
import run.cosy.http.auth.{
  AttributeException,
  HTTPHeaderParseException,
  ParsingExc,
  SelectorException
}
import run.cosy.http.messages.Parameters.{bsTk, keyTk, nameTk, reqTk, sfTk}
import run.cosy.http.headers.{ParsingException, Rfc8941}
import run.cosy.http.headers.Rfc8941.SfDict
import run.cosy.http.utils.StringUtils

import java.nio.charset.StandardCharsets.US_ASCII

/** A container for a function that selects in a Msg (Request or Response) the data and returns a
  * sequence of Signing Values
  */
trait SelectorFn[Msg]:

   /*
    * This is the function to call on Message data
    * The result is a NonEmptyList when more than one value can be given
    *  - In the case of query-param these will result in multiple lines of results
    *  - In the case of normal headers these results can then be put put together in any number
    * of ways.
    * The result is a String for simple methods such as @query
    * */
   val signingValues: Msg => Either[ParsingExc, String | NonEmptyList[String]]
end SelectorFn

/** Selector for components starting with @ take whole messages as parameters In the spec they are
  * called These need to be written by hand for each framework, unless the Http Message layer can be
  * more full abstracted
  */

/** see [[README.md README.md]] for justification. */
trait RequestSelector[H <: Http] extends Selector:
   type Msg = Http.Request[H]

/** see [[README.md README]] for justification. */
trait ResponseSelector[H <: Http] extends Selector:
   type Msg = Http.Response[H]

/** This is the class that keeps track of the name of the selector function and its rendering to a
  * base string, that will be used in the signature
  */
trait Selector:
   type Msg

   /** params needed for this selection: such as "name" for "@query-param", Mostly empty.
     */
   def params: Rfc8941.Params = ListMap()

   /** todo: This should be a subtype of SfString that is limited to the vocab of an optional `@`
     * char followed by a lowercase rfc8941.Token. The canonical representation of it is surrounded
     * by quotes " as with SfString. The reason it is a token is that that is what the structure of
     * an http header is.
     */
   def name: ComponentId

   val selectorFn: SelectorFn[Msg]

   /** this is the implementation for @query-param, only. Override on headers. */
   def renderNel(nel: NonEmptyList[String]): Either[ParsingExc, String]

   def signingStr(msg: Msg): Either[ParsingExc, String] =
     selectorFn.signingValues(msg).flatMap {
       case nel: NonEmptyList[String] => renderNel(nel)
       case str                       => Right(header + str)
     }

   def header: String = identifier + ": "
   def identifier: String =
      val attrs =
        if params.isEmpty then ""
        else
           params.map[String]((key, value) =>
             if value.isInstanceOf[Boolean] then key.canon
             else key.canon + "=" + value.canon
           ).mkString(";", ";", "")
      s"""${name.canon}$attrs"""
   end identifier

end Selector
