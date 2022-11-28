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
import run.cosy.http.auth.{AttributeException, SelectorException}
import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.messages.HeaderId.SfHeaderId
import run.cosy.http.messages.RequestSelector
import run.cosy.http.messages.Selectors.Strict
import run.cosy.http.messages.Selectors.{CollationTp, SelFormat}

import scala.collection.immutable
import scala.util.{Failure, Success, Try}

/** DB of Selectors for requests. At selectors are hard coded, but header selectors are parsed as
  * per client request
  * @param atSel:
  *   DB of Message Selectors
  * @param headerTypeDB
  *   Database mapping header ids to the recognised way of encoding that header
  */
class RequestSelectorDB[F[_], H <: Http](
    atSel: AtSelectors[F, H],
    knownIds: Seq[HeaderId],
    selFns: HeaderSelectorFns[F, H]
):
   import Parameters.{bsTk, keyTk, nameTk, sfTk}
   import Rfc8941.SfString

   lazy val atComponentMap: Map[AtId, RequestSelector[F, H]] =
      import atSel.*
      List(
        `@path`,
        `@authority`,
        `@query`,
        `@scheme`,
        `@target-uri`,
        `@method`,
        `@request-target`
      ).map(rqs => rqs.name -> rqs).toMap

   // todo: this should be a map from a HeaderId type
   lazy val componentIds: Map[String, ComponentId] =
     (AtIds.requestIds.toSeq ++ knownIds).map(id => id.specName -> id).toMap

   // we get this info from the Signing-String header
   def get(id: String, params: Rfc8941.Params): Try[RequestSelector[F, H]] =
     componentIds.get(id) match
        case Some(at: AtId) => atComponentMap.get(at) match
             case Some(sel) => Success(sel)
             case None =>
               if at == AtIds.`query-param` then
                  params.get(nameTk).collect {
                    case p: SfString => atSel.`@query-param`(p)
                  }.toRight(SelectorException(
                    s"Wrong parameter for @query-param. Received: >$params<"
                  )).toTry
               else
                  Failure(
                    SelectorException(
                      s"component >$id< is not valid msg component name for requests"
                    )
                  )
        case Some(hdrId: HeaderId) =>
          for
             collTp <- interpretParams(hdrId, params)
          yield // todo: create a class for this type (used twice already)
            new RequestHeaderSel[F, H](
              hdrId,
              collTp,
              selFns.requestHeaders(hdrId),
              params // we pass the params as received since they have been filtered for sanity
            )
        case None => Failure(SelectorException(s"we don't recognised component >$id<"))

   /** return the collation type for the this header selector id as requested by the given
     * parameters. Check with headerTypeDB if the requests are valid for intepreted types this
     * ignores parameters it does not understand. May not be the right behavior. todo: check
     */
   def interpretParams(id: HeaderId, params: Rfc8941.Params): Try[id.AllowedCollation] =
     Try {
       // 1. we translate all the parameters to well typed CollationTps and forget anything else
       val tps: Seq[CollationTp] = params.collect {
         case (`keyTk`, str: Rfc8941.SfString) => id match
              case sfId: SfHeaderId if sfId.format == SelFormat.Dictionary => Selectors.DictSel(str)
              case _ => throw AttributeException(
                  s"we don't know that header >$id< can be interpreted as a dictionary"
                )
         case (`keyTk`, x) =>
           throw AttributeException(s"key value can only be of type SfString. Received >$x< ")
         case (`sfTk`, true) => id match
              case sfId: SfHeaderId => Selectors.Strict
              case _ => throw AttributeException(
                  "We don't know what the agreed type for parsing header >$id< as sf is."
                )
         case (`sfTk`, x) =>
           throw AttributeException(s"value of attributed 'sf' can only be true. Received >$x<")
         case (`bsTk`, true) => Selectors.Bin
         case (`bsTk`, x) =>
           throw AttributeException(s"value of attributed 'bs' can only be true. Received >$x<")
       }.toSeq
       // 2. now we have to detect inconsistencies and reduce
       val ct =
         if tps.contains(Selectors.Bin)
         then // a. is it binary? then check if it is inconsistent, or return
            if tps.exists(ct =>
                 ct.isInstanceOf[Selectors.DictSel] || ct == Selectors.Strict
               )
            then
               throw AttributeException(
                 "We cannot have attributes 'sf' or 'key' together with 'sb' on a header component"
               )
            else Selectors.Bin
         else // b. if not binary, then
            tps.find(_.isInstanceOf[Selectors.DictSel]) orElse {
              tps.find(_ == Selectors.Strict)
            } getOrElse {
              Selectors.Raw
            }
       // todo: find a way of not using xx_instnaceOf methods
       ct.asInstanceOf[id.AllowedCollation]
     }

end RequestSelectorDB
