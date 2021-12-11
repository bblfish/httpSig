package run.cosy.http.headers

import scala.util.control.NoStackTrace

trait HttpSigException extends java.lang.RuntimeException with NoStackTrace with Product with Serializable

case class NumberOutOfBoundsException(num: Number) extends HttpSigException

//mutated from akka.http.scaladsl.model.ParsingException
//todo: is the mutation good?
case class ParsingException(str: String, detail: String) extends HttpSigException

//used to be ResponseSummary(on: Uri, code: StatusCode, header: Seq[HttpHeader], respTp: ContentType)
//but it would be complicated to adapt for akka and http4s types
case class ResponseSummary(onUri: String, code: String, header: Seq[String], respTp: String)

trait AuthExc extends HttpSigException
case class CryptoException(msg: String) extends AuthExc
case class AuthException(response: ResponseSummary, msg: String) extends AuthExc
case class InvalidCreatedFieldException(msg: String) extends AuthExc
case class InvalidExpiresFieldException(msg: String) extends AuthExc
case class UnableToCreateSigHeaderException(msg: String) extends AuthExc
case class InvalidSigException(msg: String) extends AuthExc

case class HTTPHeaderParseException(error: cats.parse.Parser.Error, httpHeader: String) extends HttpSigException
