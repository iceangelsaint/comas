package edu.carleton.cas.background;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.reporting.ReportManager.ProblemStatus;
import java.io.File;
import java.io.FileFilter;
import java.util.Date;

public class ArchiveSentinel implements KeepAliveInterface, InitializerInterface {
   private static final FileFilter ARCHIVE_FILE_FILTER = new FileFilter() {
      public boolean accept(File file) {
         return !file.isHidden() && file.canRead() && file.getName().endsWith(".zip");
      }
   };
   private static final float DISK_SPACE_THRESHOLD = 5.0F;
   long lastArchiveTimeInMillis;
   int numberOfArchives;
   boolean problem = false;
   final Invigilator login;
   long threshold;
   long lastRate;
   KeepAliveStatistics lastStatistics;

   public ArchiveSentinel(Invigilator login, long threshold) {
      this.login = login;
      this.numberOfArchives = 0;
      this.lastArchiveTimeInMillis = 0L;
      this.threshold = threshold;
      this.lastRate = 0L;
      this.lastStatistics = new KeepAliveStatistics();
   }

   public String getName() {
      return "Archives";
   }

   public KeepAliveStatistics getStatistics() {
      return this.login.examArchiver.getStatistics();
   }

   public void start() {
      this.checkForLowFreeDiskSpace(ClientShared.DIR);
   }

   public void stop() {
   }

   public void init() {
      this.lastArchiveTimeInMillis = System.currentTimeMillis();
      this.login.examArchiver.getStatistics().setTimeOfLastProcessed(this.lastArchiveTimeInMillis);
   }

   public boolean keepAlive() {
      String dirString = ClientShared.getArchivesDirectory(this.login.getCourse(), this.login.getActivity());
      File archivesDir = new File(dirString);
      if (ClientShared.AUTO_ARCHIVE) {
         long timeToWaitToCheck = 1000L * (long)ClientShared.AUTO_ARCHIVE_FREQUENCY * (long)(ClientShared.MIN_INTERVAL + ClientShared.MAX_INTERVAL);
         long timeNow = System.currentTimeMillis();
         if (this.lastArchiveTimeInMillis == 0L) {
            this.lastArchiveTimeInMillis = timeNow;
            return false;
         }

         File[] files = archivesDir.listFiles(ARCHIVE_FILE_FILTER);
         if (files != null) {
            if (files.length <= this.numberOfArchives) {
               float freeSpaceInGB = (float)archivesDir.getFreeSpace() * 1.0F / 1.0737418E9F;
               if (files.length == this.numberOfArchives) {
                  if (!this.login.isInitialized() || timeNow - this.lastArchiveTimeInMillis < timeToWaitToCheck) {
                     return false;
                  }

                  this.problem = true;
                  this.login.logArchiver.put(Level.DIAGNOSTIC, String.format("Archiving problem? Number of archives (%d) unchanged since last check at %s, %.03f seconds ago. Free space (%.02f GB)", this.numberOfArchives, (new Date(this.lastArchiveTimeInMillis)).toString(), (double)(timeNow - this.lastArchiveTimeInMillis) / (double)1000.0F, freeSpaceInGB), new Object[]{"problem", "suspended", "deleted_archives", "set"});
               } else {
                  this.problem = true;
                  this.login.logArchiver.put(Level.DIAGNOSTIC, String.format("Archive problem. Had %d archives at %s. Now have %d archives. Free space (%.02f GB)", this.numberOfArchives, (new Date(this.lastArchiveTimeInMillis)).toString(), files.length, freeSpaceInGB), this.login.createProblemSetEvent("deleted_archives"));
               }
            } else {
               this.lastArchiveTimeInMillis = timeNow;
               this.checkForLowFreeDiskSpace(dirString);
               if (this.problem) {
                  this.problem = false;
                  this.login.logArchiver.put(Level.DIAGNOSTIC, "Archiving okay", this.login.createProblemClearEvent("deleted_archives"));
                  if (this.login.getReportManager().hasProblemWithStatus("suspended", ProblemStatus.set)) {
                     this.login.createProblemClearEvent("suspended");
                  }
               }
            }

            this.numberOfArchives = files.length;
         }

         Archiver ea = this.login.examArchiver;
         if (ea != null) {
            KeepAliveStatistics kas = ea.getStatistics();
            if (kas.getTotalProcessed() > 0) {
               long rate = kas.getRate();
               if (this.lastRate != rate) {
                  long currentRate = kas.getRate(this.lastStatistics);
                  String slowMessage;
                  if (currentRate <= this.threshold && currentRate != 0L) {
                     if (currentRate > rate) {
                        slowMessage = " (↑)";
                     } else if (currentRate < rate) {
                        slowMessage = " (↓)";
                     } else {
                        slowMessage = "";
                     }
                  } else if (currentRate == 0L) {
                     slowMessage = " (ZERO ⚠)";
                  } else if (currentRate > rate) {
                     slowMessage = " (SLOW ↑)";
                  } else if (currentRate < rate) {
                     slowMessage = " (SLOW ↓)";
                  } else {
                     slowMessage = " (SLOW)";
                  }

                  Exception e = kas.getLastException();
                  String emsg = e == null ? "" : ", Exceptions(" + kas.getTotalExceptions() + "): " + e.toString();
                  String msg = String.format("%s:%s Starts=%d, Processed=%d, Failures=%d, Backlog=%d, Last=%.03f seconds, Rate(session)=%d msecs/upload, Rate(now)=%d msecs/upload, Time=%.03f seconds%s", this.getName(), slowMessage, kas.getTotalStarts(), kas.getTotalProcessed(), kas.getTotalFailures(), ea.backlog(), (float)(timeNow - kas.getTimeOfLastProcessed()) * 0.001F, rate, currentRate, (float)kas.getTotalTime() * 0.001F, emsg);
                  if (currentRate <= this.threshold && currentRate != 0L) {
                     this.login.logArchiver.put(Level.DIAGNOSTIC, msg, this.login.createProblemClearEvent("slow_archive_upload"));
                  } else {
                     this.login.logArchiver.put(Level.DIAGNOSTIC, msg, this.login.createProblemSetEvent("slow_archive_upload"));
                  }

                  this.lastRate = rate;
               }
            }

            this.lastStatistics = new KeepAliveStatistics(kas);
         }
      }

      return false;
   }

   private void checkForLowFreeDiskSpace(String dir) {
      File fDir = new File(dir);
      float freeSpaceInGB = (float)fDir.getFreeSpace() * 1.0F / 1.0737418E9F;
      if (freeSpaceInGB < 5.0F) {
         this.login.logArchiver.put(Level.DIAGNOSTIC, String.format("Low free disk space for archives (%.02f GB)", freeSpaceInGB));
      }

   }
}
