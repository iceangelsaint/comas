package edu.carleton.cas.utility;

import edu.carleton.cas.jetty.embedded.ProgressIndicator;
import java.util.concurrent.CountDownLatch;

public final class CountDownLatchNotifier extends CountDownLatch {
   private final ProgressIndicator progressIndicator;

   public CountDownLatchNotifier(int count, ProgressIndicator progressIndicator) {
      super(count);
      this.progressIndicator = progressIndicator;
   }

   public void countDown(String message) {
      super.countDown();
      this.progressIndicator.setProgressMessage(message);
   }
}
