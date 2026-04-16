package edu.carleton.cas.background;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.resources.SystemWebResources;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;

public class Harvester extends Thread {
   private static final int PROGRESS_UPDATE_FREQUENCY_IN_MSECS = 500;
   private final Archiver archiver;
   private final String dir;
   private int numberToUpload;
   private int uploaded;
   private final Invigilator invigilator;
   private final String text;
   private final String type;
   private final int start_index;
   private final CountDownLatch latch;

   public Harvester(int index, String dir, Invigilator invigilator, Archiver archiver, String text, String type, CountDownLatch latch) {
      this.start_index = index;
      this.dir = dir;
      this.archiver = archiver;
      this.invigilator = invigilator;
      this.text = text;
      this.type = type.toLowerCase();
      this.latch = latch;
      this.uploaded = 0;
      this.numberToUpload = 0;
   }

   private int init() {
      File archivalDir = new File(this.dir);
      File[] archives = archivalDir.listFiles(new FilenameFilter() {
         public boolean accept(File dir, String name) {
            return name.toLowerCase().endsWith(Harvester.this.type);
         }
      });
      if (archives != null && archives.length > 0) {
         if (this.start_index <= archives.length && this.start_index >= 1) {
            Comparator<File> compareUsingLastModified = new Comparator() {
               public int compare(File a, File b) {
                  long alastModified = a.lastModified();
                  long blastModified = b.lastModified();
                  if (alastModified > blastModified) {
                     return 1;
                  } else {
                     return alastModified == blastModified ? 0 : -1;
                  }
               }
            };
            Arrays.sort(archives, compareUsingLastModified);
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, String.format("Harvesting %d %s starting at index %d", archives.length, this.text, this.start_index));
            int index = 1;
            int numberUploaded = 0;

            for(File archive : archives) {
               if (index >= this.start_index) {
                  ++numberUploaded;
                  this.archiver.put(archive.getAbsolutePath());
               }

               ++index;
            }

            int toBeHarvested = Math.min(numberUploaded, archives.length);
            return toBeHarvested;
         } else {
            return 0;
         }
      } else {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, String.format("No %s to harvest", this.text));
         return 0;
      }
   }

   public void run() {
      this.numberToUpload = this.init();
      if (this.numberToUpload > 0) {
         this.showProgress();
         this.uploaded = this.archiver.getStatistics().getTotalProcessed();

         while(this.uploaded < this.numberToUpload) {
            try {
               Thread.sleep(500L);
               this.showProgress();
            } catch (InterruptedException var13) {
            } finally {
               this.uploaded = this.archiver.getStatistics().getTotalProcessed();
            }
         }
      }

      this.latch.countDown();
      if (this.numberToUpload > 0) {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, String.format("Harvested %d %s starting at index %d", this.uploaded, this.text, this.start_index));
      }

      try {
         this.latch.await();
      } catch (InterruptedException var11) {
      } finally {
         this.invigilator.setInvigilatorState(InvigilatorState.ending);
         this.invigilator.updateProgressServlet(100, SystemWebResources.getLocalResource("endLandingPage", "/end"));
      }

   }

   private void showProgress() {
      int progress = this.uploaded * 100 / this.numberToUpload;
      String msg;
      if (progress == 100) {
         msg = SystemWebResources.getLocalResource("endLandingPage", "/end");
      } else {
         msg = String.format("%d of %d %s uploaded", this.uploaded, this.numberToUpload, this.text);
      }

      this.invigilator.updateProgressServlet(progress, msg);
   }
}
