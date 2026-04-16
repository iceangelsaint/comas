package edu.carleton.cas.resources;

import edu.carleton.cas.background.SessionConfigurationModeMonitor;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.utility.DNSCacheFlusherCmd;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import oshi.hardware.NetworkIF;

public final class NetworkIFManagerForLinux extends AbstractNetworkInterfaceManager {
   private HashMap map = new HashMap();

   public NetworkIFManagerForLinux(SessionConfigurationModeMonitor scmm) {
      super(scmm);
   }

   public void setDnsServers(NetworkIF nif, String requiredDNS) {
      this.ssid = this.getWiFiSSID(nif);
      this.dhcp = this.isDHCP(nif);
      String nameToUse = this.getNameForNetworkManager(nif.getName());
      if (this.dhcp) {
         this.runCmd(new String[]{"nmcli", "connection", "modify", nameToUse, "ipv4.ignore-auto-dns", "yes"});
      }

      this.runCmd(new String[]{"nmcli", "connection", "modify", nameToUse, "+ipv4.dns", requiredDNS});

      String[] var7;
      for(String dnsServer : var7 = this.scmm.initialDnsServers) {
         this.runCmd(new String[]{"nmcli", "connection", "modify", nameToUse, "-ipv4.dns", dnsServer});
      }

      this.resetNetworkManagerService();
      this.setIpV6State(nif, "1");
   }

   public void resetDnsServers(NetworkIF nif, String[] requiredDNS, boolean isDHCP) {
      String nameToUse = this.getNameForNetworkManager(nif.getName());
      String ssidOnReset = this.getWiFiSSID(nif);
      if (!ssidOnReset.equals(this.ssid) || isDHCP) {
         this.dhcp = true;
      }

      if (this.dhcp) {
         this.runCmd(new String[]{"nmcli", "connection", "modify", nameToUse, "ipv4.ignore-auto-dns", "no"});
      } else {
         for(String dnsServer : requiredDNS) {
            this.runCmd(new String[]{"nmcli", "connection", "modify", nameToUse, "+ipv4.dns", dnsServer});
         }
      }

      this.runCmd(new String[]{"nmcli", "connection", "modify", nameToUse, "-ipv4.dns", this.scmm.allowed_dns_server});
      if (isDHCP) {
         this.setupInterfaceAsDHCP(nif);
      }

      this.resetNetworkManagerService();
      this.setIpV6State(nif, "0");
   }

   public void changeNetworkInterfaceState(NetworkIF nif, boolean enable, String prompt) {
      this.runCmdWithPassword(new String[]{"sudo", "-S", "ifconfig", nif.getName(), enable ? "up" : "down"}, prompt);
   }

   public void initializeDnsCache() {
      String os = this.scmm.invigilator.getHardwareAndSoftwareMonitor().getOSVersion();
      if (os.startsWith("22.04")) {
         this.runCmd(DNSCacheFlusherCmd.getAltCmd());
      } else {
         this.runCmd(DNSCacheFlusherCmd.getCmd());
      }

   }

   public void close() {
   }

   public boolean isDHCP(NetworkIF nif) {
      String nameToUse = this.getNameForNetworkManager(nif.getName());
      String[] cmd = new String[]{"nmcli", "-f", "ipv4.method", "con", "show", nameToUse};
      LineContainingPatternProcessor lcpp = new LineContainingPatternProcessor(".*ipv4.*auto.*");
      CommandRunner cr = new CommandRunner(cmd, lcpp);
      cr.run();
      return lcpp.result().length() > 0;
   }

   private void resetNetworkManagerService() {
      this.runCmdWithPassword(new String[]{"sudo", "-S", "service", "NetworkManager", "restart"});
   }

   public void setIpV6State(NetworkIF nif, String state) {
      if (state.equals("off")) {
         state = "1";
      } else if (state.equals("on")) {
         state = "0";
      }

      this.runCmdWithPassword(new String[]{"sudo", "-S", "sysctl", "-w", "net.ipv6.conf.all.disable_ipv6=" + state});
      this.runCmdWithPassword(new String[]{"sudo", "-S", "sysctl", "-w", "net.ipv6.conf.default.disable_ipv6=" + state});
      this.runCmdWithPassword(new String[]{"sudo", "-S", "sysctl", "-w", "net.ipv6.conf.lo.disable_ipv6=" + state});
   }

   public String getIpV6State(NetworkIF nif) {
      return nif.getIPv6addr().length > 0 ? "on" : "off";
   }

   private void runCmdWithPassword(String[] cmd, String prompt) {
      PasswordOutputProcessor pop = null;
      if (this.password == null) {
         CountDownLatch cdl = this.scmm.getPasswordDialog().getPassword(prompt);

         try {
            cdl.await((long)ClientShared.THIRTY_SECONDS_IN_MSECS, TimeUnit.MILLISECONDS);
         } catch (InterruptedException var15) {
         }

         this.password = this.scmm.getPasswordDialog().getPasswordInput();
      }

      try {
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
            } catch (IOException var13) {
            }
         }

      }

   }

   private void runCmdWithPassword(String[] cmd) {
      this.runCmdWithPassword(cmd, "CoMaS: Network parameters need to be changed");
   }

   private String getNameForNetworkManager(String name) {
      String value = (String)this.map.get(name);
      if (value != null) {
         return value;
      } else {
         String[] cmd = new String[]{"nmcli", "c"};
         LineContainingPatternProcessor lcpp = new LineContainingPatternProcessor("^.*" + name + ".*$");
         CommandRunner cr = new CommandRunner(cmd, lcpp);
         cr.run();
         String[] tokens = lcpp.result().split("[\t ]");
         if (tokens != null && tokens.length > 0) {
            this.map.put(name, tokens[0]);
            return tokens[0];
         } else {
            return name;
         }
      }
   }

   public boolean isVPN(NetworkIF nif) {
      return nif.getName().startsWith("tun") && nif.getIPv4addr().length > 0;
   }

   public void setupWithVPN(NetworkIF nif, String requiredDNS) {
   }

   public void setupInterfaceAsDHCP(NetworkIF nif) {
      String nameToUse = this.getNameForNetworkManager(nif.getName());
      this.runCmd(new String[]{"nmcli", "connection", "modify", nameToUse, "ipv4.ignore-auto-dns", "no"});
   }

   public String getWiFiSSID(NetworkIF nif) {
      SingleLineOutputProcessor slop = new SingleLineOutputProcessor();
      this.runCmd(new String[]{"nmcli", "-t", "-f", "name", "connection", "show", "--active"}, slop);
      return slop.result.trim();
   }
}
