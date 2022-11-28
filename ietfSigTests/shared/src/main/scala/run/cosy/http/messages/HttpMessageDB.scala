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
   
   val `2.5_Post_Ex` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
       |Host: example.com
       |Date: Tue, 20 Apr 2021 02:07:55 GMT
       |Content-Type: application/json
       |Content-Digest: sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T\
       |   aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
       |Content-Length: 18
       |Signature-Input: sig1=("@method" "@authority" "@path" \
       |     "content-digest" "content-length" "content-type")\
       |     ;created=1618884473;keyid="test-key-rsa-pss"
       |Signature: sig1=:HIbjHC5rS0BYaa9v4QfD4193TORw7u9edguPh0AW3dMq9WImrl\
       |  FrCGUDih47vAxi4L2YRZ3XMJc1uOKk/J0ZmZ+wcta4nKIgBkKq0rM9hs3CQyxXGxH\
       |  LMCy8uqK488o+9jrptQ+xFPHK7a9sRL1IXNaagCNN3ZxJsYapFj+JXbmaI5rtAdSf\
       |  SvzPuBCh+ARHBmWuNo1UzVVdHXrl8ePL4cccqlazIJdC4QEjrF+Sn4IxBQzTZsL9y\
       |  9TP5FsZYzHvDqbInkTNigBcE9cKOYNFCn4D/WM7F6TNuZO9EgtzepLWcjTymlHzK7\
       |  aXq6Am6sfOrpIC49yXjj3ae6HRalVc/g==:
       |
       |{"hello": "world"}""".rfc8792single
   )


   //we cannot test @scheme  or @target-uri as those require additional info
   case class RequestStr(str: String)

   case class ResponseStr(str: String)
   
   
end HttpMessageDB
