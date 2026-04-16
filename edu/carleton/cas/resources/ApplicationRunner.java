package edu.carleton.cas.resources;

import edu.carleton.cas.background.LogArchiver;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.ui.DisappearingAlert;
import edu.carleton.cas.ui.WebAppDialogCoordinator;
import java.io.File;
import java.util.logging.Level;

public class ApplicationRunner extends Thread {
   private static final String[] startCmd = new String[]{"open", "explorer", "xdg-open", "nautilus"};
   private String cmd;
   private final File file;
   private CommandRunner cr;
   private LogArchiver logger;
   boolean coordinationRequired;

   public ApplicationRunner(File file, LogArchiver logger, boolean coordinationRequired) {
      if (ClientShared.isMacOS()) {
         this.cmd = startCmd[0];
      } else if (ClientShared.isWindowsOS()) {
         this.cmd = startCmd[1];
      } else {
         this.cmd = startCmd[2];
      }

      this.file = file;
      this.logger = logger;
      this.coordinationRequired = coordinationRequired;
      this.setName("application runner");
   }

   public void run() {
      if (this.coordinationRequired) {
         WebAppDialogCoordinator.coordinate(ClientShared.MAX_TIME_TO_WAIT_TO_COORDINATE_UI);
      }

      String[] cmdToRun = new String[]{this.cmd, this.file.getAbsolutePath()};

      try {
         this.cr = new CommandRunner(cmdToRun);
         this.cr.run();
      } catch (Exception e) {
         if (ClientShared.isLinuxOS()) {
            cmdToRun = new String[]{startCmd[3], this.file.getAbsolutePath()};
            this.cr = new CommandRunner(cmdToRun);

            try {
               this.cr.run();
            } catch (Exception e1) {
               this.logOrDisplayAlert(e1);
            }
         } else {
            this.logOrDisplayAlert(e);
         }
      }

   }

   private void logOrDisplayAlert(Exception e) {
      if (this.logger != null) {
         this.logger.put(Level.WARNING, "Could not run application. Reason is " + String.valueOf(e));
      } else {
         (new DisappearingAlert()).show("Could not open " + this.file.getName());
      }

   }

   public void close() {
      if (this.cr != null) {
         this.cr.close();
      }

   }
}
