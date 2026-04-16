package edu.carleton.cas.resources;

import edu.carleton.cas.background.SessionConfigurationModeMonitor;
import edu.carleton.cas.logging.Level;

public abstract class AbstractNetworkInterfaceManager implements NetworkIFManagerInterface, OutputProcessorObserver {
   protected final SessionConfigurationModeMonitor scmm;
   protected String password;
   protected final NullOutputProcessor nullOutputProcessor;
   protected boolean dhcp;
   protected boolean passwordIsCorrect;
   protected String ssid;

   protected AbstractNetworkInterfaceManager(SessionConfigurationModeMonitor scmm) {
      this.scmm = scmm;
      this.password = null;
      this.dhcp = false;
      this.ssid = "";
      this.nullOutputProcessor = new NullOutputProcessor(this);
      this.passwordIsCorrect = false;
   }

   protected void runCmd(String[] cmd) {
      try {
         (new CommandRunner(cmd, this.nullOutputProcessor)).run();
      } catch (Exception e) {
         this.exceptionOccurred(e);
      }

   }

   protected void runCmd(String[] cmd, OutputProcessor op) {
      try {
         (new CommandRunner(cmd, op)).run();
      } catch (Exception e) {
         this.exceptionOccurred(e);
      }

   }

   protected void setPasswordState(String state) {
      boolean previousValueOfPasswordIsCorrect = this.passwordIsCorrect;
      String msg;
      if (state.equals("set")) {
         msg = "Password has been entered incorrectly";
         this.passwordIsCorrect = false;
      } else {
         msg = "Correct password entered";
         this.passwordIsCorrect = true;
      }

      if (!this.passwordIsCorrect || this.passwordIsCorrect != previousValueOfPasswordIsCorrect) {
         this.scmm.invigilator.logArchiver.put(Level.DIAGNOSTIC, msg, new Object[]{"password_error", "problem", state});
      }

   }

   public String getSSID() {
      return this.ssid;
   }

   public void resultAvailable(OutputProcessor p) {
      edu.carleton.cas.logging.Logger.debug(Level.DIAGNOSTIC, p.result());
   }

   public void exceptionOccurred(Exception e) {
      String msg = this.scmm.invigilator.getHardwareAndSoftwareMonitor().getOSVersion();
      this.scmm.invigilator.logArchiver.put(Level.DIAGNOSTIC, msg + " Session mode monitor could not run a command: " + String.valueOf(e), new Object[]{"software_configuration", "problem", "set"});
   }
}
