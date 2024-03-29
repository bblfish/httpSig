/*
 * Copyright 2021 Henry Story
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package run.cosy.http.headers

import cats.parse.Parser
import run.cosy.http.headers.Rfc8941.Serialise.Serialise
import run.cosy.http.headers.{RFC8941Exception, NumberOutOfBoundsException, ParsingException}

import java.math.{MathContext, RoundingMode}
import java.util.Base64
import scala.collection.immutable.{ArraySeq, ListMap}
import scala.reflect.TypeTest
import scala.util.{Failure, Success, Try}
import scodec.bits.ByteVector
import cats.syntax.all.*

//https://dotty.epfl.ch/docs/reference/experimental/canthrow.html
// import language.experimental.saferExceptions

/** Structured Field Values for HTTP [[https://www.rfc-editor.org/rfc/rfc8941.html RFC8941]]
  */
object Rfc8941:

   //
   // types used by RFC8941
   //
   /** see [[https://www.rfc-editor.org/rfc/rfc8941.html#section-3.3 §3.3 Items]] of RFC8941.
     */
   type Item   = SfInt | SfDec | SfString | Token | Bytes | Boolean
   type Bytes  = ByteVector
   type Param  = (Token, Item)
   type Params = ListMap[Token, Item]
   type SfList = List[Parameterized]
   type SfDict = ListMap[Token, Parameterized]

   // warning this is public and there is an unsafeParse
   def Param(tk: String, i: Item): Param = (Token.unsafeParsed(tk), i)

   def Params(ps: Param*): Params = ListMap(ps*)

   def SfDict(entries: (Token, Parameterized)*): ListMap[Token, Parameterized] =
     ListMap(entries*)

   private def paramConversion(paras: Param*): Params = ListMap(paras*)

   sealed trait Parameterized:
      def params: Params

   /** SFInt's cover a subspace of Java Longs. An Opaque type would not do, as these need to be
     * pattern matched. Only the object constructor can build these
     */
   final case class SfInt private (val long: Long)

   /* https://www.rfc-editor.org/rfc/rfc8941.html#ser-decimal */
   final case class SfDec private (val double: Double)

   /** class has to be abstract to remove the `copy` operation which would allow objects outside
     * this package to create illegal values.
     */
   final case class SfString private (val asciiStr: String):
      /** the string formatted for inclusion in Rfc8941 output with surrounding "..." and escaped \
        * and "
        */
      def formattedString: String =
         import SfString.bs
         val sb = new StringBuilder
         sb.append('"')
         for c <- asciiStr do
            if c == '\\' then sb.append(bs).append(bs)
            else if c == '"' then sb.append(bs).append('"')
            else sb.append(c)
         sb.append('"')
         sb.toString()

   // class is abstract to remove copy operation
   final case class Token private (val tk: String)

   /** dict-member = member-key ( parameters / ( "=" member-value )) member-value = sf-item /
     * inner-list
     *
     * @param key
     *   member-key
     * @param values
     *   if InnerList with an empty list, then we have "parameters", else we have an inner list
     */
   final case class DictMember(key: Token, values: Parameterized)

   /** Parameterized Item */
   final case class PItem[T <: Item](item: T, params: Params) extends Parameterized

   /** Inner List */
   final case class IList(items: List[PItem[?]], params: Params) extends Parameterized

   object SfInt:
      val MAX_VALUE: Long = 999_999_999_999_999L
      val MIN_VALUE: Long = -MAX_VALUE

      @throws[NumberOutOfBoundsException]
      @throws[NumberFormatException]
      def apply(longStr: String): SfInt = apply(longStr.toLong)

      /** We throw a stackfree exception. Calling code can wrap in Try. */

      @throws[NumberOutOfBoundsException]
      def apply(long: Long): SfInt =
        if long <= MAX_VALUE && long >= MIN_VALUE then new SfInt(long)
        else throw NumberOutOfBoundsException(long)

      // no need to check bounds if parsed by parser below
      private[Rfc8941] def unsafeParsed(longStr: String): SfInt = new SfInt(longStr.toLong)
   end SfInt

   object SfDec:
      val MAX_VALUE: Double = 999_999_999_999.999
      val MIN_VALUE: Double = -MAX_VALUE
      val mathContext       = new MathContext(3, RoundingMode.HALF_EVEN)

      @throws[NumberOutOfBoundsException]
      def apply(d: Double): SfDec =
        if d <= MAX_VALUE && d >= MIN_VALUE then
           new SfDec(BigDecimal(d).setScale(3, BigDecimal.RoundingMode.HALF_EVEN).doubleValue)
        else throw NumberOutOfBoundsException(d)

      @throws[NumberFormatException]
      def apply(sfDecStr: String): SfDec = Parser.sfDecimal.parseAll(sfDecStr) match
         case Left(err)  => throw new NumberFormatException(err.toString)
         case Right(num) => num

      // no need to check bounds if parsed by parser below
      private[Rfc8941] def unsafeParsed(int: String, fract: String): SfDec =
        new SfDec(BigDecimal(int + "." + fract).setScale(
          3,
          BigDecimal.RoundingMode.HALF_EVEN
        ).doubleValue)
   end SfDec

   object SfString:
      val bs = '\\'

      @throws[IllegalArgumentException]
      def apply(str: String): SfString =
        if str.forall(isAsciiChar) then new SfString(str)
        else throw new IllegalArgumentException(s"$str<<< contains non ascii chars ")

      def isAsciiChar(c: Int): Boolean = (c > 0x1f) && (c < 0x7f)

      /* No danger of throwing an exception here, as tokens are subsets of SfString */
      def apply(token: Token): SfString = new SfString(token.tk)

      private[Rfc8941] def unsafeParsed(asciiStr: List[Char]): SfString =
        new SfString(asciiStr.mkString)
   end SfString

   object Token:
      @throws[ParsingException]
      def apply(t: String): Token =
        Parser.sfToken.parseAll(t) match
           case Right(value) => value
           case Left(err) => throw ParsingException(
               s"error parsing token $t",
               s"failed at offset ${err.failedAtOffset}"
             )

      private[Rfc8941] def unsafeParsed(name: String) = new Token(name)

   end Token

   object PItem:
      def apply[T <: Item](item: T): PItem[T] = new PItem[T](item, ListMap())

      def apply[T <: Item](item: T)(params: Param*): PItem[T] = new PItem(item, ListMap(params*))

   implicit def token2PI[T <: Item]: Conversion[T, PItem[T]] = (i: T) => PItem[T](i)

   object IList:
      def apply(items: PItem[?]*)(params: Param*): IList = new IList(items.toList, ListMap(params*))

   implicit val paramConv: Conversion[Seq[Param], Params] = paramConversion

   object Syntax:
      extension (sc: StringContext)
        def sf(args: Any*): SfString =
           val strings     = sc.parts.iterator
           val expressions = args.iterator
           val buf         = new StringBuilder(strings.next())
           while strings.hasNext do
              buf.append(expressions.next())
              buf.append(strings.next())
           SfString(buf.toString())

   object Parser:

      import cats.parse.Numbers.{nonNegativeIntString, signedIntString}
      import cats.parse.{Parser as P, Parser0 as P0, Rfc5234 as R5234}
      import run.cosy.http.headers.Rfc7230.ows

      // these have to be at the top, or all the vals below have to be lazy
      private val `*`            = P.charIn('*')
      private val `:`            = P.char(':')
      private val `?`            = P.char('?')
      private val `.`            = P.char('.')
      private val bs             = '\\'
      private val minus          = P.char('-')
      private val `\\` : P[Unit] = P.char(bs)

      lazy val boolean: P[Boolean] = R5234.bit.map(_ == '1')
      val sfBoolean: P[Boolean]    = (`?` *> boolean)
      val sfInteger: P[SfInt] =
        (minus.?.with1 ~ R5234.digit.rep(1, 15)).string.map(s => Rfc8941.SfInt.unsafeParsed(s))
      val decFraction: P[String] = R5234.digit.rep(1, 3).string
      val signedDecIntegral: P[String] =
        (minus.?.with1 ~ R5234.digit.rep(1, 12)).map { case (min, i) =>
          min.map(_ => "-").getOrElse("") + i.toList.mkString
        }
      val sfDecimal: P[SfDec] =
        (signedDecIntegral ~ (`.` *> decFraction)).map { case (dec, frac) =>
          SfDec.unsafeParsed(dec, frac.toList.mkString) // todo: keep the non-empty list?
        }
      // first we have to check for decimals, then for integers, or there is the risk that the `.` won't be noticed
      // todo: optimisation would remove backtracking here
      val sfNumber: P[SfDec | SfInt] = (sfDecimal.backtrack.orElse(sfInteger))

      /** unescaped = SP / %x21 / %x23-5B / %x5D-7E note: this is similar to
        * [[run.cosy.http.headers.Rfc5234]] except except that tabs are not allowed an anything
        * above the ascii char set.
        */
      val unescaped: P[Char] =
        P.charIn(' ', 0x21.toChar)
          .orElse(P.charIn(0x23.toChar to 0x5b.toChar))
          .orElse(P.charIn(0x5d.toChar to 0x7e.toChar))
      val escaped: P[Char] = (`\\` *> (P.charIn(bs, '"')))
      val sfString: P[SfString] = (R5234.dquote *> (unescaped | escaped).rep0 <* R5234.dquote)
        .map(chars => SfString.unsafeParsed(chars))
      val sfToken: P[Token] =
        ((R5234.alpha | P.charIn('*')) ~ (Rfc7230.tchar | P.charIn(':', '/')).rep0)
          .map { (c, lc) => Token.unsafeParsed((c :: lc).mkString) }
      val base64: P[Char] = (R5234.alpha | R5234.digit | P.charIn('+', '/', '='))
      val sfBinary: P[ByteVector] = (`:` *> base64.rep.string <* `:`).map { chars =>
        ByteVector.fromValidBase64(chars, scodec.bits.Bases.Alphabets.Base64)
      }
      val bareItem: P[Item] =
        P.oneOf(sfNumber :: sfString :: sfToken :: sfBinary :: sfBoolean :: Nil)
      val lcalpha: P[Char] = P.charIn(0x61.toChar to 0x7a.toChar) | P.charIn('a' to 'z')
      val key: P[Token] =
        ((lcalpha | `*`) ~ (lcalpha | R5234.digit | P.charIn('_', '-', '.', '*')).rep0)
          .map((c, lc) => Token.unsafeParsed((c :: lc).mkString))
      val parameter: P[Param] =
        (key ~ (P.char('=') *> bareItem).orElse(P.pure(true)))
      // note: parameters always returns an answer (the empty list) as everything can have parameters
      // todo: this is not exeactly how it is specified, so check here if something goes wrong
      val parameters: P0[Params] =
        (P.char(';') *> ows *> parameter).rep0.orElse(P.pure(List())).map { list =>
          ListMap.from[Token, Item](list.iterator)
        }
      val sfItem: P[PItem[Item]] =
        (bareItem ~ parameters).map((item, params) => PItem(item, params))
      val innerList: P[IList] =
         import R5234.sp
         (((P.char('(') ~ sp.rep0) *> (sfItem ~ ((sp.rep(
           1
         ) *> sfItem).backtrack.rep0) <* sp.rep0).? <* P.char(')')) ~ parameters)
           .map {
             case (Some(pi, lpi), params) => IList(pi :: lpi, params)
             case (None, params)          => IList(List(), params)
           }
      val listMember: P[Parameterized] = (sfItem | innerList)
      val sfList: P[SfList] =
        (listMember ~ ((ows *> P.char(',') *> ows).void.with1 *> listMember).rep0).map((p, lp) =>
          p :: lp
        )
      val memberValue: P[Parameterized] = (sfItem | innerList)
      // note: we have to go with parsing `=` first as parameters always returns an answer.
      val dictMember: P[DictMember] = (key ~ (P.char('=') *> memberValue).eitherOr(parameters))
        .map {
          case (k, Left(parameters))     => DictMember(k, PItem(true, parameters))
          case (k, Right(parameterized)) => DictMember(k, parameterized)
        }
      val sfDictionary: P[SfDict] =
        (dictMember ~ ((ows *> P.char(',') *> ows).with1 *> dictMember).rep0).map((dm, list) =>
           val x: List[DictMember] = dm :: list
           // todo: avoid this tupling
           ListMap.from(x.map((d: DictMember) => Tuple.fromProductTyped(d)))
        )
   end Parser

   /** Serialisation implementations for the RFC8941 types as defined in
     * [[https://www.rfc-editor.org/rfc/rfc8941.html#section-4.1 §4.1 Serializing Structured Fields]]
     * written as a type class so thqt it can easily be extended to give the result with non RFC8941
     * headers and work with different frameworks.
     *
     * todo: it may be that scalaz's Show class that uses a Cord
     */
   object Serialise:

      trait Serialise[-T]:
         extension (o: T)
           // may be better if encoded directly to a byte string
           def canon: String

      given itemSer: Serialise[Item] with
         extension (o: Item)
           def canon: String = o match
              case i: SfInt => i.long.toString
              // todo: https://www.rfc-editor.org/rfc/rfc8941.html#ser-decimal
              case d: SfDec       => d.double.toString
              case s: SfString    => s.formattedString
              case tk: Token      => tk.tk
              case as: ByteVector => ":" + as.toBase64 + ":"
              case b: Boolean     => if b then "?1" else "?0"

      //
      // complex types
      //

      given paramSer(using Serialise[Item]): Serialise[Param] with
         extension (o: Param)
           def canon: String = ";" + o._1.canon + {
             o._2 match
                case b: Boolean => ""
                case other      => "=" + other.canon
           }

      given paramsSer(using Serialise[Param]): Serialise[Params] with
         extension (o: Params)
           def canon: String = o.map(_.canon).mkString

      given sfParamterizedSer[T <: Item](
          using
          Serialise[Item],
          Serialise[Params]
      ): Serialise[Parameterized] with
         extension (o: Parameterized)
           def canon: String = o match
              case l: IList => l.items.map(i => i.canon).mkString("(", " ", ")") + l.params.canon
              case pi: PItem[?] => pi.item.canon + pi.params.canon

      given sfListSer(
          using Serialise[Params]
      ): Serialise[SfList] with
         extension (o: SfList)
           def canon: String =
             o.map(p => p.canon).mkString(", ")

      given sfDictSer(
          using
          Serialise[Item],
          Serialise[Param],
          Serialise[Params],
          Serialise[PItem[Item]],
          Serialise[IList]
      ): Serialise[SfDict] with
         extension (o: SfDict)
           def canon: String = o.map {
             case (tk, PItem(true, params)) => tk.canon + params.canon
             case (tk, lst: IList)          => tk.canon + "=" + lst.canon
             case (tk, pit @ PItem(_, _))   => tk.canon + "=" + pit.canon
           }.mkString(", ")
   end Serialise
