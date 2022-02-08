## HttpSig library

This library consists of Scala and Scala-JS components implementing the
IETF's HTTP-Bis [Signing HTTP Messages](https://httpwg.org/http-extensions/draft-ietf-httpbis-message-signatures.html)
spec. This builds on the experience of [Amazon Web Services, Signing HTTP](https://docs.aws.amazon.com/general/latest/gr/sigv4_signing.html).
It can be used in may ways, including authentication protocols such as
[HttpSig](https://github.com/solid/authentication-panel/blob/main/proposals/HttpSignature.md).

This repository contains the following projects:

* rfc8941: a pure scala implementation
  of [RFC 8941: Structured Field Values](https://datatracker.ietf.org/doc/html/rfc8941) 
  needed by "Signing HTTP Messages" that compiles to Java and JavaScript. This is a core component needed for "Signing Http Messages"
* akka: Implementation of "Signing HTTP Messages" for the [akka-http](https://akka.io/) Actor Framework of
  of [Signing HTTP Messages](https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html)
* [http4s](https://http4s.org) implementation of "Signing HTTP Messages" project for Java or JS clients (todo nodejs)

### Released Artifacts

Artifacts are released in the Sonatype [net.bblfish.crypto](https://oss.sonatype.org/content/repositories/snapshots/net/bblfish/crypto/) 
snapshot repository.

### TODO

* make it Java friendly (see how [akka](https://akka.io/) achieves that)
* make it JS friendly (client and server)

### Thanks

This work was made possible by the generous EU grant from nlnet for 
the [Solid Control Project](https://nlnet.nl/project/SolidControl/).
That project is to end in January 2022. 

If you wish to have the library run on a specific client or server environment, please
contact [henry.story@co-operating.systems](mailto:henry.story@co-operating.systems) or leave
issues in the Issue DataBase.

We are looking for further funding opportunities to continue the work.