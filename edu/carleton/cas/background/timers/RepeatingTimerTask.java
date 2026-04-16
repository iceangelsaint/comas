package edu.carleton.cas.background.timers;

import java.util.TimerTask;

public class RepeatingTimerTask extends TimerTask {
   private final TimerTask task;
   private final long lowerBound;
   private final long upperBound;
   private final ExtendedTimer timer;
   private boolean cancelled;

   public RepeatingTimerTask(ExtendedTimer timer, TimerTask task, long lowerBound, long upperBound) throws NullPointerException, IllegalStateException {
      if (timer == null) {
         throw new NullPointerException("No timer provided");
      } else if (task == null) {
         throw new NullPointerException("No task provided");
      } else if (lowerBound < 0L) {
         throw new IllegalStateException("Illegal lower bound");
      } else if (upperBound >= 0L && upperBound > lowerBound) {
         this.task = task;
         this.lowerBound = lowerBound;
         this.upperBound = upperBound;
         this.timer = timer;
         this.cancelled = false;
      } else {
         throw new IllegalStateException("Illegal upper bound");
      }
   }

   public void run() {
      if (!this.cancelled) {
         try {
            this.task.run();
         } catch (Exception var10) {
         } finally {
            try {
               if (!this.cancelled) {
                  this.timer.scheduleRandomRepeating(this.task, this.lowerBound, this.upperBound);
               }
            } catch (IllegalStateException var9) {
            }

         }

      }
   }

   public long getLowerBound() {
      return this.lowerBound;
   }

   public long getUpperBound() {
      return this.upperBound;
   }

   public boolean cancel() {
      this.cancelled = true;
      this.task.cancel();
      return super.cancel();
   }
}
