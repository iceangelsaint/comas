package edu.carleton.cas.background;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.reporting.ReportManager.ProblemStatus;
import java.io.File;
import java.io.FileFilter;
import java.util.Date;

public class ScreenShotSentinel implements KeepAliveInterface, InitializerInterface {
   private static final FileFilter SCREEN_SHOT_FILE_FILTER = new FileFilter() {
      public boolean accept(File file) {
         return !file.isHidden() && file.canRead() && file.getName().endsWith(".jpg");
      }
   };
   private static final float DISK_SPACE_THRESHOLD = 5.0F;
   long lastScreenShotTimeInMillis;
   int numberOfScreenShots;
   final Invigilator login;
   boolean problem = false;
   long threshold;
   long lastRate;
   KeepAliveStatistics lastStatistics;

   public ScreenShotSentinel(Invigilator login, long threshold) {
      this.login = login;
      this.numberOfScreenShots = 0;
      this.lastScreenShotTimeInMillis = 0L;
      this.threshold = threshold;
      this.lastRate = 0L;
      this.lastStatistics = new KeepAliveStatistics();
   }

   public String getName() {
      return "Screen shots";
   }

   public KeepAliveStatistics getStatistics() {
      return this.login.screenShotArchiver.getStatistics();
   }

   public void start() {
      this.checkForLowFreeDiskSpace(ClientShared.DIR);
   }

   public void stop() {
   }

   public void init() {
      this.lastScreenShotTimeInMillis = System.currentTimeMillis();
      this.login.screenShotArchiver.getStatistics().setTimeOfLastProcessed(this.lastScreenShotTimeInMillis);
   }

   public boolean keepAlive() {
      String screensDirString = ClientShared.getScreensDirectory(this.login.getCourse(), this.login.getActivity());
      File screensDir = new File(screensDirString);
      long timeNow = System.currentTimeMillis();
      long timeToWaitToCheck = (long)(ClientShared.MIN_INTERVAL + ClientShared.MAX_INTERVAL) * 1000L;
      if (this.lastScreenShotTimeInMillis == 0L) {
         this.lastScreenShotTimeInMillis = timeNow;
         return false;
      } else {
         if (ClientShared.USE_SCREEN_SHOTS) {
            File[] files = screensDir.listFiles(SCREEN_SHOT_FILE_FILTER);
            if (files != null) {
               if (files.length <= this.numberOfScreenShots) {
                  float freeSpaceInGB = (float)screensDir.getFreeSpace() * 1.0F / 1.0737418E9F;
                  if (files.length == this.numberOfScreenShots) {
                     if (!this.login.isInitialized() || timeNow - this.lastScreenShotTimeInMillis < timeToWaitToCheck) {
                        return false;
                     }

                     this.problem = true;
                     this.login.logArchiver.put(Level.DIAGNOSTIC, String.format("Screen shot problem? Number of images (%d) unchanged since last check at %s, %.03f seconds ago. Free space (%.02f GB)", this.numberOfScreenShots, (new Date(this.lastScreenShotTimeInMillis)).toString(), (double)(timeNow - this.lastScreenShotTimeInMillis) / (double)1000.0F, freeSpaceInGB), new Object[]{"problem", "suspended", "deleted_screenshots", "set"});
                  } else {
                     this.problem = true;
                     this.login.logArchiver.put(Level.DIAGNOSTIC, String.format("Screen shot problem. Had %d images at %s. Now have %d images. Free space (%.02f GB)", this.numberOfScreenShots, (new Date(this.lastScreenShotTimeInMillis)).toString(), files.length, freeSpaceInGB), this.login.createProblemSetEvent("deleted_screenshots"));
                  }
               } else {
                  this.lastScreenShotTimeInMillis = timeNow;
                  this.checkForLowFreeDiskSpace(screensDirString);
                  if (this.problem) {
                     this.problem = false;
                     this.login.logArchiver.put(Level.DIAGNOSTIC, "Screen shots okay", this.login.createProblemClearEvent("deleted_screenshots"));
                     if (this.login.getReportManager().hasProblemWithStatus("suspended", ProblemStatus.set)) {
                        this.login.createProblemClearEvent("suspended");
                     }
                  }
               }

               this.numberOfScreenShots = files.length;
            }

            Archiver ssa = this.login.screenShotArchiver;
            if (ssa != null) {
               KeepAliveStatistics kas = ssa.getStatistics();
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
                     String msg = String.format("%s:%s Starts=%d, Processed=%d, Failures=%d, Backlog=%d, Last=%.03f seconds, Rate(session)=%d msecs/upload, Rate(now)=%d msecs/upload, Time=%.03f seconds%s", this.getName(), slowMessage, kas.getTotalStarts(), kas.getTotalProcessed(), kas.getTotalFailures(), ssa.backlog(), (float)(timeNow - kas.getTimeOfLastProcessed()) * 0.001F, rate, currentRate, (float)kas.getTotalTime() * 0.001F, emsg);
                     if (currentRate <= this.threshold && currentRate != 0L) {
                        this.login.logArchiver.put(Level.DIAGNOSTIC, msg, this.login.createProblemClearEvent("slow_screenshot_upload"));
                     } else {
                        this.login.logArchiver.put(Level.DIAGNOSTIC, msg, this.login.createProblemSetEvent("slow_screenshot_upload"));
                     }

                     this.lastRate = rate;
                  }
               }

               this.lastStatistics = new KeepAliveStatistics(kas);
            }
         }

         return false;
      }
   }

   private void checkForLowFreeDiskSpace(String dir) {
      File fDir = new File(dir);
      float freeSpaceInGB = (float)fDir.getFreeSpace() * 1.0F / 1.0737418E9F;
      if (freeSpaceInGB < 5.0F) {
         this.login.logArchiver.put(Level.DIAGNOSTIC, String.format("Low free disk space for screen shots (%.02f GB)", freeSpaceInGB));
      }

   }
}
