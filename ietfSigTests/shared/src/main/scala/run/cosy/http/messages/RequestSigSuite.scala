package run.cosy.http.messages

import bobcats.Verifier.SigningString
import munit.CatsEffectSuite
import run.cosy.http.Http.Request
import run.cosy.http.auth.MessageSignature
import run.cosy.http.headers.SigInput
import run.cosy.http.utils.StringUtils.*
import run.cosy.http.headers.Rfc8941
import run.cosy.http.{Http, HttpOps}

import scala.util.{Try, Success, Failure}
import Rfc8941.Serialise.given

open class RequestSigSuite[F[_], H <: Http](
    msgSig: MessageSignature[F, H],
    msgDB: HttpMsgInterpreter[F, H]
)(using RequestSelectorDB[F, H]) extends CatsEffectSuite:

   import msgSig.{given, *}

   test("Test SigInput §2.5") {
     val req: Http.Request[F, H] = msgDB.asRequest(HttpMessageDB.`2.5_Post_Ex`)

     val sigInput25: Option[SigInput] = SigInput(
       """("@method" "@authority" "@path" \
        |"content-digest" "content-length" "content-type")\
        |;created=1618884473;keyid="test-key-rsa-pss"""".rfc8792single
     )
 
     val x: Try[SigningString] = req.signingStr(sigInput25.get)
     assertEquals(
       x.flatMap(s => s.decodeAscii.toTry),
       Success(
         """"@method": POST
           |"@authority": example.com
           |"@path": /foo
           |"content-digest": sha-512=:WZDPaVn/7XgHaAy8pmojAkGWoRx2UFChF41A2svX\
           |  +TaPm+AbwAgBWnrIiYllu7BNNyealdVLvRwEmTHWXvJwew==:
           |"content-length": 18
           |"content-type": application/json
           |"@signature-params": ("@method" "@authority" "@path" \
           |  "content-digest" "content-length" "content-type")\
           |  ;created=1618884473;keyid="test-key-rsa-pss"""".rfc8792single
       )
     )


     val sigData = req.getSignature(Rfc8941.Token("sig1"))
     assertEquals(
       sigData.map(_._1),
       sigInput25
     )

     val signature = """sig1=:HIbjHC5rS0BYaa9v4QfD4193TORw7u9edguPh0AW3dMq9WImrl\
         |FrCGUDih47vAxi4L2YRZ3XMJc1uOKk/J0ZmZ+wcta4nKIgBkKq0rM9hs3CQyxXGxH\
         |LMCy8uqK488o+9jrptQ+xFPHK7a9sRL1IXNaagCNN3ZxJsYapFj+JXbmaI5rtAdSf\
         |SvzPuBCh+ARHBmWuNo1UzVVdHXrl8ePL4cccqlazIJdC4QEjrF+Sn4IxBQzTZsL9y\
         |9TP5FsZYzHvDqbInkTNigBcE9cKOYNFCn4D/WM7F6TNuZO9EgtzepLWcjTymlHzK7\
         |aXq6Am6sfOrpIC49yXjj3ae6HRalVc/g==:""".rfc8792single

     assertEquals(
        sigData.map(_._2.canon),
        Some(signature.substring(5).nn)
     )   

   }
