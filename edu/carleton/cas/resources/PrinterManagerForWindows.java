package edu.carleton.cas.resources;

import edu.carleton.cas.background.SessionConfigurationModeMonitor;

public class PrinterManagerForWindows implements PrinterManagerInterface {
   PrinterManagerForWindows(SessionConfigurationModeMonitor scnm) {
   }

   public void start() {
      String[] cmd = new String[]{"powershell.exe", "Stop-Service", "-Name", "Spooler", "-force"};
      CommandRunner cr = new CommandRunner(cmd);
      cr.run();
   }

   public void stop() {
      String[] cmd = new String[]{"powershell.exe", "Start-Service", "-Name", "Spooler"};
      CommandRunner cr = new CommandRunner(cmd);
      cr.run();
   }
}
