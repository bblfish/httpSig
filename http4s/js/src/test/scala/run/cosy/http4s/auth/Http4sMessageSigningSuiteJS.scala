package run.cosy.http4s.auth

import run.cosy.http.auth.SigningSuiteHelpers

given pem: bobcats.util.PEMUtils = bobcats.util.WebCryptoPEMUtils
given helper: SigningSuiteHelpers = new SigningSuiteHelpers

class Http4sMessageSigningSuiteJVM extends run.cosy.http4s.auth.Http4sMessageSigningSuite