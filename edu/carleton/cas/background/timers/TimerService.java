package edu.carleton.cas.background.timers;

import java.util.Date;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public abstract class TimerService {
   private static final String GLOBAL_TIMER = "Global_Timer";
   private static final ExtendedTimer instance = create("Global_Timer");
   private static final ConcurrentHashMap map = new ConcurrentHashMap();

   public static ExtendedTimer get(String name) {
      return (ExtendedTimer)map.get(name);
   }

   public static void put(String name, ExtendedTimer timer) {
      map.put(name, timer);
   }

   public static ExtendedTimer create(String name) {
      ExtendedTimer timer = new ExtendedTimer(name);
      put(name, timer);
      return timer;
   }

   public static boolean destroy(String name) {
      ExtendedTimer timer = (ExtendedTimer)map.remove(name);
      if (timer != null) {
         timer.cancel();
      }

      return timer != null;
   }

   public static boolean destroy(ExtendedTimer timer) {
      return destroy(timer.getName());
   }

   public static ExtendedTimer getInstance() {
      return instance;
   }

   public static void schedule(TimerTask task, long delay) {
      instance.schedule(task, delay);
   }

   public static void schedule(TimerTask task, long delay, long period) {
      instance.schedule(task, delay, period);
   }

   public static void schedule(TimerTask task, Date firstDate, long period) {
      instance.schedule(task, firstDate, period);
   }

   public static void schedule(TimerTask task, Date date) {
      instance.schedule(task, date);
   }

   public static void scheduleAtFixedRate(TimerTask task, long delay, long period) {
      instance.scheduleAtFixedRate(task, delay, period);
   }

   public static void scheduleAtFixedRate(TimerTask task, Date firstDate, long period) {
      instance.scheduleAtFixedRate(task, firstDate, period);
   }

   public static void scheduleRandom(TimerTask task, long lowerBound, long upperBound) {
      long delay = ThreadLocalRandom.current().nextLong(lowerBound, upperBound);
      schedule(task, delay);
   }

   public static RepeatingTimerTask scheduleRandomRepeating(RepeatingTimerTask task) {
      scheduleRandom(task, task.getLowerBound(), task.getUpperBound());
      return task;
   }

   public static RepeatingTimerTask scheduleRandomRepeating(TimerTask task, long lowerBound, long upperBound) {
      RepeatingTimerTask rtt = new RepeatingTimerTask(instance, task, lowerBound, upperBound);
      return scheduleRandomRepeating(rtt);
   }

   public static int purge() {
      return instance.purge();
   }

   public static void cancel() {
      instance.cancel();
   }

   public static void cancelAll() {
      map.forEach((k, v) -> v.cancel());
   }

   public static void stop() {
      cancelAll();
      clear();
      cancel();
   }

   public static void clear() {
      map.clear();
   }
}
