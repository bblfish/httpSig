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

package run.cosy.http.messages

import run.cosy.http.auth.SelectorException
import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.SfString
import run.cosy.http.messages.Selectors.{CollationTp, SelFormat}

import scala.util.{Failure, Success, Try}

sealed trait ComponentId:
   def lcname: Rfc8941.Token
   def canon: String
   def specName: String

/** Type of component name for headers, and the type of interpretation in terms RFC8941 the values
  * are suseptible to
  */
sealed trait HeaderId extends ComponentId:
   type AllowedCollation <: CollationTp

   /* The lower case name of the header */
   def lcname: Rfc8941.Token

   def canon: String    = s""""${lcname.tk}""""
   def specName: String = lcname.tk
end HeaderId

sealed trait AtId extends ComponentId:
   def canon            = "\"@" + lcname.tk + "\""
   def specName: String = "@" + lcname.tk

object ComponentId:
   def parse(name: String): Rfc8941.Token | String =
      val id = Rfc8941.Token(name)
      if id.tk.forall(c => (!Character.isAlphabetic(c)) || Character.isLowerCase(c)) then id
      else s"header selector must be lower case token. received >$name<"

object AtId:
   /** Because there are a limited set of these, they should be tested at compile time using
     * something like https://softwaremill.com/fancy-strings-in-scala-3/ (otherwise: that is what
     * unit tests are for)
     */
   def apply(name: String): Try[AtId] = Try {
     if name.length == 0 then throw SelectorException("Message Component must start with @ char ")
     else if name.head != '@' then
        throw SelectorException("Message Component must start with @ char ")
     else
        ComponentId.parse(name.tail) match
           case err: String => throw SelectorException(err)
           case tk: Rfc8941.Token => new AtId:
                override val lcname: Rfc8941.Token = tk
   }

end AtId

object HeaderId:
   def apply(name: Rfc8941.Token) = ???

   def apply(name: String, format: SelFormat): Try[HeaderId] = Try {
     ComponentId.parse(name) match
        case str: String => throw new SelectorException(str)
        case id: Rfc8941.Token =>
          format match
             case SelFormat.Item       => new ItemId(id)
             case SelFormat.List       => new ListId(id)
             case SelFormat.Dictionary => new DictId(id)
   }

   private def parseAndWrap[X <: HeaderId](name: String, wrap: Rfc8941.Token => X): Try[X] =
     ComponentId.parse(name) match
        case tk: Rfc8941.Token => Success(wrap(tk))
        case str: String       => Failure(SelectorException(str))

   def item(name: String): Try[ItemId] = parseAndWrap(name, x => new ItemId(x))

   def dict(name: String): Try[DictId] = parseAndWrap(name, x => new DictId(x))

   def list(name: String): Try[ListId] = parseAndWrap(name, x => new ListId(x))

   def apply(name: String) = Try {
     ComponentId.parse(name) match
        case str: String       => throw new SelectorException(str)
        case id: Rfc8941.Token => new OldId(id)
   }

   sealed trait SfHeaderId extends HeaderId:
      def format: SelFormat

   final class OldId private[HeaderId] (val lcname: Rfc8941.Token) extends HeaderId:
      override type AllowedCollation = Selectors.Bin.type | Selectors.Raw.type

   final class DictId private[HeaderId] (val lcname: Rfc8941.Token) extends SfHeaderId:
      override type AllowedCollation = CollationTp
      def format: SelFormat = SelFormat.Dictionary
   final class ItemId private[HeaderId] (val lcname: Rfc8941.Token) extends SfHeaderId:
      override type AllowedCollation =
        Selectors.Bin.type | Selectors.Raw.type | Selectors.Strict.type
      def format: SelFormat = SelFormat.Item
   final class ListId private[HeaderId] (val lcname: Rfc8941.Token) extends SfHeaderId:
      override type AllowedCollation =
        Selectors.Bin.type | Selectors.Raw.type | Selectors.Strict.type
      def format: SelFormat = SelFormat.List