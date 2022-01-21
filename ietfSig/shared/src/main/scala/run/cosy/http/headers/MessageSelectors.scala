package run.cosy.http.headers

// implementations will need to specify default hosts and ports
trait MessageSelectors {
	type Message
	type Request <: Message
	type Response <: Message
	val securedConnection: Boolean
	type HeaderSelector[R] = MessageSelector[R] & run.cosy.http.headers.HeaderSelector[R]

	lazy val authorization: HeaderSelector[Request]
	lazy val `cache-control`: HeaderSelector[Message]
	lazy val date: HeaderSelector[Message]

	lazy val etag: HeaderSelector[Response]
	lazy val host: HeaderSelector[Request]
	lazy val signature: DictSelector[Message]

	lazy val `content-type`: HeaderSelector[Message]
	lazy val `content-length`: HeaderSelector[Message]
	lazy val digest: HeaderSelector[Message]

	lazy val `@request-target`: MessageSelector[Request]
	lazy val `@method`: MessageSelector[Request]
	lazy val `@target-uri`: MessageSelector[Request]
	lazy val `@authority`: MessageSelector[Request]
	lazy val `@scheme`: MessageSelector[Request]
	lazy val `@path`: MessageSelector[Request]
	lazy val `@status`: MessageSelector[Response]
	lazy val `@query`: MessageSelector[Request]
	lazy val `@query-params`: MessageSelector[Request]
	lazy val `@request-response`:  MessageSelector[Request]


	/** Note: @target-uri and @scheme can only be set by application code as a choice needs to be made */
	given requestSelectorOps: SelectorOps[Request] = SelectorOps[Request](
		authorization,  host,
		`@request-target`, `@method`, `@path`, `@query`, `@query-params`,
		`@request-response`,
		//all the below are good for responses too
		digest, `content-length`, `content-type`, signature, date, `cache-control`
	)

	//both: digest, `content-length`, `content-type`, signature, date, `cache-control`

	given responseSelectorOps: SelectorOps[Response] =
		SelectorOps[Response](`@status`, etag,
			//all these are good for requests too
			digest, `content-length`, `content-type`, signature, date, `cache-control`
		)
}
