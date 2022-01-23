package run.cosy.http.headers

import run.cosy.http.Http


// implementations will need to specify default hosts and ports
trait MessageSelectors[H <: Http] {
	import Http.*
	val securedConnection: Boolean
	type HeaderSelector[H] = MessageSelector[H] //& run.cosy.http.headers.HeaderSelector[H]

	lazy val authorization: HeaderSelector[Request[H]]
	lazy val `cache-control`: HeaderSelector[Message[H]]
	lazy val date: HeaderSelector[Message[H]]

	lazy val etag: HeaderSelector[Response[H]]
	lazy val host: HeaderSelector[Request[H]]
	lazy val signature: DictSelector[Message[H]]

	lazy val `content-type`: HeaderSelector[Message[H]]
	lazy val `content-length`: HeaderSelector[Message[H]]
	lazy val digest: HeaderSelector[Message[H]]

	lazy val `@request-target`: MessageSelector[Request[H]]
	lazy val `@method`: MessageSelector[Request[H]]
	lazy val `@target-uri`: MessageSelector[Request[H]]
	lazy val `@authority`: MessageSelector[Request[H]]
	lazy val `@scheme`: MessageSelector[Request[H]]
	lazy val `@path`: MessageSelector[Request[H]]
	lazy val `@status`: MessageSelector[Response[H]]
	lazy val `@query`: MessageSelector[Request[H]]
	lazy val `@query-params`: MessageSelector[Request[H]]
	lazy val `@request-response`:  MessageSelector[Request[H]]


	/** Note: @target-uri and @scheme can only be set by application code as a choice needs to be made */
	given requestSelectorOps: SelectorOps[Request[H]] = SelectorOps[Request[H]](
		authorization,  host,
		`@request-target`, `@method`, `@path`, `@query`, `@query-params`,
		`@request-response`,
		//all the below are good for responses too
		digest, `content-length`, `content-type`, signature, date, `cache-control`
	)

	//both: digest, `content-length`, `content-type`, signature, date, `cache-control`

	given responseSelectorOps: SelectorOps[Response[H]] =
		SelectorOps[Response[H]](`@status`, etag,
			//all these are good for requests too
			digest, `content-length`, `content-type`, signature, date, `cache-control`
		)
}
