package edu.carleton.cas.resources;

import edu.carleton.cas.background.SessionConfigurationModeMonitor;

public class PrinterManagerForLinux implements PrinterManagerInterface {
   PrinterManagerForLinux(SessionConfigurationModeMonitor scnm) {
   }

   public void start() {
      String[] cmd = new String[]{"gsettings", "reset", "org.gnome.desktop.lockdown", "disable-printing"};
      CommandRunner cr = new CommandRunner(cmd);
      cr.run();
   }

   public void stop() {
      String[] cmd = new String[]{"gsettings", "set", "org.gnome.desktop.lockdown", "disable-printing", "true"};
      CommandRunner cr = new CommandRunner(cmd);
      cr.run();
   }
}
