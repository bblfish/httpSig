# Data Model


## Morphisms in a Kleisli category of an error monad

We have something like the following morphisms in the Kleisli category of the Try Monad. That is each function is really a `X 🡢 Try[Y]` which we will denote 
in the Kleisli category as `X ⟹ Y`

```scala
CompontenDB : ID ⟹ Component
Component : Params ⟹ Selector
Selector : Msg ⟹ String
```
Or in short

```scala
ComponentDB: ID ⟹ (Params ⟹ (Msg ⟹ String))
```                  

Note: there are actually two types of `ID`
 - `@xxx` ids, starting with `@`
 - `header-name` ids, the rest

The type of `ID` can affect the type of `Msg` in the last function. 
If `ID` is of the `@` type then the whole HTTP header will be needed,
otherwise only the headers will be needed. That distinction can be useful
to help testing, since finding the selector for normal headers can be done with a generic data structure such as 
```scala
type Msg = ListMap[String,Seq[String]]
```
or even simpler 
```scala
type Msg = Seq[String]
```
There are good reasons to keep the simpler definition as we will see.

## Refinements

The above definition gives a good overview of the protocol. But it is too
coarse grained.

### Dividing into Request and Response DBs

The `Msg` type can be either a `Request` or a `Response`

The `ComponentDB` is called when a Msg needs to be signed or verified. At that point it is known whether the Msg is a Request or a Response. 
The DBs will be very different. There are headers that appear in a response but not a request such as `www-authenticate`, and vice versa some such as `Authorization` that appear only in the request headers.
So we could perhaps be more precise with:

```scala
RequestDB: ID ⟹ (Params ⟹ (Request ⟹ String))
ResponseDB: ID ⟹ (Params ⟹ (Response ⟹ String))
```

### `req` and types

The `req` attribute throws a spanner into that clean answer.
When present it specifies that the data should be searched in the request that goes with the given response. (Suggesting this should only
be found in response `Signature-Input` statements)
So this does not change our definition of `RequestDB`, but it does
require changing `ResponseDB` to

```scala
ResponseDB: ID ⟹ (Params ⟹ (Request|Response ⟹ String))
```

Applying Parameters to a component can return a function from either a request to a string or a Response to a string.

### looking at individual components

With individual components we can be more precise. 
`WWW-Authenticate` headers only appear in responses, so we
have:

```scala
C(`www-authenticate`): Params ⟹ Response ⟹ String 
```
On the other hand, `Authorize` headers only appear in
Requests:

```scala
C(`authorize`): Params ⟹ Request ⟹ String 
```
And finally other headers appear on both:

```scala
C(`content-length`): Params ⟹ (Request|Response ⟹ String)
C(`content-type`): Params ⟹ (Request|Response ⟹ String)
```

We could of course go and be even more precise with the types
stating that content-length can not appear on `OPTIONS` requests,...
But that is something the HTTP server can deal with at another layer,
by rejecting faulty requests. 

We need the distinction between requests and responses because
the caller will need to know whether to give the function a request
or a response to get the string it needs to build the signature base.

### `req` in headers that apply only to requests

It is not legal to have a Param `req` in a header component that can only be applied to a Response. Take `ID(www-authenticate)` for example. A `responseDB(www-authenticate)` would return a `Component`. So far so good. But if one then attempted to apply a `Params` list containing a `req` attribute in it, in order to get a selector, then the call should fail, since `req` implies that the attribute needs to be applied to a Request: but that is not possible with www-authenticate.

What would happen if we use the original `ComponentDB` model? Well, `componentDB(ID(www-authenticate))` should return a component, and applying parameters including `req` to that should return a selector.

The selector would of course fail when applied to the Request (in accordance with the spec on `req` tag) because that does not have a `www-authenticate` header.

Well it SHOULD fail, even if the request had such a header, because the request SHOULD NOT have that header. So that looks like the problem. Applying the "req" attribute on the `www-authenticate` header Component of a request should fail immediately. This suggests that `www-authenticate` should know it is a request component and that its
signature is

### Header Selectors

So we have reached the conclusion that this represents our domain well.

```scala
RequestDB:  ID ⟹ (Params ⟹ (Request ⟹ String))
ResponseDB: ID ⟹ (Params ⟹ (Request|Response ⟹ String))
```

But we actually have two distinct type of Ids: those starting with the `@` character and those that don't. Ie we have

```scala
type ID = @Id | Id 
```

The above DB functions describe perfectly what we need for `@Id`s since the selectors (the last function) must take `Request` or `Response` inputs. 

But non `@Id`s, i.e. `Id`s, need only inspect the headers of the request, which is a much simpler data structure. 
And from that we can get all we need if an external tool where to extract only the headers we need as a List of Strings.
That is we could have:

```scala
requestDB: Id ⟹ (Params ⟹ (NonEmptyList[String] ⟹ String))
resultDB:  Id ⟹ (Params ⟹ (NonEmptyList[String] ⟹ String))
```

Both functions have the same signature, but they won't be the same functions.
For example, as we saw, `resultDb(www-authenticate)` will return a component, but `requestDB(www-authenticate)` should return an immediate error. 

Furthermore there is a difference that the above signature is hiding: namely that the Non Empty lists on the requests and on the responses come from different objects. 

We have that `requestDB(id)(params)` will return a function that will need headers
taken from a request, whereas `responseDB(id)(params)` will need headers taken either from a response or from request! (if the `req` attribute was present in the parameters). 
So we don't escape the structure of the signature we started with.

This indicates we should stick with the uniform description we had earlier, but perhaps with some extra refinement for header `Id`s

```scala
requestDB: Id ⟹ Params ⟹ Request ⟹ NonEmptyList[String] ⟹ String
resultDB:  Id ⟹ Params ⟹ Msg ⟹ NonEmptyList[String] ⟹ String
```

What we have done is to decompose the selector morphism from Request to String into one that goes through a non-empty list.

```scala
extractHeaders: Message ⟹ NonEmptryList[String] 
```

and a function `renderBase` that can take a NonEmptyList of values for a given header, to the string that header contributes to the full signing base. This
may be a pure function.
 
```scala
renderBase: NonEmptryList[String] ⟹ String
```
which we can compose
```scala
Selector: Message ⟹ NonEmptyList[String] ⟹ String 
Selector = extractHeaders; renderBase
```
and it should be easy to test the `NonEmptyList[String] => String` function.

### four types of `renderBase` functions

As it turns out there are four types of such functions:
 1. one that just trims the contents and collates them
 2. one that parses each string as a Dictionary
 3. one that also queries the content of the dictionary
 4. one that treats each line after trimming as a a binary string

We can build each of those without knowing anything about the Request or Response structures that the data came from. So that should make for good testing. 
We should also be able to also make a dummy Request and Response types to test with. 

## Stepping out of Kleisli

We can then build on the following general split:

```scala
RequestDB:  ID ⟹ (Params ⟹ (Request ⟹ String))
ResponseDB: ID ⟹ (Params ⟹ (Request|Response ⟹ String))
```
Because `ID` splits cleanly between `@Id` and `Id` we will actually have two 
types of Components, `AtComponents` and `HeaderComponents`, which split both of those functions.

Finally for normal `Id` we need to be aware of the functions

```scala
extractHeaders: Message ⟹ NonEmptryList[String]
renderBase: NonEmptryList[String] ⟹ String
```
which we can compose
```scala
Selector: Message ⟹ NonEmptyList[String] ⟹ String 
Selector = extractHeaders; renderBase
```

## Relation to (dependent) function types

Something feels very close to dependent types in the above.
For example, given the `RequestDB(@query-param)` as a function,
it can only accept attributes of type `QParam`

```scala
type QParam = ("name".type, ValString)
```
So that we have the function
```scala
type QPComponent:  QParam -> Request -> Try[String]
```
On the other hand the type of `RequestDB(@method)` is
```scala
type MethodComponent: () -> Request -> Try[String]
```
If one wanted to type the arguments to RequestDB so that only the correct values could be used, i.e. removing the Try monad wrapping,
then one finds that only a limited number of constructors would 
be available, each a different function. 

In [AtComponents.scala](AtComponents.scala) each method returns
an `OnRequest` or `OnResponse` type, which are essentially functions
```scala
type OnRequest = Params => Try[AtSelector[Request]]
type OnResponse = Params => Try[AtSelector[Response]]
```        
So that 
```scala
def `@path`: OnRequest
```
should be read as 
```scala
type Component(@path): Params => Try[AsSelector[Request]]
```
We remove the first `Try` monad layer in the [AtSelectors.scala](AtSelectors.scala)
where we define the same method by narrowing down precisely the type
of the argument
```scala
def path(onReq: Boolean = false): AtSelector[Request]
```
where `AtSelector[Request]` contains the function
```scala
type signingStr: Request => Try[String]
```

What we have is that 
 1. the `@Id` field corresponds to a function name
 2. the Params correspond to attribute values of the function, i.e. arguments, ie. the Domain!
 3. the value of applying the interpreted params to the function is a new function which takes a Request to a Try[String].

When the a client wants to build an `Input-Signature` it should use the functions made available in [AtSelectors.scala](AtSelectors.scala), as those are type safe.
When a server wants to verify a signature it must interpret each element: 
 1. first the function id
 2. *then* the attributes, which depend on the function
 3. applying the parsed attrbutes to the funciton gives the resulting selector function `Request => Try[String]` 

In this way of looking at things we have a Try for the Id, because we may
not have a function corresponding to the name. 
We have a try for the parameters because they may not be the parameters for that function. 
The function's domain specifies how to interpret the arguments. 
Those arguments tune the resulting function between http messages and resulting strings. 

And that is actually what we have. Well perhaps it topsy turvy. We have the interface [AtComponents](AtComponents.scala) that must be implemented by the frameworks, and which is used by [AtSelectors](AtSelectors.scala) which specifies the constructor functions. 



