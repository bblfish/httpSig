package run.cosy.http.messages

import run.cosy.http.utils.StringUtils.*

object HttpMessageDB:
   
   // the example in the spec does not have the `GET`. Added here for coherence
   val `§2.1_HeaderField` = RequestStr(
     """GET /xyz HTTP/1.1
   	  |Host: www.example.com
   	  |Date: Sat, 07 Jun 2014 20:51:35 GMT
   	  |X-OWS-Header:   Leading and trailing whitespace.   \
   	  |
   	  |X-Obs-Fold-Header: Obsolete
   	  |    line folding. \
   	  |
   	  |Cache-Control: max-age=60
   	  |X-Empty-Header: \
   	  |
   	  |Cache-Control:    must-revalidate
   	  |Example-Dict:  a=1,    b=2;x=1;y=2,   c=(a   b   c)  \
      |
      |Example-Header: value, with, lots
      |Example-Dict: d
      |Example-Header: of, commas
      |""".rfc8792single
   )
   
   val `2.2.1_Method_POST` = RequestStr(
      """POST /path?param=value HTTP/1.1
        |Host: www.example.com""".rfc8792single)
   
   val `2.2.1_Method_GET` = RequestStr(
      """GET /path?param=value HTTP/1.1
        |Host: www.example.com""".rfc8792single
   )

   val `2.2.5_GET_with_LongURL` = RequestStr(
      """GET https://test.example.com/path?param=value&param=another HTTP/1.1"""
   )

   val `2.2.5_OPTIONS_4akka` = RequestStr(
      """OPTIONS https://www.example.com:443 HTTP/1.1"""
   )

   //this example cannot be constructed in Akka
   val `2.2.5_CONNECT` = RequestStr(
      """CONNECT www.example.com:80  HTTP/1.1"""
   )

   //this example cannot be constructed in Akka
   val `2.2.5_OPTIONS` = RequestStr(
      """OPTIONS *  HTTP/1.1"""
   )
   
   val `2.2.7_Query` = RequestStr(
     """POST /path?param=value&foo=bar&baz=bat%2Dman HTTP/1.1
       |Host: www.example.com""".rfc8792single
   )

   /* Altered example from book, as it resembles others so much, and
    * we don't have an example with no Host line.
    * See last bullet point in [[https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-13.html#section-1.4 1.4. Application of HTTP Message Signatures]] */
   val `2.2.7_Query_String` = RequestStr(
      """HEAD /path?queryString HTTP/1.1"""
   )
  
   /** same as above */
   val `2.2.8_Query_Parameters` = RequestStr(
      """GET /path?param=value&foo=bar&baz=batman&qux= HTTP/1.1"""
   )

   val `2.2.9_Status_Code` = ResponseStr(
      """HTTP/1.1 200 OK
        |Date: Fri, 26 Mar 2010 00:05:00 GMT"""
   ) 


   //we cannot test @scheme  or @target-uri as those require additional info
   case class RequestStr(str: String)

   case class ResponseStr(str: String)
   
   
end HttpMessageDB
