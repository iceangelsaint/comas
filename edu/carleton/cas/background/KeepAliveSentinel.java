package edu.carleton.cas.background;

import edu.carleton.cas.background.timers.ExtendedTimer;
import edu.carleton.cas.background.timers.TimerService;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.resources.HardwareAndSoftwareMonitor;
import edu.carleton.cas.ui.DisappearingAlert;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimerTask;

public class KeepAliveSentinel implements ControlInterface {
   final ArrayList services;
   final ExtendedTimer timer;
   int timeInMillis;
   final Invigilator invigilator;

   public KeepAliveSentinel(Invigilator login) {
      this(60000, login);
   }

   public KeepAliveSentinel(int timeInMillis, Invigilator invigilator) {
      this.invigilator = invigilator;
      this.services = new ArrayList();
      this.timer = TimerService.create("KeepAliveSentinel");
      if (timeInMillis < 60000) {
         this.timeInMillis = 60000;
      } else {
         this.timeInMillis = timeInMillis;
      }

   }

   public void register(KeepAliveInterface service) {
      synchronized(this.services) {
         this.services.add(service);
      }
   }

   public void deregister(KeepAliveInterface service) {
      synchronized(this.services) {
         this.services.remove(service);
      }
   }

   public void start() {
      try {
         this.timer.scheduleAtFixedRate(new SentinelTask(), (long)this.timeInMillis, (long)this.timeInMillis);
      } catch (IllegalStateException | IllegalArgumentException e) {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Unable to start keep alive sentinel: " + String.valueOf(e));
      }

   }

   public void setupEndOfSessionEventPowerCheckAndAutomatedReporting() {
      try {
         long endTime = this.invigilator.getSessionEndTime();
         if (endTime > System.currentTimeMillis()) {
            this.timer.schedule(new SessionTimeoutTask(), new Date(endTime));
         }

         int maxReportingFrequency = Math.round((float)this.invigilator.getExamDurationInMinutes());
         long reportingFrequency = (long)Utils.getIntegerOrDefaultInRange(this.invigilator.getProperties(), "report_frequency", 0, 0, maxReportingFrequency);
         if (reportingFrequency != 0L && reportingFrequency < (long)maxReportingFrequency) {
            this.timer.scheduleAtFixedRate(new ReportTask(), (long)this.timeInMillis, reportingFrequency * 60L * 1000L);
         }

         this.schedulePowerCheck();
      } catch (IllegalStateException | IllegalArgumentException e) {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Unable to start keep alive sentinel: " + String.valueOf(e));
      }

      try {
         this.generateSessionReports("start.");
      } catch (Exception var6) {
      }

   }

   private void generateSessionReports(String stateOfSession) {
      this.invigilator.getReportManager().generateSessionReports(stateOfSession, (String)null);
   }

   public void schedulePowerCheck() throws IllegalArgumentException, IllegalStateException {
      this.timer.schedule(new PowerCheckTask(), new Date(this.invigilator.getActualStartTime() + HardwareAndSoftwareMonitor.POWER_EXHAUSTION_THRESHOLD_IN_MINUTES * 60L * 1000L));
      this.timer.schedule(new PowerCheckTask(), new Date(this.invigilator.getEstimatedEndTime() - HardwareAndSoftwareMonitor.POWER_EXHAUSTION_THRESHOLD_IN_MINUTES * 60L * 1000L));
   }

   public void stop() {
      try {
         this.generateSessionReports("end.");
      } catch (Exception var3) {
      }

      try {
         TimerService.destroy(this.timer);
      } catch (Exception var2) {
      }

   }

   private class SentinelTask extends TimerTask {
      public void run() {
         synchronized(KeepAliveSentinel.this.services) {
            try {
               for(KeepAliveInterface s : KeepAliveSentinel.this.services) {
                  if (s.keepAlive()) {
                     s.start();
                     KeepAliveStatistics kas = s.getStatistics();
                     Exception e = kas.getLastException();
                     String emsg = e == null ? "" : ", Exceptions(" + kas.getTotalExceptions() + "): " + e.toString();
                     String msg = String.format("%s service stopped. Starts=%d, Processed=%d, Failures=%d, Time=%.03f%s", s.getName(), kas.getTotalStarts(), kas.getTotalProcessed(), kas.getTotalFailures(), (double)kas.getTotalTime() / (double)1000.0F, emsg);
                     KeepAliveSentinel.this.invigilator.logArchiver.put(Level.DIAGNOSTIC, msg);
                  }
               }
            } catch (Exception var8) {
            }

         }
      }
   }

   private class SessionTimeoutTask extends TimerTask {
      public void run() {
         try {
            KeepAliveSentinel.this.invigilator.endTheSession();
            KeepAliveSentinel.this.invigilator.exitAfterSessionEnd(0);
         } catch (Exception var2) {
         }

      }
   }

   private class PowerCheckTask extends TimerTask {
      public void run() {
         try {
            if (KeepAliveSentinel.this.invigilator.getHardwareAndSoftwareMonitor().getPowerHealth(KeepAliveSentinel.this.invigilator).isRed()) {
               String powerMsg = String.format("You have less than %d mins of power", KeepAliveSentinel.this.invigilator.getHardwareAndSoftwareMonitor().getTimeRemainingEstimate());
               (new DisappearingAlert((long)ClientShared.DISAPPEARING_ALERT_TIMEOUT, 1, 2)).show(powerMsg, "CoMaS Power Information Alert");
            }
         } catch (Exception var2) {
         }

      }
   }

   private class ReportTask extends TimerTask {
      public void run() {
         try {
            KeepAliveSentinel.this.invigilator.getReportManager().instantReport((Object[])null);
            KeepAliveSentinel.this.generateSessionReports("");
         } catch (Exception var2) {
         }

      }
   }
}
