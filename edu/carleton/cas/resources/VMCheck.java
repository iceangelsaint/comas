package edu.carleton.cas.resources;

import java.util.Properties;

public class VMCheck extends ResourceMonitor implements Runnable {
   public VMCheck(Properties properties) {
      super("vmcheck", "vmcheck", properties);
      this.task = new VMCheckTask((Logger)null, this);
   }

   public void open() {
      this.timer.schedule(this.task, 1000L);
   }

   public void run() {
      this.task.run();
   }
}
