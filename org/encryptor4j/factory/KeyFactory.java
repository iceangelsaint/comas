package org.encryptor4j.factory;

import java.security.Key;

public interface KeyFactory {
   KeyFactory AES = new AESKeyFactory();
   KeyFactory DES = new DESKeyFactory();

   Key keyFromPassword(char[] var1);

   Key randomKey();

   Key randomKey(int var1);
}
