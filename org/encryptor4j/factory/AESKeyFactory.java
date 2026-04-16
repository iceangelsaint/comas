package org.encryptor4j.factory;

public class AESKeyFactory extends AbsKeyFactory {
   public static final String ALGORITHM = "AES";
   public static final int MAXIMUM_KEY_LENGTH = 256;

   public AESKeyFactory() {
      super("AES", 256);
   }
}
