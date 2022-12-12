## HttpSig library

This library consists of Scala and Scala-JS components implementing the
IETF's HTTP-Bis [HTTP Message Signatures](https://datatracker.ietf.org/doc/draft-ietf-httpbis-message-signatures/). 

See also the web service
written in Python at [httpsig.org](https://httpsig.org)
         
## What is Http Message Signatures?

The first version of this spec appeared as [draftcavage-http-signatures](https://datatracker.ietf.org/doc/html/draft-cavage-http-signatures-00) in 2013. After which followed 12 more versions. The IETF HTTP Bis WG then took that work over improving it a lot.

Http Signatures can be used by clients and by servers, to sign messages. This can be used in  authentication protocols such as the in development [Solid HttpSig](https://github.com/bblfish/authentication-panel/blob/main/proposals/HttpSignature.md).

### Where is it used?

Older versions of the spec have been used by Amazon Web Services and Mastodon, among others.  

 * see the [Amazon Web Services, Signing HTTP](https://docs.aws.amazon.com/general/latest/gr/sigv4_signing.html) spec. 

 * Version 06 of that spec is used by Mastodon. See
   - [the joinmastodon /spec/security](https://docs.joinmastodon.org/spec/security/#http-sign) document.
   - [signature_verification.rb](https://github.com/mastodon/mastodon/blob/736b4283b0936134e83092f8b16d686f6478a51f/app/controllers/concerns/signature_verification.rb) ruby file

## Content of this repository 

This repository contains the following projects:

* **rfc8941**: a pure scala implementation
  of [RFC 8941: Structured Field Values](https://datatracker.ietf.org/doc/html/rfc8941) 
  needed by "Signing HTTP Messages" that compiles to Java and JavaScript. This is a core component needed for "Signing Http Messages"
* **ietfSig**: the main implementation in Scala compiling to JVM and JS, with an abstraction of HTTP so as to reduce duplication of code for the various frameworks such as Akka or HttpSig
* **ietfSigTests**: a project with tests that are then run by each of the implementations in their tests. 
* **akka**: Implementation of "Signing HTTP Messages" for the [akka-http](https://akka.io/) Actor Framework of
  of [Signing HTTP Messages](https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html)
* **http4s** implementation of the cats based [http4s](https://http4s.org)

## Usage

The architecture of the third version of this library was worked out n [the ietfSig README.md](./ietfSig/shared/src/main/scala/run/cosy/http/messages/README.md). 

Note: currently we only have implemented request signing and verification. That is most of the work needed for response verification, so it should not take much time to get done.  

Look at examples in the test suite.
 * [VerifyBaseOnRequests.scala](ietfSigTests/shared/src/main/scala/run/cosy/http/messages/VerifyBaseOnRequests.scala) shows how a client can build the base for all the examples in the spec using typesafe functions.
 * [VerifySignatureTests.scala](ietfSigTests/shared/src/main/scala/run/cosy/http/auth/VerifySignatureTests.scala) shows how a server would verify the signatures in a request, given a DB of keyId to key material db.
 * todo, verify the client correctly signs example messages
 * todo, implement the same for response signing: this should be easier because most of the tools for request signing can be re-used

Another place to look is for applications using the library. 
See for example:
  * [Reactive Solid](https://github.com/co-operating-systems/Reactive-SoLiD) web server in Akka
  * [SolidCtrlApp](https://github.com/bblfish/SolidCtrlApp)

(There may be a lag between the time this library is released and the time they use the latest version)

### JavaScript Testing

`httpSig` compiles to Java and JavaScript. 
Testing in JS environments is done using [Selenium](https://www.selenium.dev). 
This requires having a selenium driver. 
On MacOs this is installed ([see Stack Overflow](https://stackoverflow.com/questions/18868743/how-to-install-selenium-webdriver-on-mac-os)) using `brew install selenium-server`. But one still requires Chrome and Firefox drivers to be installed after that.
 * For Mozilla drivers see [their geckodriver page](https://github.com/mozilla/geckodriver/releases)
 * For Chrome see the [Chrome Driver page](https://chromedriver.chromium.org/downloads) 

Inside of sbt one can then run tests for Firefox only with
```scala
> set Global / useJSEnv := JSEnv.Firefox
> test
```
Inside of sbt one can then run tests for Chrome only with
```scala
> set Global / useJSEnv := JSEnv.Chrome
> test
```
NodeJS is the default, but that won't run any tests in this case
as we have not implemented encryption for NodeJS in bobcats yet.


### Released Artifacts

Artifacts are released in the Sonatype [net.bblfish.crypto](https://oss.sonatype.org/content/repositories/snapshots/net/bblfish/crypto/) 
snapshot repository.

### TODO

* make it Java friendly (see how [akka](https://akka.io/) achieves that)
* make it JS friendly (client and server)

### Thanks

This work was made possible by the generous EU grant from nlnet for 
the [Solid Control Project](https://nlnet.nl/project/SolidControl/) and for [Solid Wallet](https://nlnet.nl/project/SolidWallet/index.html)
That last will go through 2023. 

If you wish to have the library run on a specific client or server environment, please
contact [henry.story@co-operating.systems](mailto:henry.story@co-operating.systems) or leave
issues in the Issue database.
