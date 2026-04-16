package edu.carleton.cas.resources;

import edu.carleton.cas.background.SessionConfigurationModeMonitor;

public class PrinterManagerForMacOS implements PrinterManagerInterface {
   private final SessionConfigurationModeMonitor scmm;

   PrinterManagerForMacOS(SessionConfigurationModeMonitor scmm) {
      this.scmm = scmm;
   }

   public void start() {
      this.scmm.invigilator.getHardwareAndSoftwareMonitor().terminateRunningProcess("cupsd");
   }

   public void stop() {
      this.scmm.invigilator.getHardwareAndSoftwareMonitor().suspendRunningProcess("cupsd");
   }
}
