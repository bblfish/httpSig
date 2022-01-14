package run.cosy.http.headers

import scala.util.control.NoStackTrace

//todo for Scala 3.1.1 one could change back to using NoStackTrace https://github.com/lampepfl/dotty/issues/13608
class RFC8941Exception(message: String) extends Throwable(message, null, true, false)

case class NumberOutOfBoundsException(num: Number) extends RFC8941Exception("num=" + num)

//mutated from akka.http.scaladsl.model.ParsingException
//todo: is the mutation good?
case class ParsingException(str: String, detail: String) extends RFC8941Exception(str)
