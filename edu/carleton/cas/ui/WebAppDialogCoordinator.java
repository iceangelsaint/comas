package edu.carleton.cas.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class WebAppDialogCoordinator {
   private static final WebAppDialogCoordinator singleton = new WebAppDialogCoordinator(1);
   public final CountDownLatch uiCoordinator;

   private WebAppDialogCoordinator(int count) {
      this.uiCoordinator = new CountDownLatch(count);
   }

   public static CountDownLatch getCoordinator() {
      return singleton.uiCoordinator;
   }

   public static void coordinate() {
      try {
         singleton.uiCoordinator.await();
      } catch (Exception var1) {
      }

   }

   public static void coordinate(long millis) {
      coordinate(millis, TimeUnit.MILLISECONDS);
   }

   public static void coordinate(long amount, TimeUnit unit) {
      try {
         singleton.uiCoordinator.await(amount, unit);
      } catch (Exception var4) {
      }

   }

   public static void sync() {
      singleton.uiCoordinator.countDown();
   }
}
