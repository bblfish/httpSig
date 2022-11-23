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
import run.cosy.http.auth.{AttributeException, HTTPHeaderParseException, SelectorException}
import run.cosy.http.messages.Component.{bsTk, keyTk, nameTk, reqTk, sfTk}
import run.cosy.http.headers.{ParsingException, Rfc8941}
import run.cosy.http.headers.Rfc8941.SfDict
import run.cosy.http.utils.StringUtils

import java.nio.charset.StandardCharsets.US_ASCII

/** Components such as `@query`` or `content-type` represent the names of constructors for
  * selectors. The parameters provide arguments to the construction of the selectors. There are two
  * types of selectors depending on whether they select data from a request or a response.
  * Individual components determine what type of selector is returned.
  */
trait Component:

   val name: String
   lazy val lowercaseName: String = run.cosy.platform.StringUtil.toLowerCaseInsensitive(name).nn
   def optionalParamKeys: Set[Rfc8941.Token] = Set(Component.reqTk)

   /** Create a selector with from this Component with the given parameters. If the parameters are
     * not legal for this component return a Failure
     */
   def apply(params: Rfc8941.Params = ListMap()): Try[Selector]
end Component

object Component:

   /* all defined in 2.1
    * key to use to select an dictionary entry*/
   val keyTk = Rfc8941.Token("key")

   /** A boolean flag indicating that the component value is serialized using strict encoding of the
     * structured field value.
     */
   val sfTk = Rfc8941.Token("sf")

   /** A boolean flag indicating that individual field values are encoded using Byte Sequence data
     * structures before being combined into the component value.
     */
   val bsTk = Rfc8941.Token("bs")

   /** A boolean flag for signed responses indicating that the component value is derived from the
     * request that triggered this response message and not from the response message directly. Note
     * that this parameter can also be applied to any derived component identifiers that target the
     * request.
     */
   val reqTk = Rfc8941.Token("req")

   /** A boolean flag indicating that the field value is taken from the trailers of the message
     */
   val trTk = Rfc8941.Token("tk")

   /** see §2.2.8 he value will be the name of the keys in the query name=value pairs */
   val nameTk = Rfc8941.Token("name")
end Component

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
   val signingValues: Msg => Try[String | NonEmptyList[String]]
end SelectorFn

/** Selector for components starting with @ take whole messages as parameters In the spec they are
  * called These need to be written by hand for each framework, unless the Http Message layer can be
  * more full abstracted
  */

/** see [[README.md README.md]] for justification. possibly remove AtSelector intermediary */
trait RequestSelector[F[_], H <: Http] extends Selector:
   type Msg = Http.Request[F, H]

/** see [[README.md README]] for justification. possibly remove AtSelector intermediary */
trait ResponseSelector[F[_], H <: Http] extends Selector:
   type Msg = Http.Response[F, H]

/** This is the class that keeps track of the name of the selector function and its rendering to a
  * base string, that will be used in the signature
  */
trait Selector:
   type Msg

   /** params needed for this selection: such as "name" for "@query-param", Mostly empty.
     */
   def params: Rfc8941.Params = ListMap()

   def name: Rfc8941.SfString

   final lazy val lowercaseName: String = name.asciiStr.toLowerCase(java.util.Locale.US).nn
   val selectorFn: SelectorFn[Msg]

   /** this is the implementation for @query-param, only. Override on headers. */
   def renderNel(nel: NonEmptyList[String]): Try[String] =
     Success(nel.map(identifier + _).toList.mkString("\n"))

   def signingStr(msg: Msg): Try[String] =
     selectorFn.signingValues(msg).flatMap {
       case nel: NonEmptyList[String] => renderNel(nel)
       case str                       => Success(identifier + str)
     }

   def identifier: String =
      val attrs =
        if params.isEmpty then ""
        else
           params.map[String]((key, value) =>
             if value.isInstanceOf[Boolean] then key.canon
             else key.canon + "=" + value.canon
           ).mkString(";", ";", "")
      s""""$lowercaseName"$attrs: """
   end identifier

end Selector
