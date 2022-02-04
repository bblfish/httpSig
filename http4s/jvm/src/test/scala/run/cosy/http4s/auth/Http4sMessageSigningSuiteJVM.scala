package run.cosy.http4s.auth

import run.cosy.http.HttpOps
import run.cosy.http.auth.SigningSuiteHelpers
import run.cosy.http4s.Http4sTp

class Http4sMessageSigningSuiteJVM extends run.cosy.http4s.auth.Http4sMessageSigningSuite {
	given pem: bobcats.util.PEMUtils = bobcats.util.BouncyJavaPEMUtils
	override given ops: HttpOps[Http4sTp.type] = Http4sTp.httpOps
	override given sigSuite: SigningSuiteHelpers = new SigningSuiteHelpers

}
