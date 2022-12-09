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
import run.cosy.http.auth.{AttributeException, MessageSignature, ParsingExc, SelectorException}
import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.messages.HeaderId.SfHeaderId

import scala.collection.immutable
import scala.util.{Failure, Success, Try}

/** DB matching component names to selectors At selectors are hard coded, whereas header selectors
  * are more flexible, depending on the type of the syntax of a header's values
  * @param reqSel:
  *   DB of Message Selectors
  * @param headerTypeDB
  *   Database mapping header ids to the recognised way of encoding that header
  */
case class ReqComponentDB[FH[_], H <: Http](
    reqSel: ReqSelectors[FH, H],
    knownIds: Seq[HeaderId]
):
   import Parameters.{bsTk, keyTk, nameTk, sfTk}
   import Rfc8941.SfString

   def addIds(ids: HeaderId*) = ReqComponentDB(reqSel, knownIds ++ ids)

   lazy val atComponentMap: Map[AtId, RequestSelector[FH, H]] =
      import reqSel.*
      List(
        `@path`,
        `@authority`,
        `@query`,
        `@scheme`,
        `@target-uri`,
        `@method`,
        `@request-target`
      ).map(rqs => rqs.name -> rqs).toMap

   lazy val componentIds: Map[String, ComponentId] =
     (AtIds.Request.all ++ knownIds).map(id => id.specName -> id).toMap

   // we get this info from the Signing-String header
   def get(id: String, params: Rfc8941.Params): Either[ParsingExc, RequestSelector[FH, H]] =
     componentIds.get(id) match
        case Some(at: AtId) => atComponentMap.get(at) match
             case Some(sel) => Right(sel)
             case None =>
               if at == AtIds.Request.`query-param` then
                  params.get(nameTk).collect {
                    case p: SfString => reqSel.`@query-param`(p)
                  }.toRight(SelectorException(
                    s"Wrong parameter for @query-param. Received: >$params<"
                  ))
               else
                  Left(
                    SelectorException(
                      s"component >$id< is not valid msg component name for requests"
                    )
                  )
        case Some(hdrId: HeaderId) =>
          for
             collTp <- ReqComponentDB.interpretParams(hdrId, params)
          yield reqSel.onRequest(hdrId)(collTp)
        case None => Left(SelectorException(s"we don't recognised component >$id<"))

end ReqComponentDB

object ReqComponentDB:
   import Parameters.{bsTk, keyTk, nameTk, sfTk}
   import run.cosy.http.messages.HeaderSelectors as sel

   /** return the collation type for the this header selector id as requested by the given
     * parameters. Check with headerTypeDB if the requests are valid for intepreted types this
     * ignores parameters it does not understand. May not be the right behavior. todo: check
     */
   def interpretParams(
       id: HeaderId,
       params: Rfc8941.Params
   ): Either[AttributeException, id.AllowedCollation] =
     try
        // 1. we translate all the parameters to well typed CollationTps and forget anything else
        val tps: Seq[sel.CollationTp] = params.collect {
          case (`keyTk`, str: Rfc8941.SfString) => id match
               case sfId: SfHeaderId if sfId.format == sel.SelFormat.Dictionary =>
                 sel.Dict(str)
               case _ => throw AttributeException(
                   s"we don't know that header >$id< can be interpreted as a dictionary"
                 )
          case (`keyTk`, x) =>
            throw AttributeException(s"key value can only be of type SfString. Received >$x< ")
          case (`sfTk`, true) => id match
               case sfId: SfHeaderId => sel.SF
               case _ => throw AttributeException(
                   s"We don't know what the agreed type for parsing header >$id< as sf is."
                 )
          case (`sfTk`, x) =>
            throw AttributeException(s"value of attributed 'sf' can only be true. Received >$x<")
          case (`bsTk`, true) => sel.BS
          case (`bsTk`, x) =>
            throw AttributeException(s"value of attributed 'bs' can only be true. Received >$x<")
        }.toSeq
        // 2. now we have to detect inconsistencies and reduce
        val ct =
          if tps.contains(sel.BS)
          then // a. is it binary? then check if it is inconsistent, or return
             if tps.exists(ct =>
                  ct.isInstanceOf[sel.Dict] || ct == sel.SF
                )
             then
                throw AttributeException(
                  "We cannot have attributes 'sf' or 'key' together with 'sb' on a header component"
                )
             else sel.BS
          else // b. if not binary, then
             tps.find(_.isInstanceOf[sel.Dict]) orElse {
               tps.find(_ == sel.SF)
             } getOrElse {
               sel.LS
             }
        // todo: find a way of not using xx_instnaceOf methods
        Right(ct.asInstanceOf[id.AllowedCollation])
     catch case e: AttributeException => Left(e)
