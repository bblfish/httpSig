package run.cosy.http.dummy

import cats.Id
import run.cosy.http.dummy.DummyHttp
import run.cosy.http.Http.{Header, Message, Request}
import run.cosy.http.messages.{HeaderId, SelectorFn}
import run.cosy.http.{Http, HttpOps}

import scala.util.Try
import cats.data.NonEmptyList

import scala.util.{Failure, Success}
import run.cosy.http.auth.{ParsingExc, SelectorException}
import _root_.org.typelevel.ci.CIString

object DummyHttp extends Http:
  http =>
  
  override type Message[F[_]] =  Seq[Header]
  override type Request[F[_]] =  Seq[Header]
  override type Response[F[_]]=  Seq[Header]

  override type Header = (CIString, String)
  
  given hOps: HttpOps[HT] with
    /** extensions needed to abstract across HTTP implementations for our purposes */
    extension[F[_]] (msg: Http.Message[F, HT])
      def headers: Seq[Http.Header[HT]] =
        msg.asInstanceOf[Seq[Header]]

    extension[F[_], R <: Http.Message[F, HT]] (msg: R)
      def addHeaders(headers: Seq[Http.Header[HT]]): R =
        val x: Seq[Header] = msg.asInstanceOf[Seq[Header]] :++ headers
        x.asInstanceOf[R]
      // here we do really add a header to existing ones.
      // note http4s
      def addHeader(name: String, value: String): R =
         val x = msg.asInstanceOf[Seq[Header]] :++ List(name -> value)
         x.asInstanceOf[R]

      // this is used in tests
      def removeHeader(name: String): R =
        val x = msg.asInstanceOf[Seq[Header]].filter( hdr => hdr._1.equals(CIString(name)))
        x.asInstanceOf[R]

      // used in tests: return the Optional Value
      def headerValue(name: String): Option[String] =
        msg.asInstanceOf[Seq[Header]].groupBy(p => p._1).get(CIString(name)).map(_.map(_._2).mkString(", "))
  end hOps
end DummyHttp

import run.cosy.http.messages
import run.cosy.http.headers.Rfc8941
import run.cosy.http.dummy.DummyHttp
import run.cosy.http.dummy.DummyHttp.HT as DHT
import run.cosy.http.Http
import run.cosy.http.Http.*

  
object DummyHeaderSelectorFns extends messages.HeaderSelectorFns[Id,DHT]:

  import DummyHttp.given

  override def requestHeaders(name: HeaderId): RequestFn =
    new messages.SelectorFn[Http.Request[Id,DHT]]:
      override val signingValues: Request[Id, DHT] => Either[ParsingExc, String|NonEmptyList[String]] =
        req => msgSel(name, req.asInstanceOf[Seq[(String,String)]])
        
  end requestHeaders 

  def msgSel(name: HeaderId, msg: Seq[(String, String)]): Either[ParsingExc,String|NonEmptyList[String]] =
     msg.groupBy(_._1).get(CIString(name.specName)) match
            case None => Left(SelectorException("no header named: "+name))
            case Some(pairs) => 
              val v: Seq[String] =  pairs.map(_._2)
              Right(NonEmptyList(v.head,v.tail.toList))
  end msgSel

  override def responseHeaders(name: HeaderId): ResponseFn =
    new messages.SelectorFn[Http.Response[Id,DHT]]:
      override val signingValues: Response[Id, DHT] => Either[ParsingExc, String | NonEmptyList[String]] =
        res => msgSel(name, res.asInstanceOf[Seq[(String,String)]])

end DummyHeaderSelectorFns


