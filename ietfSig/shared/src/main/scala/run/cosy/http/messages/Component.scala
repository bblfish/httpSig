package run.cosy.http.messages

import cats.data.NonEmptyList

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

/** A selector is essentially a function for a given message component identifier that takes either
  *   - an http message (for derived components - names starting with @)
  *   - a list of headers values for the given header
  *
  * Selectors are constructed to create a signature or constructed from a received signature. They
  * can be used with messages (Request and response) to produce a component of the base to be
  * signed.
  */
sealed trait Component:
   type Msg // this is needed for the type of the returned Selector

   val name: String
   lazy val lowercaseName: String = run.cosy.platform.StringUtil.toLowerCaseInsensitive(name).nn
   def optionalParamKeys: Set[Rfc8941.Token] = Set(Component.reqTk)

   /** Create a selector with from this Component with the given parameters */
   def apply(params: Rfc8941.Params = ListMap()): Try[Selector[Msg]]

end Component

object Component:
   val keyTk  = Rfc8941.Token("key")
   val sfTk   = Rfc8941.Token("sf")
   val bsTk   = Rfc8941.Token("bs")
   val reqTk  = Rfc8941.Token("req")
   val nameTk = Rfc8941.Token("name")
end Component


trait AtComponent extends Component:

  def requiredParamKeys: Set[Rfc8941.Token] = Set.empty

  def legalParams(params: Rfc8941.Params): Boolean =
    params.keySet.removedAll(optionalParamKeys) == requiredParamKeys

  /*  This is the key function that takes a Msg and parameters an returns the base value */
  def apply(params: Rfc8941.Params = ListMap()): Try[AtSelector[Msg]] =
    if legalParams(params) then Success(mkSelector(params))
    else
      Failure(AttributeException(s"header $name does not contain legal params: $params"))

  // we can assume the parameters are valid
  protected def mkSelector(params: Rfc8941.Params): AtSelector[Msg]
end AtComponent

object HeaderComponent:
  def apply(name: String): Option[HeaderComponent] =
    if name.size > 0 && (name(0) != '@') then
      Some(new HeaderComponent(name)) // todo: check that it has a legal header syntax.
    else None

// The name cannot start with @ so a constructor needs to take care of that
class HeaderComponent private (val name: String) extends Component:
  override type Msg = NonEmptyList[String]

  override def apply(params: Rfc8941.Params = ListMap()): Try[HdrSelector] =
    Try {
      val ptk: Set[Rfc8941.Token] = params.keySet.removedAll(optionalParamKeys)
      if ptk.contains(keyTk) && Set.empty == (ptk - keyTk) then
        params(keyTk) match
          case Rfc8941.SfString(v) =>
            try DictNameSelector(name, Rfc8941.Token(v), params)
            catch
              case e: ParsingException => throw AttributeException(
                s"value of name attribute >>$v<< cannot be transformed into a Rfc8941.SfToken"
              )
          case x => throw AttributeException(
            "value of name attribute must be a Rfc8941.String. It is: " + x
          )
      else if ptk == Set(sfTk) then SfDictSelector(name, params)
      else if ptk == Set(bsTk) then BinSelector(name, params)
      else if ptk == Set.empty then RawSelector(name, params)
      else throw AttributeException("Header component can not have parameters: " + params)
    }
end HeaderComponent

sealed trait Selector[Msg]:
  def params: Rfc8941.Params
  def name: String
  lazy val lowercaseName = name.toLowerCase(java.util.Locale.US)

  /* This is the function to call on Message data */
  def signingStr(msg: Msg): Try[String]

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

/** this is good for implementations that only return one line, ie. most. a notable exception is
  * \@query-param which can return 1 or more lines
  */
trait SelectorOneLine[Msg] extends Selector[Msg]:
  /* This is the function to call on Message data */
  override def signingStr(msg: Msg): Try[String] = value(msg).map(identifier + _)

  protected def value(msg: Msg): Try[String]
end SelectorOneLine

/** Selector for components starting with @ take whole messages as parameters In the spec they are
  * called These need to be written by hand for each framework, unless the Http Message layer can be
  * more full abstracted
  */
trait AtSelector[Msg] extends Selector[Msg]

/** These selectors are generic. We should provide implementations with reasonable default values.
  * Note:
  * [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-13.html#section-7.5.7 §7.5.7 Padding attacks with multiple field values]]
  * mentions that to avoid such attacks, fields in the signature should additionally be validated
  * (after this parsing has succeeded) (todo: called it Hdr rather than Header because of name clash
  * with old code)
  */
sealed trait HdrSelector
  extends SelectorOneLine[NonEmptyList[String]]

object SfDictSelector:
  def parse(headerValue: String): Try[SfDict] =
    Rfc8941.Parser.sfDictionary.parseAll(headerValue) match
      case Left(err)   => Failure(HTTPHeaderParseException(err, headerValue))
      case Right(dict) => Success(dict)

/** Dict selector with only the "sf" paramater */
case class SfDictSelector(name: String, params: Rfc8941.Params)
  extends HdrSelector:

  override protected def value(headers: NonEmptyList[String]): Try[String] =
    val combinedHeaders = headers.toList.mkString(", ")
    SfDictSelector.parse(combinedHeaders).map(_.canon)

/** Dict selector with name param */
case class DictNameSelector(
  name: String,
  nameSelector: Rfc8941.Token,
  params: Rfc8941.Params
) extends HdrSelector:
  override protected def value(headers: NonEmptyList[String]): Try[String] =
    val combinedHeaders = headers.toList.mkString(", ")
    for
      dict <- SfDictSelector.parse(combinedHeaders)
      value <- dict.get(nameSelector)
        .toRight(SelectorException(
          s"No dictionary element >$nameSelector< in headers >$name< with value >$combinedHeaders"
        )).toTry
    yield value.canon

case class RawSelector(name: String, params: Rfc8941.Params) extends HdrSelector:
  override protected def value(headers: NonEmptyList[String]): Try[String] =
    Success(headers.map(_.trim).toList.mkString(", "))

case class BinSelector(name: String, params: Rfc8941.Params) extends HdrSelector:
  override protected def value(headers: NonEmptyList[String]): Try[String] = Try {
    headers.map(h => ArraySeq.ofByte(h.trim.nn.getBytes(US_ASCII).nn).canon)
      .toList.mkString(", ")
  }
