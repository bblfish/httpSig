package run.cosy.http.messages

import run.cosy.http.Http
import run.cosy.http.Http.*

trait AtComponents[F[_], H <: Http] {
  trait OnRequest extends AtComponent :
    override type Msg = Http.Request[F, H]

  trait OnResponse extends AtComponent :
      override type Msg = Http.Response[F, H]

  def `@method` : OnRequest
  
  def `@request-target`: OnRequest
  
  def `@target-uri`(using ServerContext): OnRequest

  def `@authority`(using sc: ServerContext): OnRequest
  
  def `@scheme`(using sc: ServerContext): OnRequest
  
  def `@path`: OnRequest
  
  def `@query`: OnRequest
  
  def `@query-param`: OnRequest
  
  def `@status`: OnResponse
}
