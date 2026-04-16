package edu.carleton.cas.background;

import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class KeepAliveStatistics {
   protected AtomicInteger failures;
   protected AtomicInteger totalFailures;
   protected AtomicInteger totalProcessed;
   protected AtomicInteger totalStarts;
   protected AtomicInteger totalExceptions;
   protected AtomicLong totalTime;
   protected AtomicLong timeOfLastProcessed;
   protected AtomicReference lastException;

   public KeepAliveStatistics() {
      this.failures = new AtomicInteger(0);
      this.totalFailures = new AtomicInteger(0);
      this.totalProcessed = new AtomicInteger(0);
      this.totalStarts = new AtomicInteger(0);
      this.totalExceptions = new AtomicInteger(0);
      this.totalTime = new AtomicLong(0L);
      this.timeOfLastProcessed = new AtomicLong(0L);
      this.lastException = new AtomicReference();
   }

   public KeepAliveStatistics(KeepAliveStatistics kas) {
      this.failures = new AtomicInteger(kas.getFailures());
      this.totalFailures = new AtomicInteger(kas.getTotalFailures());
      this.totalProcessed = new AtomicInteger(kas.getTotalProcessed());
      this.totalStarts = new AtomicInteger(kas.getTotalStarts());
      this.totalExceptions = new AtomicInteger(kas.getTotalExceptions());
      this.totalTime = new AtomicLong(kas.getTotalTime());
      this.timeOfLastProcessed = new AtomicLong(kas.getTimeOfLastProcessed());
      this.lastException = new AtomicReference(kas.getLastException());
   }

   public int getTotalExceptions() {
      return this.totalExceptions.get();
   }

   public Exception getLastException() {
      return (Exception)this.lastException.get();
   }

   public int setLastException(Exception e) {
      this.lastException.set(e);
      return this.totalExceptions.incrementAndGet();
   }

   protected int incrementFailures() {
      this.totalFailures.incrementAndGet();
      return this.failures.incrementAndGet();
   }

   protected int incrementStarts() {
      return this.totalStarts.incrementAndGet();
   }

   protected int incrementTotalProcessed() {
      return this.totalProcessed.incrementAndGet();
   }

   protected long addTotalTime(long delta) {
      return this.totalTime.addAndGet(delta);
   }

   protected void resetFailures() {
      this.failures.set(0);
   }

   protected int getFailures() {
      return this.failures.get();
   }

   protected int getTotalFailures() {
      return this.totalFailures.get();
   }

   protected int getTotalProcessed() {
      return this.totalProcessed.get();
   }

   protected int getTotalStarts() {
      return this.totalStarts.get();
   }

   protected long getTotalTime() {
      return this.totalTime.get();
   }

   protected long getTimeOfLastProcessed() {
      return this.timeOfLastProcessed.get();
   }

   protected void setTimeOfLastProcessed(long time) {
      this.timeOfLastProcessed.set(time);
   }

   protected long getRate(KeepAliveStatistics kas) {
      if (kas == null) {
         return 0L;
      } else {
         long delta_t = this.getTotalTime() - kas.getTotalTime();
         long delta_p = (long)(this.getTotalProcessed() - kas.getTotalProcessed());
         return delta_p == 0L ? 0L : delta_t / delta_p;
      }
   }

   protected long getRate() {
      long numberProcessed = (long)this.getTotalProcessed();
      return numberProcessed == 0L ? 0L : this.getTotalTime() / numberProcessed;
   }

   public void reportAsTableRow(String name, StringBuffer sb, SimpleDateFormat sdf) {
      sb.append("<tr>");
      sb.append("<td>");
      sb.append(name);
      sb.append("</td>");
      sb.append("<td>");
      sb.append(this.getTotalStarts());
      sb.append("</td>");
      sb.append("<td>");
      sb.append(this.getTotalFailures());
      sb.append("</td>");
      sb.append("<td>");
      int totalProcessed = this.getTotalProcessed();
      sb.append(totalProcessed);
      sb.append("</td>");
      sb.append("<td>");
      sb.append(this.getRate());
      sb.append("</td>");
      sb.append("<td>");
      sb.append(this.getTotalExceptions());
      sb.append("</td>");
      sb.append("<td>");
      sb.append((double)this.getTotalTime() / (double)1000.0F);
      sb.append("</td>");
      sb.append("<td>");
      sb.append(totalProcessed == 0 ? "-" : sdf.format(this.getTimeOfLastProcessed()));
      sb.append("</td>");
      sb.append("<td>");
      Exception e = this.getLastException();
      sb.append(e == null ? "" : e.toString());
      sb.append("</td>");
      sb.append("</tr>");
   }
}
