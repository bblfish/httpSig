package run.cosy.http.auth

import run.cosy.http.auth.RunPlatform.BrowserJS
import run.cosy.http.messages.HttpMessageDB
import run.cosy.http.messages.HttpMessageDB.RequestStr

case class SignatureTest(
    msg: String,
    reqStr: RequestStr,
    sigName: String,
    created: Long,
    keyId: String,
    shouldSucceed: Boolean = true,
    unsupported: List[RunPlatform] = Nil,
    expires: Long = Long.MinValue,
    signature: String = "" // if empty then we don't know the signature and need to test
)

object SignatureTests:

  val DB = HttpMessageDB

  val specRequestSigs: List[SignatureTest] =
    List(
      SignatureTest(
        "sig using attributes, from §2.5",
        DB.`2.4_Req_Ex`,
        "sig1",
        1618884473L,
        "test-key-rsa-pss"
      ),
      SignatureTest(
        "sig using attributes, from §2.5",
        DB.`2.4_Req_v2`,
        "sig1",
        1618884473L,
        "test-key-rsa-pss"
      ),
      SignatureTest(
        "sig on POST, example from §3.2",
        DB.`3.2_POST_Signed`,
        "sig1",
        1618884473L,
        "test-key-rsa-pss"
      ),
      SignatureTest(
        "Sig on post, example from §4.3",
        DB.`4.3_POST_Sig1`,
        "sig1",
        1618884475L,
        "test-key-rsa-pss"
      ),
      // this has an expiration date to test too.
      // see https://github.com/httpwg/http-extensions/issues/2347
      //       TestSignature(
      //         "Sig on request §4.3 enhanced by proxy. Test original",
      //         DB.`4.3_POST_With_Proxy`,
      //         "sig1",
      //         1618884475L,
      //         "test-key-rsa-pss"
      //       ),
      SignatureTest(
        "Sig on request §4.3 enhanced by proxy with later valid date. Test proxy's sig.",
        DB.`4.3_POST_With_Proxy`,
        "proxy_sig",
        1618884480L,
        "test-key-rsa"
      ),
      SignatureTest(
        "test sign B.2.1 req, with nonce, signed with rsa-pss-sha512",
        DB.`B.2_Req_sig_b21`,
        "sig-b21",
        1618884473L,
        "test-key-rsa-pss"
      ),
      SignatureTest(
        "test sign B.2.2 req, with tag, signed with rsa-pss-sha512",
        DB.`B.2_Req_sig_b22`,
        "sig-b22",
        1618884473L,
        "test-key-rsa-pss"
      ),
      SignatureTest(
        "test sign B.2.3 req, full signed with rsa-pss-sha512",
        DB.`B.2_Req_sig_b23`,
        "sig-b23",
        1618884473L,
        "test-key-rsa-pss"
      ),
      SignatureTest(
        "B.2.5 signing with hmac-sha256",
        DB.`B.2_Req_sig_b25`,
        "sig-b25",
        1618884473L,
        "test-shared-secret"
      ),
      SignatureTest(
        "B.2.5 signing with ed25519",
        DB.`B.2_Req_sig_b26`,
        "sig-b26",
        1618884473L,
        "test-key-ed25519",
        unsupported = List(BrowserJS)
      ),
      SignatureTest(
        "B.3 Proxy example",
        DB.`B.3.Proxy_Signed`,
        "ttrp",
        1618884473L,
        "test-key-ecc-p256"
      )
    ) ::: {
      List(
        DB.`B.4_Req`         -> true,
        DB.`B.4_Transform_1` -> true,
        DB.`B.4_Transform_2` -> true,
        DB.`B.4_Transform_3` -> true,
        DB.`B.4_Transform_4` -> false,
        DB.`B.4_Transform_5` -> false
      ).map(req =>
        SignatureTest(
          "B.4 reordering",
          req._1,
          "transform",
          1618884473L,
          "test-key-ed25519",
          req._2,
          unsupported = List(BrowserJS)
        )
      )
    }
end SignatureTests
