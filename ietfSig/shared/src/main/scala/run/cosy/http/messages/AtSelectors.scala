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

import scala.collection.immutable.ListMap
import scala.util.{Success, Try}

/** These selectors can all be used to build a Signature-Input */
open class AtRequestSel[F[_], H <: Http](
    val name: AtId,
    val selectorFn: SelectorFn[Http.Request[F, H]],
    override val params: Rfc8941.Params = ListMap()
) extends RequestSelector[F, H]:
   override def renderNel(nel: NonEmptyList[String]): Try[String] =
     Success(nel.map(identifier + _).toList.mkString("\n"))

open class AtResponseSel[F[_], H <: Http](
    val name: AtId,
    val selectorFn: SelectorFn[Http.Response[F, H]],
    override val params: Rfc8941.Params = ListMap()
) extends ResponseSelector[F, H]:
   override def renderNel(nel: NonEmptyList[String]): Try[String] =
     Success(nel.map(identifier + _).toList.mkString("\n"))

trait AtSelectors[F[_], H <: Http](using sf: AtSelectorFns[F, H]):

   import AtIds.*
   import Rfc8941.Syntax.*
   lazy val `@method`: AtRequestSel[F, H] =
     AtRequestSel[F, H](method, sf.method)

   lazy val `@request-target`: AtRequestSel[F, H] =
     AtRequestSel[F, H](`request-target`, sf.requestTarget)

   lazy val `@target-uri`: AtRequestSel[F, H] =
     AtRequestSel[F, H](`target-uri`, sf.targetUri)

   lazy val `@authority`: AtRequestSel[F, H] =
     AtRequestSel[F, H](`authority`, sf.authority)

   lazy val `@scheme`: AtRequestSel[F, H] =
     AtRequestSel[F, H](`scheme`, sf.scheme)

   lazy val `@path`: AtRequestSel[F, H] =
     AtRequestSel[F, H](`path`, sf.path)

   lazy val `@query`: AtRequestSel[F, H] =
     AtRequestSel[F, H](`query`, sf.query)

   lazy val `@status`: AtResponseSel[F, H] =
     AtResponseSel[F, H](`status`, sf.status)

   /** todo: arguably the paramName should be an Rfc8941.Token, because it will be used as the key
     * in a dict, and that is a token. But then one has to be careful to render that token as a
     * string in the `"@query-param";key="q":` header
     */
   def `@query-param`(paramName: SfString): RequestSelector[F, H] =
     AtRequestSel[F, H](
       `query-param`,
       sf.queryParam(paramName),
       Params(Parameters.nameTk -> paramName)
     )

end AtSelectors
