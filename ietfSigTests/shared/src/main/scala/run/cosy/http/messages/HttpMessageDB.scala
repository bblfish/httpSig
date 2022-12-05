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
   
   /** Semantically the same as the previous one, to test akka parsing  */
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
       |  ;created=1618884475;keyid="test-key-rsa-pss"
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
      |  "content-digest" "content-length" "content-type")\
      |  ;created=1618884475;keyid="test-key-rsa-pss"
      |Signature:  sig1=:LAH8BjcfcOcLojiuOBFWn0P5keD3xAOuJRGziCLuD8r5MW9S0\
      |  RoXXLzLSRfGY/3SF8kVIkHjE13SEFdTo4Af/fJ/Pu9wheqoLVdwXyY/UkBIS1M8Br\
      |  c8IODsn5DFIrG0IrburbLi0uCc+E2ZIIb6HbUJ+o+jP58JelMTe0QE3IpWINTEzpx\
      |  jqDf5/Df+InHCAkQCTuKsamjWXUpyOT1Wkxi7YPVNOjW4MfNuTZ9HdbD2Tr65+BXe\
      |  TG9ZS/9SWuXAc+BZ8WyPz0QRz//ec3uWXd7bYYODSjRAxHqX+S1ag3LZElYyUKaAI\
      |  jZ8MGOt4gXEwCSLDv/zqxZeWLj/PDkn6w==:
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
   
   
   

   //we cannot test @scheme  or @target-uri as those require additional info
   case class RequestStr(str: String)

   case class ResponseStr(str: String)
   
   
end HttpMessageDB
