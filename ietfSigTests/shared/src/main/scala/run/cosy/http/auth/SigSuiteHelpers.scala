package run.cosy.http.auth

import bobcats.*
import bobcats.HmacAlgorithm.SHA256
import bobcats.Verifier.{Signature, SigningString}
import cats.effect.Async
import cats.effect.kernel.Sync
import cats.syntax.all.*
import cats.{Functor, Monad, MonadError}
import run.cosy.http.auth.MessageSignature as MsgSig
import run.cosy.http.auth.MessageSignature.SigningF
import run.cosy.http.headers.Rfc8941
import run.cosy.http.headers.Rfc8941.SfString
import scodec.bits.ByteVector

import scala.util.{Failure, Try}

class SigSuiteHelpers[F[_]](using
    // <- todo: why not something less strong than Throwable? (it's not that easy to change)
    ME: MonadError[F, Throwable],
    V: Verifier[F],
    S: Signer[F],
    HMac: Hmac[F]
):
   import bobcats.Verifier.*

   /** this function feels like it could belong to a library, but really a lot depends on the type
     * of key returned. Here it is a PureKeyId, so we assume this was returned directly from
     * previous knowledge of how to associate the keyId with a keySpec...
     */
   def verifierFor(
       keySpec: JWKPublicKeySpec[AsymmetricKeyAlg],
       sig: bobcats.AsymmetricKeyAlg.Signature,
       keyId: SfString
   ): F[MsgSig.SignatureVerifier[F, KeyIdentified]] =
     for
        verifierFn <- V.build(keySpec, sig)
     yield (signingStr: SigningString, signature: Signature) =>
       verifierFn(signingStr, signature).flatMap(bool =>
         if bool then ME.pure(PureKeyId(keyId.asciiStr))
         else
            ME.fromEither(
              Left(InvalidSigException(s"could not verify $keyId with ${keySpec.algorithm} " +
                s"on >${signingStr.decodeAsciiLenient}<"))
            )
       )

   def keySpecsFor(
       keyinfo: bobcats.TestKeyPair
   ): (JWKPublicKeySpec[AsymmetricKeyAlg], JWKPrivateKeySpec[AsymmetricKeyAlg]) =
     (
       JWKPublicKeySpec(keyinfo.publicJwkKey, keyinfo.keyAlg),
       JWKPrivateKeySpec(keyinfo.privateJwkKey, keyinfo.keyAlg)
     )

   //      pemutils.getPublicKeySpec(keyinfo.publicPk8Key, keyinfo.keyAlg)

   import bobcats.{HttpMessageSignaturesV07 as SigV07, HttpMessageSignaturesV13 as SigV13}

   lazy val (rsaPubKey, rsaPrivKey)         = keySpecsFor(SigV13.`test-key-rsa`)
   lazy val (rsaPSSPubKey, rsaPSSPrivKey)   = keySpecsFor(SigV13.`test-key-rsa-pss`)
   lazy val (ecc256PubKey, ecc256PrivKey)   = keySpecsFor(SigV13.`test-key-ecc-p256`)
   lazy val (ed25519PubKey, ed25519PrivKey) = keySpecsFor(SigV07.`test-key-ed25519`)
   import run.cosy.http.utils.StringUtils.*

   /** emulate fetching the signature verification info for the keyids given in the Spec
     */
   def keyidFetcher(keyid: Rfc8941.SfString): F[MsgSig.SignatureVerifier[F, KeyIdentified]] =
      import bobcats.AsymmetricKeyAlg as Asym
      keyid.asciiStr match
         case "test-key-rsa-pss" =>
           verifierFor(rsaPSSPubKey, Asym.`rsa-pss-sha512`, keyid)
         case "test-key-rsa" =>
           verifierFor(rsaPubKey, Asym.`rsa-v1_5-sha256`, keyid)
         case "test-key-ecc-p256" =>
           verifierFor(ecc256PubKey, Asym.`ecdsa-p256-sha256`, keyid)
         case "test-key-ed25519" =>
           verifierFor(ed25519PubKey, Asym.ed25119, keyid)
         case "test-shared-secret" =>
           for
              bytes <- ME.fromEither(ByteVector.fromBase64(
                """uzvJfB4u3N0Jy4T7NZ75MDVcr8zSTInedJtkgcu46YW4XByzNJjxBdtjUkdJPBt\
       bmHhIDi6pcl8jsasjlTMtDQ==""".rfc8792single
              ).toRight(InvalidSigException("cannot parse secret key as bytestring")))
              key <- Hmac[F].importKey(bytes, SHA256)
           yield (base: SigningString, givenSig: Signature) =>
             Hmac[F].digest(key, base).flatMap(calculatedSig =>
               if calculatedSig == givenSig
               then ME.pure(PureKeyId("test-shared-secret"))
               else ME.fromTry(Failure(CryptoException("cannot verify symmetric key")))
             )
         case x => ME.fromEither(Left(new Exception(s"can't get info on sig $x")))

   def signerFor(keyId: Rfc8941.SfString): F[SigningF[F]] =
      import bobcats.AsymmetricKeyAlg as Asym
      keyId.asciiStr match
         case "test-key-rsa-pss" =>
           S.build(rsaPSSPrivKey, Asym.`rsa-pss-sha512`)
         case "test-key-rsa"      => S.build(rsaPrivKey, Asym.`rsa-v1_5-sha256`)
         case "test-key-ecc-p256" => S.build(ecc256PrivKey, Asym.`ecdsa-p256-sha256`)
         case "test-key-ed25519"  => S.build(ed25519PrivKey, Asym.ed25119)
