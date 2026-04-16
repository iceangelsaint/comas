package edu.carleton.cas.background;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.utility.CountDownLatchNotifier;
import java.io.IOException;
import java.lang.Thread.State;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import org.glassfish.jersey.internal.ServiceConfigurationError;

public abstract class Archiver implements ArchiverInterface, Thread.UncaughtExceptionHandler {
   private final LinkedBlockingQueue queue = new LinkedBlockingQueue();
   private final ReentrantLock threadLock;
   private Thread thread;
   protected final AtomicBoolean stopped = new AtomicBoolean(false);
   protected final AtomicBoolean working = new AtomicBoolean(false);
   protected final AtomicReference restarted = new AtomicReference((Object)null);
   protected final Invigilator login;
   protected String target;
   protected final String type;
   protected final String name;
   protected final KeepAliveStatistics statistics = new KeepAliveStatistics();
   protected CountDownLatchNotifier latch;
   protected ReentrantLock lock;
   protected int status;

   public Archiver(Invigilator login, String target, String type, String name) {
      this.login = login;
      this.target = target;
      this.type = type;
      this.name = name;
      this.threadLock = new ReentrantLock();
      this.latch = null;
      this.lock = new ReentrantLock();
      this.thread = this.createThread();
   }

   public void lock() {
      this.lock.lock();
   }

   public void unlock() {
      this.lock.unlock();
   }

   public boolean isLocked() {
      return this.lock.isLocked();
   }

   public void setTarget(String target) {
      this.target = target;
   }

   public String getName() {
      return this.name;
   }

   public KeepAliveStatistics getStatistics() {
      return this.statistics;
   }

   private Thread createThread() {
      this.statistics.incrementStarts();
      this.thread = new Thread(new Worker());
      this.thread.setName(this.name);
      this.thread.setUncaughtExceptionHandler(this);
      return this.thread;
   }

   public boolean keepAlive() {
      this.threadLock.lock();

      try {
         if (this.thread.getState() == State.TERMINATED) {
            if (!this.isProcessing()) {
               return false;
            }

            this.thread = this.createThread();
            return true;
         }
      } finally {
         this.threadLock.unlock();
      }

      return false;
   }

   private String logStatistics(boolean log) {
      Exception e = this.statistics.getLastException();
      String emsg = e == null ? "" : ", Exceptions(" + this.statistics.getTotalExceptions() + "): " + e.toString();
      String msg = String.format("%s service stopped. Starts=%d, Processed=%d, Failures=%d, Backlog=%d, Last=%.03f, Time=%.03f%s", this.name, this.statistics.getTotalStarts(), this.statistics.getTotalProcessed(), this.statistics.getTotalFailures(), this.backlog(), (float)(System.currentTimeMillis() - this.statistics.getTimeOfLastProcessed()) * 0.001F, (float)this.statistics.getTotalTime() * 0.001F, emsg);
      if (log) {
         Logger.log(Level.INFO, msg, "");
      }

      return msg;
   }

   public boolean isProcessing() {
      if (!this.stopped.get()) {
         return true;
      } else {
         return this.working.get() || !this.queue.isEmpty();
      }
   }

   public long expectedCompletionTime() {
      if (this.isProcessing()) {
         long rate = this.statistics.getRate();
         return rate * (long)((this.working.get() ? 1 : 0) + this.backlog());
      } else {
         return 0L;
      }
   }

   private void doTheWork(Object item) throws IOException, InterruptedException {
      this.working.set(true);
      long startWorkTime = System.currentTimeMillis();
      boolean ok = this.doWork(item);
      if (!ok && this.statistics.getFailures() < ClientShared.MAX_FAILURES) {
         try {
            this.statistics.incrementFailures();
            long itemProcessedTime = System.currentTimeMillis();
            this.statistics.addTotalTime(itemProcessedTime - startWorkTime);
            Thread.sleep(ThreadLocalRandom.current().nextLong((long)ClientShared.RETRY_TIME));
         } catch (InterruptedException var7) {
         }
      } else {
         if (!ok && this.statistics.getFailures() == ClientShared.MAX_FAILURES) {
            this.doWorkOnFailure(item);
         }

         this.statistics.resetFailures();
         this.statistics.incrementTotalProcessed();
         long itemProcessedTime = System.currentTimeMillis();
         if (ok) {
            this.statistics.setTimeOfLastProcessed(itemProcessedTime);
         }

         this.statistics.addTotalTime(itemProcessedTime - startWorkTime);
         this.working.set(false);
      }

   }

   public boolean hasBeenRestarted() {
      return this.restarted.get() != null;
   }

   public void logStatisticsIfRestarted(StringBuilder sb) {
      if (this.hasBeenRestarted()) {
         sb.append(this.logStatistics(false));
         sb.append(Utils.printFirstApplicationStackFrameOrException((Throwable)this.restarted.get()));
      }

   }

   public boolean isStopped() {
      return this.stopped.get();
   }

   public void clear() {
      this.queue.clear();
   }

   public void start() {
      this.stopped.set(false);

      try {
         this.thread.start();
      } catch (IllegalThreadStateException var2) {
      }

   }

   public void stop() {
      if (this.stopped.compareAndSet(false, true)) {
         if (this.queue.isEmpty() && !this.working.get()) {
            String var10000 = this.name;
            Logger.output(var10000 + " queue is empty and no in-progress work. Failures: " + this.statistics.getTotalFailures());
            this.logStatisticsAndUpdateLatch();
            return;
         }

         this.threadLock.lock();

         try {
            if (this.thread.isAlive() && !this.working.get()) {
               this.thread.interrupt();

               try {
                  if (this.thread.isAlive() && this.latch == null) {
                     this.thread.join((long)ClientShared.MAX_MSECS_TO_WAIT_TO_END);
                  }
               } catch (InterruptedException var5) {
               }
            }
         } finally {
            this.threadLock.unlock();
         }
      }

   }

   private synchronized void logStatisticsAndUpdateLatch() {
      this.logStatistics(true);
      if (this.latch != null) {
         this.latch.countDown(this.name + " stopped");
         this.latch = null;
      }

   }

   public void stop(CountDownLatchNotifier latch) {
      this.latch = latch;
      this.stop();
   }

   public void put(Object item) {
      if (!this.stopped.get() && this.statistics.getFailures() <= ClientShared.MAX_FAILURES) {
         try {
            this.queue.put(item);
         } catch (InterruptedException var3) {
         }

      }
   }

   public int backlog() {
      return this.queue.size();
   }

   public void uncaughtException(Thread t, Throwable e) {
      if (t == this.thread) {
         this.threadLock.lock();

         try {
            this.restarted.set(e);
            this.thread = this.createThread();
            this.thread.start();
         } catch (IllegalThreadStateException var7) {
         } finally {
            this.threadLock.unlock();
         }
      }

      String msg = String.format("Abnormal termination for %s id %s. Cause: %s", this.getClass().getName(), t.getName(), e.toString());
      Logger.log(edu.carleton.cas.logging.Level.DIAGNOSTIC, msg, "");
   }

   private class Worker implements Runnable {
      public void run() {
         try {
            Archiver.this.working.set(false);
            Archiver.this.statistics.resetFailures();
            Logger.log(Level.INFO, Archiver.this.name, " service started");
            Object item = "";

            while(Archiver.this.isProcessing()) {
               try {
                  if (!Archiver.this.working.get()) {
                     item = Archiver.this.queue.take();
                  }

                  Archiver.this.doTheWork(item);
               } catch (ServiceConfigurationError | InterruptedException var8) {
               } catch (IOException e) {
                  Logger.log(Level.WARNING, e.toString() + " ", item);
               } catch (Exception e) {
                  Logger.log(Level.SEVERE, e.toString() + " ", item);
               }
            }
         } finally {
            Archiver.this.logStatisticsAndUpdateLatch();
         }

      }
   }
}
