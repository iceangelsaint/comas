package edu.carleton.cas.resources;

import java.util.TimerTask;

public abstract class AbstractTask extends TimerTask implements LegalityCheck {
   protected final Logger logger;
   protected final ResourceMonitor monitor;
   protected Process process;
   protected boolean running;

   public AbstractTask(Logger logger, ResourceMonitor monitor) {
      this.logger = logger;
      this.monitor = monitor;
      this.running = false;
   }

   public synchronized void close() {
      if (this.process != null) {
         if (this.process.isAlive()) {
            this.process.destroyForcibly();
         }

         this.process = null;
         this.running = false;
      }

   }

   public boolean isRunning() {
      return this.running;
   }

   public boolean isSessionEnded() {
      return this.monitor.properties.containsKey("session.ended");
   }

   public boolean isSessionInitialized() {
      return this.monitor.properties.containsKey("session.initialized");
   }
}
