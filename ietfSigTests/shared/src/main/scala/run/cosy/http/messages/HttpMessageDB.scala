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
   	  |Example-Dict:  a=1,   b=2;x=1;y=2,   c=(a   b   c)  \
      |
      |Example-Header: value, with, lots
      |Example-Dict: d
      |Example-Header: of, commas
      |""".rfc8792single
   )

   val `§2.1_HeaderField_2` = RequestStr(
     """GET /xyz HTTP/1.1
        |Host: www.example.com
        |Example-Dict:  a=1,    b=2;x=1;y=2,   c=(a   b   c)  \
        |
        |X-OWS-Header:   Leading and trailing whitespace.   \
        |
        |X-Obs-Fold-Header: Obsolete
        |    line folding. \
        |
        |Date: Tue, 20 Apr 2021 02:07:56 GMT
        |X-Empty-Header: \
        |
        |Cache-Control:    must-revalidate
        |Cache-Control: max-age=60
        |Example-Header: value, with, lots
        |Example-Dict: d
        |Example-Header: of, commas
        |""".rfc8792single
   )

   val `2.2.1_Method_POST` = RequestStr(
     """POST /path?param=value HTTP/1.1
        |Host: www.example.com""".rfc8792single
   )

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

   // this example cannot be constructed in Akka
   val `2.2.5_CONNECT` = RequestStr(
     """CONNECT www.example.com:80  HTTP/1.1"""
   )

   // this example cannot be constructed in Akka
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

   val `2.4_Req_Ex` = RequestStr(
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

   /** Semantically the same as the previous one, to test akka parsing */
   val `2.4_Req_v2` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
        |Host: example.com
        |Date: Tue, 20 Apr 2021 02:07:55 GMT
        |Content-Type: application/json
        |Content-Digest: sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T\
        |   aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
        |Signature-Input: sig1=("@method" "@authority" "@path" \
        |     "content-digest" "content-length" "content-type")\
        |     ;created=1618884473;keyid="test-key-rsa-pss"
        |Content-Length: 18
        |Signature: sig1=:HIbjHC5rS0BYaa9v4QfD4193TORw7u9edguPh0AW3dMq9WImrl\
        |  FrCGUDih47vAxi4L2YRZ3XMJc1uOKk/J0ZmZ+wcta4nKIgBkKq0rM9hs3CQyxXGxH\
        |  LMCy8uqK488o+9jrptQ+xFPHK7a9sRL1IXNaagCNN3ZxJsYapFj+JXbmaI5rtAdSf\
        |  SvzPuBCh+ARHBmWuNo1UzVVdHXrl8ePL4cccqlazIJdC4QEjrF+Sn4IxBQzTZsL9y\
        |  9TP5FsZYzHvDqbInkTNigBcE9cKOYNFCn4D/WM7F6TNuZO9EgtzepLWcjTymlHzK7\
        |  aXq6Am6sfOrpIC49yXjj3ae6HRalVc/g==:
        |
        |{"hello": "world"}""".rfc8792single
   )

   val `2.4_Response` = ResponseStr(
     """HTTP/1.1 503 Service Unavailable
       |Date: Tue, 20 Apr 2021 02:07:56 GMT
       |Content-Type: application/json
       |Content-Length: 62
       |Signature-Input: reqres=("@status" "content-length" "content-type" \
       | "signature";req;key="sig1" "@authority";req "@method";req)\
       |  ;created=1618884479;keyid="test-key-ecc-p256"
       |Signature: reqres=:mh17P4TbYYBmBwsXPT4nsyVzW4Rp9Fb8WcvnfqKCQLoMvzOB\
       |  LD/n32tL/GPW6XE5GAS5bdsg1khK6lBzV1Cx/Q==:
       |
       |{"busy": true, "message": "Your call is very important to us"}""".rfc8792single
   )

   val `2.5_POST_req` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
        |Host: example.com
        |Date: Tue, 20 Apr 2021 02:07:55 GMT
        |Content-Type: application/json
        |Content-Digest: sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T\
        |  aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
        |Content-Length: 18
        |
        |{"hello": "world"}""".rfc8792single
   )

   val `3.2_POST_Signed` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
       |Host: example.com
       |Date: Tue, 20 Apr 2021 02:07:55 GMT
       |Content-type: application/json
       |Content-Digest: sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T\
       |  aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
       |Content-Length: 18
       |Signature-Input: sig1=("@method" "@authority" "@path" \
       |  "content-digest" "content-length" "content-type")\
       |  ;created=1618884473;keyid="test-key-rsa-pss"
       |Signature: sig1=:HIbjHC5rS0BYaa9v4QfD4193TORw7u9edguPh0AW3dMq9WImrl\
       |  FrCGUDih47vAxi4L2YRZ3XMJc1uOKk/J0ZmZ+wcta4nKIgBkKq0rM9hs3CQyxXGxH\
       |  LMCy8uqK488o+9jrptQ+xFPHK7a9sRL1IXNaagCNN3ZxJsYapFj+JXbmaI5rtAdSf\
       |  SvzPuBCh+ARHBmWuNo1UzVVdHXrl8ePL4cccqlazIJdC4QEjrF+Sn4IxBQzTZsL9y\
       |  9TP5FsZYzHvDqbInkTNigBcE9cKOYNFCn4D/WM7F6TNuZO9EgtzepLWcjTymlHzK7\
       |  aXq6Am6sfOrpIC49yXjj3ae6HRalVc/g==:""".rfc8792single
   )

   val `4.3_POST_Sig1` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
       |Host: example.com
       |Date: Tue, 20 Apr 2021 02:07:55 GMT
       |Content-Type: application/json
       |Content-Digest: sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T\
       |  aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
       |Content-Length: 18
       |Signature-Input: sig1=("@method" "@authority" "@path" \
       |  "content-digest" "content-length" "content-type")\
       |  ;created=1618884473;keyid="test-key-rsa-pss"
       |Signature:  sig1=:LAH8BjcfcOcLojiuOBFWn0P5keD3xAOuJRGziCLuD8r5MW9S0\
       |  RoXXLzLSRfGY/3SF8kVIkHjE13SEFdTo4Af/fJ/Pu9wheqoLVdwXyY/UkBIS1M8Br\
       |  c8IODsn5DFIrG0IrburbLi0uCc+E2ZIIb6HbUJ+o+jP58JelMTe0QE3IpWINTEzpx\
       |  jqDf5/Df+InHCAkQCTuKsamjWXUpyOT1Wkxi7YPVNOjW4MfNuTZ9HdbD2Tr65+BXe\
       |  TG9ZS/9SWuXAc+BZ8WyPz0QRz//ec3uWXd7bYYODSjRAxHqX+S1ag3LZElYyUKaAI\
       |  jZ8MGOt4gXEwCSLDv/zqxZeWLj/PDkn6w==:
       |
       |{"hello": "world"}""".rfc8792single
   )

   val `4.3_POST_With_Proxy` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
       |Host: origin.host.internal.example
       |Date: Tue, 20 Apr 2021 02:07:56 GMT
       |Content-Type: application/json
       |Content-Digest: sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T\
       |  aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
       |Content-Length: 18
       |Forwarded: for=192.0.2.123
       |Signature-Input: sig1=("@method" "@authority" "@path" \
       |    "content-digest" "content-length" "content-type")\
       |    ;created=1618884475;keyid="test-key-rsa-pss", \
       |  proxy_sig=("signature";key="sig1" "forwarded")\
       |    ;created=1618884480;expires=1618884540;keyid="test-key-rsa"\
       |    ;alg="rsa-v1_5-sha256"
       |Signature:  sig1=:LAH8BjcfcOcLojiuOBFWn0P5keD3xAOuJRGziCLuD8r5MW9S0\
       |    RoXXLzLSRfGY/3SF8kVIkHjE13SEFdTo4Af/fJ/Pu9wheqoLVdwXyY/UkBIS1M8\
       |    Brc8IODsn5DFIrG0IrburbLi0uCc+E2ZIIb6HbUJ+o+jP58JelMTe0QE3IpWINT\
       |    EzpxjqDf5/Df+InHCAkQCTuKsamjWXUpyOT1Wkxi7YPVNOjW4MfNuTZ9HdbD2Tr\
       |    65+BXeTG9ZS/9SWuXAc+BZ8WyPz0QRz//ec3uWXd7bYYODSjRAxHqX+S1ag3LZE\
       |    lYyUKaAIjZ8MGOt4gXEwCSLDv/zqxZeWLj/PDkn6w==:, \
       |  proxy_sig=:G1WLTL4/9PGSKEQbSAMypZNk+I2dpLJ6qvl2JISahlP31OO/QEUd8/\
       |    HdO2O7vYLi5k3JIiAK3UPK4U+kvJZyIUidsiXlzRI+Y2se3SGo0D8dLfhG95bKr\
       |    6ukYXl60QHpsGRTfSiwdtvYKXGpKNrMlISJYd+oGrGRyI9gbCy0aFhc6I/okIML\
       |    eK4g9PgzpC3YTwhUQ98KIBNLWHgREfBgJxjPbxFlsgJ9ykPviLj8GKJ81HwsK3X\
       |    M9P7WaS7fMGOt8h1kSqgkZQB9YqiIo+WhHvJa7iPy8QrYFKzx9BBEY6AwfStZAs\
       |    XXz3LobZseyxsYcLJLs8rY0wVA9NPsxKrHGA==:
       |
       |{"hello": "world"}""".rfc8792single
   )

   val `7.2_Response` = ResponseStr(
     """HTTP/1.1 200 OK
       |Content-Type: application/json
       |
       |{"hello": "world"}""".rfc8792single
   )

   val `7.2_ResponseWithDigest` = ResponseStr(
     """HTTP/1.1 200 OK
       |Content-Type: application/json
       |Content-Digest: \
       |  sha-256=:X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=:
       |
       |{"hello": "world"}""".rfc8792single
   )

   val `B.2_Request` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
       |Host: example.com
       |Date: Tue, 20 Apr 2021 02:07:55 GMT
       |Content-Type: application/json
       |Content-Digest: sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T\
       |  aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
       |Content-Length: 18
       |
       |{"hello": "world"}""".rfc8792single
   )
   
   val `B.2_Response` = ResponseStr(
     """HTTP/1.1 200 OK
       |Date: Tue, 20 Apr 2021 02:07:56 GMT
       |Content-Type: application/json
       |Content-Digest: sha-512=:JlEy2bfUz7WrWIjc1qV6KVLpdr/7L5/L4h7Sxvh6sN\
       |  HpDQWDCL+GauFQWcZBvVDhiyOnAQsxzZFYwi0wDH+1pw==:
       |Content-Length: 23
       |
       |{"message": "good dog"}""".rfc8792single
   )

   val `B.2_Req_sig_b21` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
        |Host: example.com
        |Date: Tue, 20 Apr 2021 02:07:55 GMT
        |Content-Type: application/json
        |Content-Digest: sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T\
        |  aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
        |Content-Length: 18
        |Signature-Input: sig-b21=();created=1618884473\
        | ;keyid="test-key-rsa-pss";nonce="b3k2pp5k7z-50gnwp.yemd"
        |Signature: sig-b21=:d2pmTvmbncD3xQm8E9ZV2828BjQWGgiwAaw5bAkgibUopem\
        | LJcWDy/lkbbHAve4cRAtx31Iq786U7it++wgGxbtRxf8Udx7zFZsckzXaJMkA7ChG\
        | 52eSkFxykJeNqsrWH5S+oxNFlD4dzVuwe8DhTSja8xxbR/Z2cOGdCbzR72rgFWhzx\
        | 2VjBqJzsPLMIQKhO4DGezXehhWwE56YCE+O6c0mKZsfxVrogUvA4HELjVKWmAvtl6\
        | UnCh8jYzuVG5WSb/QEVPnP5TmcAnLH1g+s++v6d4s8m0gCw1fV5/SITLq9mhho8K3\
        | +7EPYTU8IU1bLhdxO5Nyt8C8ssinQ98Xw9Q==:
        |
        |{"hello": "world"}""".rfc8792single
   )

   val `B.2_Req_sig_b22` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
       |Host: example.com
       |Date: Tue, 20 Apr 2021 02:07:55 GMT
       |Content-Type: application/json
       |Content-Digest: sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T\
       |  aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
       |Content-Length: 18
       |Signature-Input: sig-b22=("@authority" "content-digest" \
       |  "@query-param";name="Pet");created=1618884473\
       |  ;keyid="test-key-rsa-pss";tag="header-example"
       |Signature: sig-b22=:LjbtqUbfmvjj5C5kr1Ugj4PmLYvx9wVjZvD9GsTT4F7GrcQ\
       |  EdJzgI9qHxICagShLRiLMlAJjtq6N4CDfKtjvuJyE5qH7KT8UCMkSowOB4+ECxCmT\
       |  8rtAmj/0PIXxi0A0nxKyB09RNrCQibbUjsLS/2YyFYXEu4TRJQzRw1rLEuEfY17SA\
       |  RYhpTlaqwZVtR8NV7+4UKkjqpcAoFqWFQh62s7Cl+H2fjBSpqfZUJcsIk4N6wiKYd\
       |  4je2U/lankenQ99PZfB4jY3I5rSV2DSBVkSFsURIjYErOs0tFTQosMTAoxk//0RoK\
       |  UqiYY8Bh0aaUEb0rQl3/XaVe4bXTugEjHSw==:
       |
       |{"hello": "world"}""".rfc8792single
   )

   val `B.2_Req_sig_b23` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
        |Host: example.com
        |Signature-Input: sig-b23=("date" "@method" "@path" "@query" \
        | "@authority" "content-type" "content-digest" "content-length")\
        | ;created=1618884473;keyid="test-key-rsa-pss"
        |Signature: sig-b23=:bbN8oArOxYoyylQQUU6QYwrTuaxLwjAC9fbY2F6SVWvh0yB\
        | iMIRGOnMYwZ/5MR6fb0Kh1rIRASVxFkeGt683+qRpRRU5p2voTp768ZrCUb38K0fU\
        | xN0O0iC59DzYx8DFll5GmydPxSmme9v6ULbMFkl+V5B1TP/yPViV7KsLNmvKiLJH1\
        | pFkh/aYA2HXXZzNBXmIkoQoLd7YfW91kE9o/CCoC1xMy7JA1ipwvKvfrs65ldmlu9\
        | bpG6A9BmzhuzF8Eim5f8ui9eH8LZH896+QIF61ka39VBrohr9iyMUJpvRX2Zbhl5Z\
        | JzSRxpJyoEZAFL2FUo5fTIztsDZKEgM4cUA==:
        |Date: Tue, 20 Apr 2021 02:07:55 GMT
        |Content-Type: application/json
        |Content-Digest: sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T\
        |  aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
        |Content-Length: 18
        |
        |{"hello": "world"}""".rfc8792single
   )
   
   val `B.2_Response_sig_b24` = RequestStr(
     """HTTP/1.1 200 OK
        |Date: Tue, 20 Apr 2021 02:07:56 GMT
        |Content-Type: application/json
        |Content-Digest: sha-512=:JlEy2bfUz7WrWIjc1qV6KVLpdr/7L5/L4h7Sxvh6sN\
        |  HpDQWDCL+GauFQWcZBvVDhiyOnAQsxzZFYwi0wDH+1pw==:
        |Content-Length: 23
        |Signature-Input: sig-b24=("@status" "content-type" \
        |  "content-digest" "content-length");created=1618884473\
        |  ;keyid="test-key-ecc-p256"
        |Signature: sig-b24=:wNmSUAhwb5LxtOtOpNa6W5xj067m5hFrj0XQ4fvpaCLx0NK\
        |  ocgPquLgyahnzDnDAUy5eCdlYUEkLIj+32oiasw==:
        |
        |{"message": "good dog"}""".rfc8792single
   )

   val `B.2_Req_sig_b25` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
        |Host: example.com
        |Date: Tue, 20 Apr 2021 02:07:55 GMT
        |Content-Type: application/json
        |Content-Digest: sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T\
        |  aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
        |Content-Length: 18
        |Signature-Input: sig-b25=("date" "@authority" "content-type")\
        |  ;created=1618884473;keyid="test-shared-secret"
        |Signature: sig-b25=:pxcQw6G3AjtMBQjwo8XzkZf/bws5LelbaMk5rGIGtE8=:
        |
        |{"hello": "world"}""".rfc8792single
   )

   val `B.2_Req_sig_b26` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
        |Host: example.com
        |Date: Tue, 20 Apr 2021 02:07:55 GMT
        |Content-Type: application/json
        |Content-Digest: sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX+T\
        |  aPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
        |Content-Length: 18
        |Signature-Input: sig-b26=("date" "@method" "@path" "@authority" \
        |  "content-type" "content-length");created=1618884473\
        |  ;keyid="test-key-ed25519"
        |Signature: sig-b26=:wqcAqbmYJ2ji2glfAMaRy4gruYYnx2nEFN2HN6jrnDnQCK1\
        |  u02Gb04v9EDgwUPiu4A0w6vuQv5lIp5WPpBKRCw==:
        |
        |{"hello": "world"}""".rfc8792single
   )
   

  
   val `B.3_Client_Request` = RequestStr(
        """POST /foo?param=Value&Pet=dog HTTP/1.1
       |Host: example.com
       |Date: Tue, 20 Apr 2021 02:07:55 GMT
       |Content-Type: application/json
       |Content-Length: 18
       |
       |{"hello": "world"}""".rfc8792single
   )

   val `B.3.Proxy_enhanced` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
       |Host: service.internal.example
       |Date: Tue, 20 Apr 2021 02:07:55 GMT
       |Content-Type: application/json
       |Content-Length: 18
       |Client-Cert: :MIIBqDCCAU6gAwIBAgIBBzAKBggqhkjOPQQDAjA6MRswGQYDVQQKD\
       |  BJMZXQncyBBdXRoZW50aWNhdGUxGzAZBgNVBAMMEkxBIEludGVybWVkaWF0ZSBDQT\
       |  AeFw0yMDAxMTQyMjU1MzNaFw0yMTAxMjMyMjU1MzNaMA0xCzAJBgNVBAMMAkJDMFk\
       |  wEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8YnXXfaUgmnMtOXU/IncWalRhebrXmck\
       |  C8vdgJ1p5Be5F/3YC8OthxM4+k1M6aEAEFcGzkJiNy6J84y7uzo9M6NyMHAwCQYDV\
       |  R0TBAIwADAfBgNVHSMEGDAWgBRm3WjLa38lbEYCuiCPct0ZaSED2DAOBgNVHQ8BAf\
       |  8EBAMCBsAwEwYDVR0lBAwwCgYIKwYBBQUHAwIwHQYDVR0RAQH/BBMwEYEPYmRjQGV\
       |  4YW1wbGUuY29tMAoGCCqGSM49BAMCA0gAMEUCIBHda/r1vaL6G3VliL4/Di6YK0Q6\
       |  bMjeSkC3dFCOOB8TAiEAx/kHSB4urmiZ0NX5r5XarmPk0wmuydBVoU4hBVZ1yhk=:
       |
       |{"hello": "world"}""".rfc8792single
   )

   val `B.3.Proxy_Signed` = RequestStr(
     """POST /foo?param=Value&Pet=dog HTTP/1.1
       |Host: service.internal.example
       |Date: Tue, 20 Apr 2021 02:07:55 GMT
       |Content-Type: application/json
       |Content-Length: 18
       |Client-Cert: :MIIBqDCCAU6gAwIBAgIBBzAKBggqhkjOPQQDAjA6MRswGQYDVQQKD\
       |  BJMZXQncyBBdXRoZW50aWNhdGUxGzAZBgNVBAMMEkxBIEludGVybWVkaWF0ZSBDQT\
       |  AeFw0yMDAxMTQyMjU1MzNaFw0yMTAxMjMyMjU1MzNaMA0xCzAJBgNVBAMMAkJDMFk\
       |  wEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8YnXXfaUgmnMtOXU/IncWalRhebrXmck\
       |  C8vdgJ1p5Be5F/3YC8OthxM4+k1M6aEAEFcGzkJiNy6J84y7uzo9M6NyMHAwCQYDV\
       |  R0TBAIwADAfBgNVHSMEGDAWgBRm3WjLa38lbEYCuiCPct0ZaSED2DAOBgNVHQ8BAf\
       |  8EBAMCBsAwEwYDVR0lBAwwCgYIKwYBBQUHAwIwHQYDVR0RAQH/BBMwEYEPYmRjQGV\
       |  4YW1wbGUuY29tMAoGCCqGSM49BAMCA0gAMEUCIBHda/r1vaL6G3VliL4/Di6YK0Q6\
       |  bMjeSkC3dFCOOB8TAiEAx/kHSB4urmiZ0NX5r5XarmPk0wmuydBVoU4hBVZ1yhk=:
       |Signature-Input: ttrp=("@path" "@query" "@method" "@authority" \
       |  "client-cert");created=1618884473;keyid="test-key-ecc-p256"
       |Signature: ttrp=:xVMHVpawaAC/0SbHrKRs9i8I3eOs5RtTMGCWXm/9nvZzoHsIg6\
       |  Mce9315T6xoklyy0yzhD9ah4JHRwMLOgmizw==:
       |
       |{"hello": "world"}""".rfc8792single
   )

   val `B.4_Req` = RequestStr(
     """GET /demo?name1=Value1&Name2=value2 HTTP/1.1
       |Host: example.org
       |Date: Fri, 15 Jul 2022 14:24:55 GMT
       |Accept: application/json
       |Accept: */*
       |Signature-Input: transform=("@method" "@path" "@authority" \
       |  "accept");created=1618884473;keyid="test-key-ed25519"
       |Signature: transform=:ZT1kooQsEHpZ0I1IjCqtQppOmIqlJPeo7DHR3SoMn0s5J\
       |  Z1eRGS0A+vyYP9t/LXlh5QMFFQ6cpLt2m0pmj3NDA==:""".rfc8792single
   )

   val `B.4_Transform_1` = RequestStr(
     """GET /demo?name1=Value1&Name2=value2&param=added HTTP/1.1
       |Host: example.org
       |Date: Fri, 15 Jul 2022 14:24:55 GMT
       |Accept: application/json
       |Accept: */*
       |Accept-Language: en-US,en;q=0.5
       |Signature-Input: transform=("@method" "@path" "@authority" \
       |  "accept");created=1618884473;keyid="test-key-ed25519"
       |Signature: transform=:ZT1kooQsEHpZ0I1IjCqtQppOmIqlJPeo7DHR3SoMn0s5J\
       |  Z1eRGS0A+vyYP9t/LXlh5QMFFQ6cpLt2m0pmj3NDA==:""".rfc8792single
   )

   val `B.4_Transform_2` = RequestStr(
     """GET /demo?name1=Value1&Name2=value2 HTTP/1.1
       |Host: example.org
       |Referer: https://developer.example.org/demo
       |Accept: application/json, */*
       |Signature-Input: transform=("@method" "@path" "@authority" \
       |  "accept");created=1618884473;keyid="test-key-ed25519"
       |Signature: transform=:ZT1kooQsEHpZ0I1IjCqtQppOmIqlJPeo7DHR3SoMn0s5J\
       |  Z1eRGS0A+vyYP9t/LXlh5QMFFQ6cpLt2m0pmj3NDA==:""".rfc8792single
   )

   val `B.4_Transform_3` = RequestStr(
     """GET /demo?name1=Value1&Name2=value2 HTTP/1.1
         |Accept: application/json
         |Accept: */*
         |Date: Fri, 15 Jul 2022 14:24:55 GMT
         |Host: example.org
         |Signature-Input: transform=("@method" "@path" "@authority" \
         |  "accept");created=1618884473;keyid="test-key-ed25519"
         |Signature: transform=:ZT1kooQsEHpZ0I1IjCqtQppOmIqlJPeo7DHR3SoMn0s5J\
         |  Z1eRGS0A+vyYP9t/LXlh5QMFFQ6cpLt2m0pmj3NDA==:""".rfc8792single
   )

   val `B.4_Transform_4` = RequestStr(
     """POST /demo?name1=Value1&Name2=value2 HTTP/1.1
       |Host: example.com
       |Date: Fri, 15 Jul 2022 14:24:55 GMT
       |Accept: application/json
       |Accept: */*
       |Signature-Input: transform=("@method" "@path" "@authority" \
       |  "accept");created=1618884473;keyid="test-key-ed25519"
       |Signature: transform=:ZT1kooQsEHpZ0I1IjCqtQppOmIqlJPeo7DHR3SoMn0s5J\
       |  Z1eRGS0A+vyYP9t/LXlh5QMFFQ6cpLt2m0pmj3NDA==:""".rfc8792single
   )

   val `B.4_Transform_5` = RequestStr(
     """GET /demo?name1=Value1&Name2=value2 HTTP/1.1
       |Host: example.org
       |Date: Fri, 15 Jul 2022 14:24:55 GMT
       |Accept: */*
       |Accept: application/json
       |Signature-Input: transform=("@method" "@path" "@authority" \
       |  "accept");created=1618884473;keyid="test-key-ed25519"
       |Signature: transform=:ZT1kooQsEHpZ0I1IjCqtQppOmIqlJPeo7DHR3SoMn0s5J\
       |  Z1eRGS0A+vyYP9t/LXlh5QMFFQ6cpLt2m0pmj3NDA==:""".rfc8792single
   )

   // we cannot test @scheme  or @target-uri as those require additional info
   case class RequestStr(str: String)

   case class ResponseStr(str: String)

end HttpMessageDB
