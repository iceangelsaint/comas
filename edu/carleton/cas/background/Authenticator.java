package edu.carleton.cas.background;

import edu.carleton.cas.exam.Invigilator;
import java.util.concurrent.atomic.AtomicBoolean;

public class Authenticator implements Runnable {
   private static AtomicBoolean isRunning = new AtomicBoolean(false);
   private final Invigilator invigilator;
   private final String state;

   public Authenticator(Invigilator invigilator) {
      this(invigilator, "Logging in");
   }

   public Authenticator(Invigilator invigilator, String state) {
      this.invigilator = invigilator;
      this.state = state;
   }

   public static boolean isRunning() {
      return isRunning.get();
   }

   public void run() {
      if (isRunning.compareAndSet(false, true)) {
         try {
            this.invigilator.setStateAndAuthenticate(this.state);
         } finally {
            isRunning.set(false);
         }

      }
   }
}
