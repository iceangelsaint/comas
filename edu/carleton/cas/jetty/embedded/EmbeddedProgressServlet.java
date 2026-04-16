package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.exam.Invigilator;

public abstract class EmbeddedProgressServlet extends EmbeddedServlet implements ProgressIndicator {
   private int progress = 0;
   private String message = "";

   public EmbeddedProgressServlet(Invigilator invigilator) {
      super(invigilator);
   }

   public synchronized void incrementProgress(int increment) {
      this.setProgress(this.progress + increment);
   }

   public synchronized int getProgress() {
      return this.progress;
   }

   public synchronized String getProgressMessage() {
      return this.message;
   }

   public synchronized void setProgress(int progress) {
      if (progress >= 0 && progress <= 100) {
         this.progress = progress;
      }

   }

   public synchronized void setProgressMessage(String message) {
      if (message != null) {
         this.message = message;
      }

   }
}
