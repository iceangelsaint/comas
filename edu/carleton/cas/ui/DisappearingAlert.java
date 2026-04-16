package edu.carleton.cas.ui;

import edu.carleton.cas.background.timers.ExtendedTimer;
import edu.carleton.cas.background.timers.TimerService;
import edu.carleton.cas.utility.IconLoader;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class DisappearingAlert {
   private static int ILLEGAL_OPTION_TYPE = -1;
   private static ExtendedTimer defaultTimer = TimerService.getInstance();
   private int numberOfAlerts;
   private AtomicBoolean active;
   private int maximumAlerts;
   private long timeout;
   private ExtendedTimer timer;
   private Alert alert;
   private int type;
   private AtomicReference runOnCloseReference;
   private AtomicReference runOnTimeoutReference;
   private AtomicReference runOnOpenReference;
   private int optionType;
   private int choice;
   private boolean runOnSeparateThread;

   public DisappearingAlert() {
      this((ExtendedTimer)null, 0L, Integer.MAX_VALUE, 1, ILLEGAL_OPTION_TYPE);
   }

   public DisappearingAlert(long timeout) {
      this(timeout, Integer.MAX_VALUE, 1);
   }

   public DisappearingAlert(long timeout, int maximumAlerts) {
      this(timeout, maximumAlerts, 1);
   }

   public DisappearingAlert(long timeout, int maximumAlerts, int type) {
      this(defaultTimer, timeout, maximumAlerts, type, ILLEGAL_OPTION_TYPE);
   }

   public DisappearingAlert(ExtendedTimer timer, long timeout, int maximumAlerts, int type, int optionType) {
      this.numberOfAlerts = 0;
      this.maximumAlerts = maximumAlerts;
      this.active = new AtomicBoolean(false);
      this.timeout = timeout;
      this.timer = timer;
      this.alert = null;
      this.type = type;
      this.optionType = optionType;
      this.choice = -1;
      this.runOnSeparateThread = false;
      this.runOnCloseReference = new AtomicReference();
      this.runOnTimeoutReference = new AtomicReference();
      this.runOnOpenReference = new AtomicReference();
   }

   public int getChoice() {
      return this.optionType > ILLEGAL_OPTION_TYPE ? this.choice : -1;
   }

   public void setOptionType(int optionType) {
      this.optionType = optionType;
   }

   public void setRunOnOpen(Runnable runOnOpen) {
      this.setRunOnOpen(runOnOpen, false);
   }

   public void setRunOnOpen(Runnable runOnOpen, boolean runOnSeparateThread) {
      this.runOnSeparateThread = runOnSeparateThread;
      this.runOnOpenReference.set(runOnOpen);
   }

   private void runOnOpen() {
      Runnable runIt = (Runnable)this.runOnOpenReference.getAndSet((Object)null);
      if (runIt != null) {
         if (this.runOnSeparateThread) {
            (new Thread(runIt)).start();
         } else {
            runIt.run();
         }
      }

   }

   public void setRunOnCloseRegardless(Runnable runOnCloseRegardless) {
      this.setRunOnCloseRegardless(runOnCloseRegardless, false);
   }

   public void setRunOnCloseRegardless(Runnable runOnCloseRegardless, boolean runOnSeparateThread) {
      this.runOnSeparateThread = runOnSeparateThread;
      this.setRunOnClose(runOnCloseRegardless);
      this.setRunOnTimeout(runOnCloseRegardless);
   }

   public void setRunOnClose(Runnable runOnClose) {
      this.runOnCloseReference.set(runOnClose);
   }

   public void setRunOnTimeout(Runnable runOnTimeout) {
      this.runOnTimeoutReference.set(runOnTimeout);
   }

   private void runOnClose() {
      Runnable runIt = (Runnable)this.runOnCloseReference.getAndSet((Object)null);
      if (runIt != null) {
         if (this.runOnSeparateThread) {
            (new Thread(runIt)).start();
         } else {
            runIt.run();
         }
      }

   }

   private void runOnTimeout() {
      Runnable runIt = (Runnable)this.runOnTimeoutReference.getAndSet((Object)null);
      if (runIt != null) {
         if (this.runOnSeparateThread) {
            (new Thread(runIt)).start();
         } else {
            runIt.run();
         }
      }

   }

   public Alert show(String msg) {
      return this.show(msg, "CoMaS Alert", this.timeout);
   }

   public Alert show(String msg, String title) {
      return this.show(msg, title, this.timeout);
   }

   public Alert show(String msg, String title, long _timeout) {
      if (this.active.compareAndSet(false, true)) {
         this.alert = new Alert(msg, title, _timeout);
         (new Thread(this.alert)).start();
      }

      return this.alert;
   }

   public boolean isActive() {
      return this.active.get();
   }

   public Timer getTimer() {
      return this.timer;
   }

   public void setTimer(ExtendedTimer timer) {
      this.timer = timer;
   }

   public int getMaximumAlerts() {
      return this.maximumAlerts;
   }

   public void setMaximumAlerts(int maximumAlerts) {
      this.maximumAlerts = maximumAlerts;
   }

   public long getTimeout() {
      return this.timeout;
   }

   public void setTimeout(long timeout) {
      this.timeout = timeout;
   }

   public void clear() {
      this.numberOfAlerts = 0;
   }

   public void close() {
      if (this.alert != null && this.active.compareAndSet(true, false)) {
         this.alert.close();
      }

   }

   public boolean isTimedOut() {
      return this.alert != null && this.alert.isTimedOut();
   }

   public class Alert implements Runnable {
      private final String msg;
      private final String title;
      private final long timeout;
      private JFrame frame;
      private final AtomicBoolean timedOut;
      private AlertTimeoutTask timeoutTask;

      Alert(String msg, String title, long timeout) {
         this.msg = msg;
         this.title = title;
         this.timeout = timeout;
         this.frame = null;
         this.timedOut = new AtomicBoolean(false);
         this.timeoutTask = null;
      }

      public void run() {
         if (DisappearingAlert.this.numberOfAlerts < DisappearingAlert.this.maximumAlerts) {
            this.frame = new JFrame();
            this.frame.setAlwaysOnTop(true);
            if (DisappearingAlert.this.timer != null && this.timeout > 0L) {
               try {
                  this.timeoutTask = DisappearingAlert.this.new AlertTimeoutTask(this);
                  DisappearingAlert.this.timer.schedule(this.timeoutTask, this.timeout);
               } catch (IllegalStateException var2) {
                  DisappearingAlert.this.timer = null;
               }
            }

            DisappearingAlert.this.runOnOpen();
            if (DisappearingAlert.this.optionType > DisappearingAlert.ILLEGAL_OPTION_TYPE) {
               DisappearingAlert.this.choice = JOptionPane.showConfirmDialog(this.frame, this.msg, this.title, DisappearingAlert.this.optionType, DisappearingAlert.this.type, IconLoader.getIcon(DisappearingAlert.this.type));
            } else {
               JOptionPane.showMessageDialog(this.frame, this.msg, this.title, DisappearingAlert.this.type, IconLoader.getIcon(DisappearingAlert.this.type));
            }

            this.close();
            DisappearingAlert.this.runOnClose();
            ++DisappearingAlert.this.numberOfAlerts;
         }

         DisappearingAlert.this.active.set(false);
      }

      private void closeOnTimeout() {
         this.timedOut.set(true);
         this.closeDialog();
         DisappearingAlert.this.runOnTimeout();
      }

      public void close() {
         if (this.timeoutTask != null) {
            this.timeoutTask.cancel();
         }

         this.closeDialog();
      }

      private void closeDialog() {
         if (this.frame != null) {
            this.frame.dispose();
            this.frame = null;
         }

      }

      public boolean isTimedOut() {
         return this.timedOut.get();
      }
   }

   private class AlertTimeoutTask extends TimerTask {
      private final Alert alert;

      public AlertTimeoutTask(Alert alert) {
         this.alert = alert;
      }

      public void run() {
         try {
            this.alert.closeOnTimeout();
         } catch (Exception var2) {
         }

      }

      public boolean cancel() {
         super.cancel();
         this.alert.closeDialog();
         return true;
      }
   }
}
