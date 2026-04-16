package edu.carleton.cas.resources;

import edu.carleton.cas.constants.ClientShared;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class UnixFileTask extends AbstractFileTask {
   private String userName = System.getProperty("user.name");

   public UnixFileTask(Logger logger, ResourceMonitor monitor) {
      super(logger, monitor);
   }

   public boolean isIllegal(String line) {
      if (line.contains("/System/Library")) {
         return false;
      } else if (line.contains("/private/")) {
         return false;
      } else if (line.contains("/Applications/")) {
         return false;
      } else {
         return line.contains(ClientShared.DIR) ? false : super.isIllegal(line);
      }
   }

   public void run() {
      if (!this.isRunning()) {
         try {
            this.running = true;
            synchronized(this.openFiles) {
               this.openFiles.clear();
            }

            ProcessBuilder builder = new ProcessBuilder(new String[]{"lsof", "-F", "pcuftDsin", "-u", this.userName});
            builder.redirectErrorStream(true);
            this.process = builder.start();
            InputStream stdout = this.process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
            this.processName = "";
            this.logger.begin();

            String[] line;
            while((line = this.getProcessOpenFileAssociation(reader)) != null) {
               String reducedLine = line[0] + ": " + line[1];
               this.logger.log(reducedLine);
               if (line[1].contains(this.getFolderOfInterest())) {
                  this.getProcessNameAndFileName(line[0], line[1]);
               } else if (this.isIllegal(line[1])) {
                  this.monitor.notifyListeners(this.monitor.getResourceType(), reducedLine);
               }
            }

            this.logger.end();
         } catch (Exception e) {
            this.monitor.notifyListeners(this.monitor.getResourceType(), "File monitoring exception: " + String.valueOf(e));
         } finally {
            this.running = false;
            this.close();
         }

      }
   }

   private String[] getProcessOpenFileAssociation(BufferedReader reader) throws IOException {
      String[] field = null;
      String fileName = "null";

      String line;
      while((line = reader.readLine()) != null) {
         if (line.startsWith("c")) {
            this.processName = line;
         } else if (line.startsWith("n") && !this.processName.equals("c") && !this.processName.equals("clsof") && !line.startsWith("n->") && !line.equals("n/dev/null") && !line.equals("n") && !line.equals("n/") && !this.processName.startsWith("ccom.apple.")) {
            field = new String[]{this.processName.substring(1), line.substring(1)};
            return field;
         }
      }

      return field;
   }
}
