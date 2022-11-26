package run.cosy.http.messages

import cats.data.NonEmptyList
import run.cosy.http.Http
import run.cosy.http.auth.{AttributeException, SelectorException}
import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.Syntax.sf
import run.cosy.http.messages.RequestSelector
import run.cosy.http.messages.RequestSelectorDB.legalHeaderComponentName
import run.cosy.http.messages.Selectors.CollationTp.Strict
import run.cosy.http.messages.Selectors.{CollationTp, Sf}

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
    knownHeaders: Map[String, CollationTp],
    selFns: HeaderSelectorFns[F, H]
):
   import Parameters.{bsTk, keyTk, nameTk, sfTk}
   import Rfc8941.SfString

   lazy val atComponentMap: Map[String, RequestSelector[F, H]] =
      import atSel.*
      List(
        `@path`,
        `@authority`,
        `@query`,
        `@scheme`,
        `@target-uri`,
        `@method`,
        `@request-target`
      ).map(rqs => rqs.lowercaseName -> rqs).toMap

   def get(id: String, params: Rfc8941.Params): Try[RequestSelector[F, H]] =
     if id.size == 0 then Failure(SelectorException("Selector Id cannot be the empty string!"))
     else if id.head == '@' then
        atComponentMap.get(id) match
           case Some(sel) => Success(sel)
           case None =>
             if id == "@query-param" then
                params.get(nameTk).collect {
                  case p: SfString => atSel.`@query-param`(p)
                }.toRight(SelectorException(
                  s"Wrong parameter for @query-param. Received: >$params<"
                )).toTry
             else
                Failure(
                  SelectorException(s"component >$id< is not valid msg component name for requests")
                )
     else
        for
          hdrId    <- legalHeaderComponentName(id)
          collTp <- interpretParams(hdrId, params)
        yield // todo: create a class for this type (used twice already)
          new RequestSel[F, H](
            hdrId,
            selFns.requestHeaders(hdrId),
            params // we pass the params as received since they have been filtered for sanity
          ):
             override def renderNel(nel: NonEmptyList[String]): Try[String] =
               Selectors.render(collTp, hdrId.asciiStr)(nel).map(identifier + _)

  //todo: this should be a map from a HeaderId type
   lazy val headerSfTps: Map[String, Sf] =
     knownHeaders.collect { case (id, CollationTp.Strict(tp)) => id -> tp }

   /** return the collation type for the this header selector id as requested by the given
     * parameters. Check with headerTypeDB if the requests are valid for intepreted types
     */
   def interpretParams(id: Rfc8941.SfString, params: Rfc8941.Params): Try[CollationTp] =
     Try {
       // 1. we translate all the parameters to well typed CollationTps and forget anything else
       val tps: Seq[CollationTp] = params.collect {
         case (`keyTk`, str: Rfc8941.SfString) => headerSfTps.get(id.asciiStr) match
              case Some(Sf.Dictionary) => CollationTp.DictSel(str)
              case _ => throw AttributeException(
                  s"we don't know that header >$id< can be interpreted as a dictionary"
                )
         case (`keyTk`, x) =>
           throw AttributeException(s"key value can only be of type SfString. Received >$x< ")
         case (`sfTk`, true) => headerSfTps.get(id.asciiStr) match
              case Some(x) => CollationTp.Strict(x)
              case None => throw AttributeException(
                  "We don't know what the agreed type for parsing header >$id< as sf is."
                )
         case (`sfTk`, x) =>
           throw AttributeException(s"value of attributed 'sf' can only be true. Received >$x<")
         case (`bsTk`, true) => CollationTp.Bin
         case (`bsTk`, x) =>
           throw AttributeException(s"value of attributed 'bs' can only be true. Received >$x<")
       }.toSeq
       // 2. now we have to detect inconsistencies and reduce
       if tps.contains(CollationTp.Bin)
       then // a. is it binary? then check if it is inconsistent, or return
          if !tps.filter(ct =>
               ct.isInstanceOf[CollationTp.DictSel] || ct.isInstanceOf[CollationTp.Strict]
             ).isEmpty
          then
             throw AttributeException(
               "We cannot have attributes 'sf' or 'key' together with 'sb' on a header component"
             )
          else CollationTp.Bin
       else // b. if not binary, then
          tps.find(_.isInstanceOf[CollationTp.DictSel]) orElse {
            tps.find(_.isInstanceOf[CollationTp.Strict])
          } getOrElse {
            CollationTp.Raw
          }
     }

end RequestSelectorDB

object RequestSelectorDB:
   /*A legal component are all lowercase Tokens. This should return
   * a type that specifically testifies to this testifies to this test having passed.
   *
   * */
   def legalHeaderComponentName(name: String): Try[Rfc8941.SfString] =
     Try {
       val id = Rfc8941.Token(name)
       if id.tk.forall(c => (!Character.isAlphabetic(c)) || Character.isLowerCase(c))
       then Rfc8941.SfString(id.tk)
       else throw SelectorException(s"header selector must be lower case token. received >$name<")
     }
