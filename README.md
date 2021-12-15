## Http Sig library

This library consists of Scala and Scala-JS components needed for
[Signing HTTP Messages](https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-04.html)
spec put forward by the HTTP-Bis WG, in order to build protocols such as
the [HttpSig](https://github.com/solid/authentication-panel/blob/main/proposals/HttpSignature.md)
authentication protocol.

This contains the following projects

* rfc8941: a pure scala implementation
  of [RFC 8941: Structured Field Values](https://datatracker.ietf.org/doc/html/rfc8941)
  needed by "Signing HTTP Messages" that compiles to Java and JavaScript.
* akka: Implementation for the [akka-http](https://akka.io/) Actor Framework of
  of [Signing HTTP Messages](https://www.ietf.org/archive/id/draft-ietf-httpbis-message-signatures-04.html)
* todo: http4s: same to sign http4s messages

### TODO

see if one can extract a common part of Signing HTTP Messages that can abstract betweeen akka and
  http4s headers.
* make it Java friendly (see how [akka](https://akka.io/) achieves that)
* make it JS friendly