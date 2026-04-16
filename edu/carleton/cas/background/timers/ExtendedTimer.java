package edu.carleton.cas.background.timers;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;

public class ExtendedTimer extends Timer {
   private final String name;

   public ExtendedTimer(String name) {
      super(name);
      this.name = name;
   }

   public void scheduleRandom(TimerTask task, long lowerBound, long upperBound) throws IllegalStateException, IllegalArgumentException, NullPointerException {
      long delay = ThreadLocalRandom.current().nextLong(lowerBound, upperBound);
      this.schedule(task, delay);
   }

   public RepeatingTimerTask scheduleRandomRepeating(RepeatingTimerTask task) throws IllegalStateException, IllegalArgumentException, NullPointerException {
      this.scheduleRandom(task, task.getLowerBound(), task.getUpperBound());
      return task;
   }

   public RepeatingTimerTask scheduleRandomRepeating(TimerTask task, long lowerBound, long upperBound) throws IllegalStateException, IllegalArgumentException, NullPointerException {
      RepeatingTimerTask rtt = new RepeatingTimerTask(this, task, lowerBound, upperBound);
      return this.scheduleRandomRepeating(rtt);
   }

   public String getName() {
      return this.name;
   }

   public String toString() {
      return "ExtendedTimer(" + this.name + ")";
   }
}
