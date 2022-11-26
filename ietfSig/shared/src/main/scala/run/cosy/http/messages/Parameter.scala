package run.cosy.http.messages

import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.Params

object Parameters:
  
  /* all defined in 2.1
   * key to use to select an dictionary entry*/
  val keyTk = Rfc8941.Token("key")

  /** A boolean flag indicating that the component value is serialized using strict encoding of the
    * structured field value.
    */
  val sfTk = Rfc8941.Token("sf")

  /** A boolean flag indicating that individual field values are encoded using Byte Sequence data
    * structures before being combined into the component value.
    */
  val bsTk = Rfc8941.Token("bs")

  /** A boolean flag for signed responses indicating that the component value is derived from the
    * request that triggered this response message and not from the response message directly. Note
    * that this parameter can also be applied to any derived component identifiers that target the
    * request.
    */
  val reqTk = Rfc8941.Token("req")

  /** A boolean flag indicating that the field value is taken from the trailers of the message
    */
  val trTk = Rfc8941.Token("tk")

  /** see §2.2.8 he value will be the name of the keys in the query name=value pairs */
  val nameTk = Rfc8941.Token("name")
end Parameters
