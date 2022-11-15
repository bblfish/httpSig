package run.cosy.http.message

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
   	  |Example-Dict:  a=1,    b=2;x=1;y=2,   c=(a   b   c)""".rfc8792single
   )
   val `2.2.1_Method` = RequestStr("""\
        |POST /path?param=value HTTP/1.1
        |Host: www.example.com""".rfc8792single)

   case class RequestStr(str: String)
