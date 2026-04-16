package edu.carleton.cas.resources;

import edu.carleton.cas.constants.ClientShared;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class WindowsFileTask extends AbstractFileTask {
   private String cmd;

   public WindowsFileTask(Logger logger, ResourceMonitor monitor) {
      super(logger, monitor);
      this.cmd = ClientShared.DOWNLOADS_DIR + "handle.exe";
   }

   public boolean isIllegal(String line) {
      if (line.contains(":\\Windows")) {
         return false;
      } else {
         return line.contains(ClientShared.DIR) ? false : super.isIllegal(line);
      }
   }

   public void run() {
      if (!this.isRunning() && !this.isSessionEnded()) {
         try {
            this.running = true;
            synchronized(this.openFiles) {
               this.openFiles.clear();
            }

            ProcessBuilder builder = new ProcessBuilder(new String[]{this.cmd, "-accepteula"});
            builder.redirectErrorStream(true);
            this.process = builder.start();
            InputStream stdout = this.process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
            this.processName = "";
            this.logger.begin();

            String line;
            while((line = reader.readLine()) != null) {
               this.logger.log(line);
               if (line.contains("File")) {
                  int index = line.indexOf("File");
                  String fileName = line.substring(index + 4);
                  line = this.processName + ": " + fileName;
                  if (line.contains(this.getFolderOfInterest())) {
                     this.getProcessNameAndFileName(this.processName, fileName);
                  } else if (this.isIllegal(line)) {
                     this.monitor.notifyListeners(this.monitor.getResourceType(), line);
                  }
               } else if (line.contains("pid:")) {
                  int index = line.indexOf(" ");
                  if (index > 0) {
                     this.processName = line.substring(0, index);
                  }
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
}
