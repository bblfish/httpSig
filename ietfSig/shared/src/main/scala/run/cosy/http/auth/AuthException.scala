package run.cosy.http.auth

import run.cosy.http.headers.RFC8941Exception


//used to be ResponseSummary(on: Uri, code: StatusCode, header: Seq[HttpHeader], respTp: ContentType)
//but it would be complicated to adapt for akka and http4s types
case class ResponseSummary(onUri: String, code: String, header: Seq[String], respTp: String)


class AuthExc(msg: String) extends Throwable(msg, null, true, false)
case class CryptoException(msg: String) extends AuthExc(msg)
case class AuthException(response: ResponseSummary, msg: String) extends AuthExc(msg)
case class InvalidCreatedFieldException(msg: String) extends AuthExc(msg)
case class InvalidExpiresFieldException(msg: String) extends AuthExc(msg)
case class UnableToCreateSigHeaderException(msg: String) extends AuthExc(msg)
case class SelectorException(msg: String) extends AuthExc(msg)
case class InvalidSigException(msg: String) extends AuthExc(msg)
case class KeyIdException(msg: String) extends AuthExc(msg)

case class HTTPHeaderParseException(error: cats.parse.Parser.Error, httpHeader: String) extends AuthExc(httpHeader)
