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
import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.{Params, SfString}
import cats.data.NonEmptyList
import run.cosy.http.auth.ParsingExc

import scala.collection.immutable.ListMap
import scala.util.{Success, Try}

/** These selectors can all be used to build a Signature-Input */
open class AtRequestSel[H <: Http](
    val name: AtId,
    val selectorFn: SelectorFn[Http.Request[H]],
    override val params: Rfc8941.Params = ListMap()
) extends RequestSelector[H]:
   override def renderNel(nel: NonEmptyList[String]): Either[ParsingExc, String] =
     Right(nel.map(header + _).toList.mkString("\n"))

open class AtResponseSel[H <: Http](
    val name: AtId,
    val selectorFn: SelectorFn[Http.Response[H]],
    override val params: Rfc8941.Params = ListMap()
) extends ResponseSelector[H]:
   override def renderNel(nel: NonEmptyList[String]): Either[ParsingExc, String] =
     Right(nel.map(header + _).toList.mkString("\n"))

trait AtReqSelectors[H <: Http](using sf: AtReqSelectorFns[H]):

   import AtIds.Request.*
   import Rfc8941.Syntax.*
   lazy val `@method`: AtRequestSel[H] =
     AtRequestSel[H](method, sf.method)

   lazy val `@request-target`: AtRequestSel[H] =
     AtRequestSel[H](`request-target`, sf.requestTarget)

   lazy val `@target-uri`: AtRequestSel[H] =
     AtRequestSel[H](`target-uri`, sf.targetUri)

   lazy val `@authority`: AtRequestSel[H] =
     AtRequestSel[H](`authority`, sf.authority)

   lazy val `@scheme`: AtRequestSel[H] =
     AtRequestSel[H](`scheme`, sf.scheme)

   lazy val `@path`: AtRequestSel[H] =
     AtRequestSel[H](`path`, sf.path)

   lazy val `@query`: AtRequestSel[H] =
     AtRequestSel[H](`query`, sf.query)

   /** todo: arguably the paramName should be an Rfc8941.Token, because it will be used as the key
     * in a dict, and that is a token. But then one has to be careful to render that token as a
     * string in the `"@query-param";key="q":` header
     */
   def `@query-param`(paramName: SfString): RequestSelector[H] =
     AtRequestSel[H](
       `query-param`,
       sf.queryParam(paramName),
       Params(Parameters.nameTk -> paramName)
     )

end AtReqSelectors

trait AtResSelectors[H <: Http](using sf: AtResSelectorFns[H]):

   lazy val `@status`: AtResponseSel[H] =
     AtResponseSel[H](AtIds.Response.`status`, sf.status)
