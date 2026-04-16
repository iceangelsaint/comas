package org.encryptor4j.factory;

public class DESKeyFactory extends AbsKeyFactory {
   public static final String ALGORITHM = "DES";
   public static final int MAXIMUM_KEY_LENGTH = 64;

   public DESKeyFactory() {
      super("DES", 64);
   }
}
