package org.encryptor4j.factory;

import java.security.Key;
import org.encryptor4j.Encryptor;

public interface EncryptorFactory {
   EncryptorFactory AES = new AESEncryptorFactory();
   EncryptorFactory DES = new DESEncryptorFactory();

   Encryptor messageEncryptor(Key var1);

   Encryptor streamEncryptor(Key var1);
}
