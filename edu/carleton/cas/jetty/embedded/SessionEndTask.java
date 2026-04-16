package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.utility.Sleeper;
import java.util.concurrent.atomic.AtomicBoolean;

public class SessionEndTask implements Runnable {
   public static final String NAME = "session end";
   private static AtomicBoolean isRunning = new AtomicBoolean(false);
   private static int MAX_TRIES = 5;
   private final Invigilator invigilator;
   private final ProgressIndicator progressIndicator;

   public SessionEndTask(Invigilator invigilator) {
      this(invigilator, (ProgressIndicator)null, (String)null);
   }

   public SessionEndTask(Invigilator invigilator, ProgressIndicator progressIndicator) {
      this(invigilator, progressIndicator, (String)null);
   }

   public SessionEndTask(Invigilator invigilator, ProgressIndicator progressIndicator, String message) {
      this.invigilator = invigilator;
      this.progressIndicator = progressIndicator;
      if (message != null) {
         invigilator.logArchiver.put(Level.LOGGED, message);
      }

   }

   public void run() {
      if (isRunning.compareAndSet(false, true)) {
         Thread t = Thread.currentThread();
         t.setName("session end");
         t.setUncaughtExceptionHandler(this.invigilator);
         String[] processes = this.invigilator.processesAccessingFolderOfInterest();

         for(String process : processes) {
            this.invigilator.getHardwareAndSoftwareMonitor().addFinalizedProcess(process);
         }

         this.invigilator.removeShutdownHook();
         this.invigilator.endTheSession();
         if (this.progressIndicator != null) {
            for(int tries = 1; !this.invigilator.isInInvigilatorState(InvigilatorState.ended) && tries < MAX_TRIES; ++tries) {
               Sleeper.sleep(1000);
            }
         }

         isRunning.set(false);
         System.exit(0);
      }
   }
}
