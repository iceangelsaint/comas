package edu.carleton.cas.resources;

import edu.carleton.cas.background.SessionConfigurationModeMonitor;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.ui.WebAlert;
import edu.carleton.cas.utility.DNSCacheFlusherCmd;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import oshi.hardware.NetworkIF;

public final class NetworkIFManagerForMacOS extends AbstractNetworkInterfaceManager {
   private Terminal terminal;

   public NetworkIFManagerForMacOS(SessionConfigurationModeMonitor scnm) {
      super(scnm);
   }

   public void setDnsServers(NetworkIF nif, String requiredDNS) {
      this.ssid = this.getWiFiSSID(nif);
      this.dhcp = this.isDHCP(nif);
      this.runCmd(new String[]{"networksetup", "-setdnsservers", nif.getDisplayName(), requiredDNS});
      this.setIpV6State(nif, "off");
   }

   public void resetDnsServers(NetworkIF nif, String[] requiredDNS, boolean isDHCP) {
      String ssidOnReset = this.getWiFiSSID(nif);
      if (!ssidOnReset.equals(this.ssid) || isDHCP) {
         this.dhcp = true;
      }

      if (this.dhcp) {
         this.runCmd(new String[]{"networksetup", "-setdnsservers", nif.getDisplayName(), "Empty"});
      } else {
         String[] cmd = new String[3 + requiredDNS.length];
         cmd[0] = "networksetup";
         cmd[1] = "-setdnsservers";
         cmd[2] = nif.getDisplayName();

         for(int i = 0; i < requiredDNS.length; ++i) {
            cmd[3 + i] = requiredDNS[i];
         }

         this.runCmd(cmd);
      }

      if (isDHCP) {
         this.setupInterfaceAsDHCP(nif);
      }

      this.setIpV6State(nif, "automatic");
   }

   public void changeNetworkInterfaceState(NetworkIF nif, boolean enable, String prompt) {
      PasswordOutputProcessor pop = null;
      String[] cmd = new String[]{"sudo", "-S", "ifconfig", nif.getName(), enable ? "up" : "down"};

      try {
         if (this.password == null) {
            CountDownLatch cdl = this.scmm.getPasswordDialog().getPassword(prompt);

            try {
               cdl.await((long)ClientShared.THIRTY_SECONDS_IN_MSECS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException var17) {
            }

            this.password = this.scmm.getPasswordDialog().getPasswordInput();
         }

         pop = new PasswordOutputProcessor(this.password + "\n");
         CommandRunner cr = new CommandRunner(cmd, pop);
         cr.run();
         if (!pop.isOkay()) {
            this.password = null;
            this.setPasswordState("set");
         } else {
            this.setPasswordState("clear");
         }
      } catch (Exception e) {
         this.exceptionOccurred(e);
      } finally {
         if (pop != null) {
            try {
               pop.close();
            } catch (IOException var16) {
            }
         }

      }

   }

   public void initializeDnsCache() {
      if (this.scmm.invigilator.getHardwareAndSoftwareMonitor().getVendor().equals("Apple")) {
         if (this.scmm.isUsingTerminal()) {
            this.flushByTerminal(this.scmm.getReport());
         } else {
            try {
               this.flushByCommandRunner();
            } catch (IOException var2) {
               this.scmm.setUsingTerminal();
            }
         }
      }

   }

   public void close() {
      if (this.terminal != null) {
         this.terminal.close();
      }

   }

   public boolean isDHCP(NetworkIF nif) {
      String[] cmd = new String[]{"ipconfig", "getsummary", nif.getName()};
      LineContainingPatternProcessor lcpp = new LineContainingPatternProcessor(".*ConfigMethod.*DHCP.*");
      CommandRunner cr = new CommandRunner(cmd, lcpp);
      cr.run();
      return lcpp.result().length() > 0;
   }

   public void setIpV6State(NetworkIF nif, String state) {
      if (state.equals("off")) {
         state = "off";
      } else if (state.equals("on")) {
         state = "automatic";
      }

      this.runCmd(new String[]{"networksetup", "-setv6" + state, nif.getDisplayName()});
   }

   public String getIpV6State(NetworkIF nif) {
      return nif.getIPv6addr().length > 0 ? "on" : "off";
   }

   private void flushByCommandRunner() throws IOException {
      String[] cmd = new String[]{"sudo", "-S", "killall", "-HUP", "mDNSResponder"};
      String diagnosticMsg = "CoMaS: Your host cache needs to be flushed";
      if (this.password == null) {
         CountDownLatch cdl = this.scmm.getPasswordDialog().getPassword(diagnosticMsg);

         try {
            cdl.await((long)ClientShared.THIRTY_SECONDS_IN_MSECS, TimeUnit.MILLISECONDS);
         } catch (InterruptedException var15) {
         }

         this.password = this.scmm.getPasswordDialog().getPasswordInput();
      }

      PasswordOutputProcessor pop = null;

      try {
         pop = new PasswordOutputProcessor(this.password + "\n");
         CommandRunner cr = new CommandRunner(cmd, pop);
         cr.run();
         if (pop.isOkay()) {
            cmd = new String[]{"sudo", "-S", "killall", "mDNSResponderHelper"};
            cr = new CommandRunner(cmd, pop);
            cr.run();
            cmd = new String[]{"sudo", "-S", "dscacheutil", "-flushcache"};
            cr = new CommandRunner(cmd, pop);
            cr.run();
            this.setPasswordState("clear");
         } else {
            this.password = null;
            this.setPasswordState("set");
         }
      } catch (Exception e) {
         this.exceptionOccurred(e);
      } finally {
         if (pop != null) {
            try {
               pop.close();
            } catch (IOException var13) {
            }
         }

      }

   }

   private void flushByTerminal(StringBuffer report) {
      String diagnosticMsg = "Your host cache needs to be emptied.\n";
      String actionMsg = "\nPlease paste the command in the clipboard into the terminal window.\nThis window will open automatically.\nYou will be asked to enter your password.\nIn order to proceed, please close the terminal window once the command has completed";
      WebAlert.warningDialog(diagnosticMsg + actionMsg, "CoMaS Host Cache Initialization");
      report.append("\nDIAGNOSIS: ");
      report.append(diagnosticMsg);
      report.append("   ACTION: ");
      report.append(actionMsg);
      DNSCacheFlusherCmd.copyToClipboard();
      this.terminal = new Terminal();
      this.terminal.start();
   }

   public boolean isVPN(NetworkIF nif) {
      return nif.getName().startsWith("utun") && nif.getIPv4addr().length > 0;
   }

   public void setupWithVPN(NetworkIF nif, String requiredDNS) {
      this.runCmd(new String[]{"networksetup", "-setsearchdomains", nif.getDisplayName(), "lan"});
   }

   public void setupInterfaceAsDHCP(NetworkIF nif) {
      this.runCmd(new String[]{"networksetup", "-setdhcp", nif.getDisplayName()});
   }

   public String getWiFiSSID(NetworkIF nif) {
      SingleLineOutputProcessor slop = new SingleLineOutputProcessor();
      this.runCmd(new String[]{"networksetup", "-getairportnetwork", nif.getName()}, slop);
      String result = slop.result();
      int index = result.indexOf("Network: ");
      return index > 0 ? result.substring(index + 9).trim() : "";
   }
}
