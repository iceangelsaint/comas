package org.encryptor4j.util;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.Base64;
import org.encryptor4j.Encryptor;
import org.encryptor4j.factory.EncryptorFactory;
import org.encryptor4j.factory.KeyFactory;

public class TextEncryptor {
   private Encryptor encryptor;

   public TextEncryptor() {
      this(KeyFactory.AES.randomKey());
   }

   public TextEncryptor(String password) {
      this(KeyFactory.AES.keyFromPassword(password.toCharArray()));
   }

   public TextEncryptor(Key key) {
      this(EncryptorFactory.AES.messageEncryptor(key));
   }

   public TextEncryptor(Encryptor encryptor) {
      this.encryptor = encryptor;
   }

   public String encrypt(String message) throws GeneralSecurityException {
      byte[] bytes = this.encryptor.encrypt(message.getBytes());
      return Base64.getEncoder().encodeToString(bytes);
   }

   public String decrypt(String message) throws GeneralSecurityException {
      byte[] bytes = Base64.getDecoder().decode(message);
      return new String(this.encryptor.decrypt(bytes));
   }

   public Encryptor getEncryptor() {
      return this.encryptor;
   }
}
