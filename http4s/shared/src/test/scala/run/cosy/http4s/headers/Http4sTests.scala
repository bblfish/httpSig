package run.cosy.http4s.headers

import org.http4s.{Request,Headers}
import org.http4s.headers.Host

//odds and ends for tests when things don't seem to work
class Http4sTests extends munit.FunSuite {


	test("Host header selection") {
		val req = Request(headers=Headers(
			"Host" -> "www.example.com",
			"Date" -> "Sat, 07 Jun 2014 20:51:35 GMT",
			"Cache-Control" -> "max-age=60",
			"Cache-Control" -> "   must-revalidate"
		))
		assertEquals(req.headers.get[Host],Some(Host("www.example.com")))
	}

}
