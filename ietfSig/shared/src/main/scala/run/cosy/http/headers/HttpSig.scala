package run.cosy.http.headers

/**
 *  The Signature to look at. Can be passed in an Authorization header. In
 *  the example below the proof name would be "sig1".
 *  <pre>
 *  GET /comments/ HTTP/1.1
 *  Authorization: HttpSig proof=sig1
 *  Accept: text/turtle, application/ld+json;q=0.9
 *  Signature-Input: sig1=("@request-target" "authorization");keyid="/keys/alice#"
 *  Signature: sig1=:jnwCuSDVKd8royZnKgm0GBQzLc...:
 * </pre>
 *
 * @param proofName the name of the proof to look at
 */
case class HttpSig(proofName: Rfc8941.Token)
