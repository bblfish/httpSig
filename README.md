## Http Sig library

This library consists of Scala and Scala-JS components implementing the
IETF's HTTP-Bis [Signing HTTP Messages](https://httpwg.org/http-extensions/draft-ietf-httpbis-message-signatures.html)
spec. This builds on the experience of [Amazon Web Services, Signing HTTP](https://docs.aws.amazon.com/general/latest/gr/sigv4_signing.html).
It can be used in may ways, including authentication protocols such as
[HttpSig](https://github.com/solid/authentication-panel/blob/main/proposals/HttpSignature.md).

This repository contains the following projects:

* rfc8941: a pure scala implementation
  of [RFC 8941: Structured Field Values](https://datatracker.ietf.org/doc/html/rfc8941)
  needed by "Signing HTTP Messages" that compiles to Java and JavaScript.
* akka: Implementation for the [akka-http](https://akka.io/) Actor Framework of
  of [Signing HTTP Messages](https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-07.html)
* todo: write a [http4s](https://http4s.org) messages signing project for client and (later) servers

### TODO

* make it Java friendly (see how [akka](https://akka.io/) achieves that)
* make it JS friendly (client and server)
* publish libs to maven

If you wish to have the library run on a specific client or server environment, please
contact [henry.story@co-operating.systems](mailto:henry.story@co-operating.systems) or leave
issues in the Issue DataBase.