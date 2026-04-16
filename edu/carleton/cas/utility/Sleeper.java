package edu.carleton.cas.utility;

import java.util.concurrent.ThreadLocalRandom;

public abstract class Sleeper {
   public static void sleep(int millis, int extraRandomMillis) {
      int randomMillis = ThreadLocalRandom.current().nextInt(extraRandomMillis);
      sleep(millis + randomMillis);
   }

   public static void sleep(int millis) {
      long now = System.currentTimeMillis();
      long end = now + (long)millis;

      while(end - now > 0L) {
         try {
            Thread.sleep(end - now);
         } catch (InterruptedException var9) {
         } finally {
            now = System.currentTimeMillis();
         }
      }

   }

   public static void sleepAndExit(int millis, int status) {
      sleep(millis);
      System.exit(status);
   }

   public static void sleepAndRun(int millis, Runnable runnable) {
      sleep(millis);
      runnable.run();
   }

   public static void sleepRunAndExit(int millis, Runnable runnable, int status) {
      sleepAndRun(millis, runnable);
      System.exit(status);
   }
}
