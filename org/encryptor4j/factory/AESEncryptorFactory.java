package org.encryptor4j.factory;

import java.security.Key;
import org.encryptor4j.Encryptor;

public class AESEncryptorFactory implements EncryptorFactory {
   public final Encryptor messageEncryptor(Key key) {
      return new Encryptor(key, "AES/CBC/PKCS5Padding", 16);
   }

   public final Encryptor streamEncryptor(Key key) {
      return new Encryptor(key, "AES/CTR/NoPadding", 16);
   }
}
