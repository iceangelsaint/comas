package edu.carleton.cas.resources;

import edu.carleton.cas.background.LogArchiver;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.ui.WebAppDialogCoordinator;
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class FileExplorer extends Thread {
   private static final String[] startCmd = new String[]{"open %s", "explorer %s", "nautilus %s", "xdg-open %s"};
   private String cmd;
   private final File folder;
   private Process process;
   private LogArchiver logger;
   boolean coordinationRequired;

   public FileExplorer(File folder) {
      this(folder, (LogArchiver)null, false);
   }

   public FileExplorer(File folder, LogArchiver logger, boolean coordinationRequired) {
      if (ClientShared.isMacOS()) {
         this.cmd = startCmd[0];
      } else if (ClientShared.isWindowsOS()) {
         this.cmd = startCmd[1];
      } else {
         this.cmd = startCmd[2];
      }

      this.folder = folder;
      this.logger = logger;
      this.coordinationRequired = coordinationRequired;
      this.setName("file explorer");
   }

   public void run() {
      if (this.coordinationRequired) {
         WebAppDialogCoordinator.coordinate(ClientShared.MAX_TIME_TO_WAIT_TO_COORDINATE_UI);
      }

      String cmdToRun = String.format(this.cmd, this.folder.getAbsolutePath());

      try {
         if (Desktop.getDesktop().isSupported(Action.BROWSE_FILE_DIR)) {
            Desktop.getDesktop().browseFileDirectory(this.folder);
         } else {
            this.process = Runtime.getRuntime().exec(cmdToRun);
         }
      } catch (Exception e) {
         if (ClientShared.isLinuxOS()) {
            cmdToRun = String.format(startCmd[3], this.folder.getAbsolutePath());

            try {
               this.process = Runtime.getRuntime().exec(cmdToRun);
            } catch (IOException e1) {
               if (this.logger != null) {
                  this.logger.put(Level.WARNING, "Failed to run " + cmdToRun + ": " + String.valueOf(e1));
               }
            }
         } else if (this.logger != null) {
            this.logger.put(Level.WARNING, "Failed to run " + cmdToRun + ": " + String.valueOf(e));
         }
      }

   }

   public void close() {
      if (this.process != null) {
         this.process.destroyForcibly();
         this.process = null;
      }

   }

   public static void main(String[] args) {
      FileExplorer fe = new FileExplorer(new File("/Users/tonywhite/Desktop/CoMaS"));
      fe.start();

      try {
         Thread.sleep(5000L);
      } catch (InterruptedException var6) {
      } finally {
         fe.close();
      }

   }
}
