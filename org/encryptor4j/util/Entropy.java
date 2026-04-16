package org.encryptor4j.util;

public class Entropy {
   private Entropy() {
   }

   public static final double shannon(byte[] bytes) {
      int n = bytes.length;
      long[] values = new long[256];

      for(int i = 0; i < n; ++i) {
         ++values[bytes[i] - -128];
      }

      double entropy = (double)0.0F;
      double log256 = Math.log((double)256.0F);

      for(long count : values) {
         if (count != 0L) {
            double p = (double)count / (double)n;
            entropy -= p * (Math.log(p) / log256);
         }
      }

      return entropy;
   }

   public static final double shannonSequence(byte[] bytes) {
      byte[] diffs = new byte[bytes.length - 1];
      int i = 0;

      for(int n = diffs.length; i < n; ++i) {
         diffs[i] = (byte)(bytes[i + 1] - bytes[i]);
      }

      return shannon(diffs);
   }
}
