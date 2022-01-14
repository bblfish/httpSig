package run.cosy.http.headers

import scala.util.control.NoStackTrace

//todo for Scala 3.1.1 one could change back to using NoStackTrace https://github.com/lampepfl/dotty/issues/13608
class HttpSigException(message: String) extends Throwable(message, null, true, false)

case class NumberOutOfBoundsException(num: Number) extends HttpSigException("num=" + num)

//mutated from akka.http.scaladsl.model.ParsingException
//todo: is the mutation good?
case class ParsingException(str: String, detail: String) extends HttpSigException(str)

//used to be ResponseSummary(on: Uri, code: StatusCode, header: Seq[HttpHeader], respTp: ContentType)
//but it would be complicated to adapt for akka and http4s types
case class ResponseSummary(onUri: String, code: String, header: Seq[String], respTp: String)

class AuthExc(msg: String) extends HttpSigException(msg)
case class CryptoException(msg: String) extends AuthExc(msg)
case class AuthException(response: ResponseSummary, msg: String) extends AuthExc(msg)
case class InvalidCreatedFieldException(msg: String) extends AuthExc(msg)
case class InvalidExpiresFieldException(msg: String) extends AuthExc(msg)
case class UnableToCreateSigHeaderException(msg: String) extends AuthExc(msg)
case class SelectorException(msg: String) extends AuthExc(msg)
case class InvalidSigException(msg: String) extends AuthExc(msg)

case class HTTPHeaderParseException(error: cats.parse.Parser.Error, httpHeader: String) extends HttpSigException(httpHeader)
