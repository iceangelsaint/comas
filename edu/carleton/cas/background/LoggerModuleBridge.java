package edu.carleton.cas.background;

import edu.carleton.cas.events.Event;
import edu.carleton.cas.events.EventListener;
import java.util.logging.Level;

public final class LoggerModuleBridge implements Logger {
   private final LogArchiver log;

   public LoggerModuleBridge(LogArchiver log) {
      this.log = log;
   }

   public void put(Level level, String description) {
      if (level != null && description != null) {
         this.log.put(level, description);
      }
   }

   public void put(Level level, String description, Object[] args) {
      if (level != null && description != null) {
         this.log.put(level, description, args);
      }
   }

   public void deregister(EventListener arg0) {
      this.log.deregister(arg0);
   }

   public void publish(Event arg0) {
      this.log.publish(arg0);
   }

   public void register(EventListener arg0) {
      this.log.register(arg0);
   }
}
