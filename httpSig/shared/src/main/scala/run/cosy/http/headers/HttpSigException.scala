package run.cosy.http.headers

import scala.util.control.NoStackTrace

trait HttpSigException extends java.lang.RuntimeException with NoStackTrace with Product with Serializable

case class NumberOutOfBoundsException(num: Number) extends HttpSigException

//mutated from akka.http.scaladsl.model.ParsingException
//todo: is the mutation good?
case class ParsingException(str: String, detail: String) extends HttpSigException
