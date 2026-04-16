package org.encryptor4j;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import javax.crypto.KeyAgreement;

public abstract class KeyAgreementPeer {
   private KeyAgreement keyAgreement;
   private KeyPair keyPair;

   public KeyAgreementPeer(KeyAgreement keyAgreement) throws GeneralSecurityException {
      this(keyAgreement, (KeyPair)null);
   }

   public KeyAgreementPeer(KeyAgreement keyAgreement, KeyPair keyPair) throws GeneralSecurityException {
      this.keyAgreement = keyAgreement;
      this.keyPair = keyPair;
   }

   protected abstract KeyPair createKeyPair() throws GeneralSecurityException;

   protected void initialize() throws GeneralSecurityException {
      if (this.keyPair == null) {
         this.keyPair = this.createKeyPair();
      }

      this.keyAgreement.init(this.keyPair.getPrivate());
   }

   public byte[] computeSharedSecret(Key key) throws InvalidKeyException {
      this.keyAgreement.doPhase(key, true);
      return this.keyAgreement.generateSecret();
   }

   public Key getPublicKey() {
      return this.keyPair.getPublic();
   }

   public KeyAgreement getKeyAgreement() {
      return this.keyAgreement;
   }
}
