package run.cosy.http.message

import run.cosy.http.utils.StringUtils.*
import bobcats.HttpMessageSignaturesV13

/** This class collects the data for each example from the spec including
  *
  *   - Request and Response HTTP messages (where needed)
  *   - Signature base that is generated from that
  *   - keys that are needed to create and verify the signature
  *
  * The second and third are taken from the verified bobcats test suite which also contains data
  * such as limitations of keys for certain platforms.
  */
class HttpSigningDataV13:
   type HttpMessage = String

   object `2.4_Request_Response_Signature_Binding`:
      val messageReq: HttpMessage = """\
        |POST /foo?param=Value&Pet=dog HTTP/1.1
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
        |{"hello": "world"}\
        |""".rfc8792single

      val messageResp: HttpMessage = """\
        |HTTP/1.1 503 Service Unavailable
        |Date: Tue, 20 Apr 2021 02:07:56 GMT
        |Content-Type: application/json
        |Content-Length: 62
        |
        |{"busy": true, "message": "Your call is very important to us"}\
        """.rfc8792single

      val signature = bobcats.HttpMessageSignaturesV13.`§2.4_Example`

   end `2.4_Request_Response_Signature_Binding`

end HttpSigningDataV13
