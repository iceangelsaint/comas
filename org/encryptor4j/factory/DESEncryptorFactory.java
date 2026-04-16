package org.encryptor4j.factory;

import java.security.Key;
import org.encryptor4j.Encryptor;

public class DESEncryptorFactory implements EncryptorFactory {
   public final Encryptor messageEncryptor(Key key) {
      return new Encryptor(key, "DES/CBC/PKCS5Padding", 16);
   }

   public final Encryptor streamEncryptor(Key key) {
      return new Encryptor(key, "DES/CTR/NoPadding", 16);
   }
}
