package edu.carleton.cas.resources;

import edu.carleton.cas.background.SessionConfigurationModeMonitor;
import edu.carleton.cas.utility.DNSCacheFlusherCmd;
import oshi.hardware.NetworkIF;

public final class NetworkIFManagerForWindows extends AbstractNetworkInterfaceManager {
   public NetworkIFManagerForWindows(SessionConfigurationModeMonitor scmm) {
      super(scmm);
   }

   public void setDnsServers(NetworkIF nif, String requiredDNS) {
      this.ssid = this.getWiFiSSID(nif);
      this.dhcp = this.isDHCP(nif);
      String[] var10001 = new String[]{"netsh", "interface", "ipv4", "set", "dns", null, null, null};
      String var10004 = this.getName(nif);
      var10001[5] = "name=\"" + var10004 + "\"";
      var10001[6] = "source=static";
      var10001[7] = requiredDNS;
      this.runCmd(var10001);
      var10001 = new String[]{"netsh", "interface", "ipv6", "set", "dnsservers", null, null, null, null};
      var10004 = this.getName(nif);
      var10001[5] = "name=\"" + var10004 + "\"";
      var10001[6] = "source=static";
      var10001[7] = "address=none";
      var10001[8] = "register=none";
      this.runCmd(var10001);
      this.setIpV6State(nif, "Disable");
   }

   public void resetDnsServers(NetworkIF nif, String[] requiredDNS, boolean isDHCP) {
      String ssidOnReset = this.getWiFiSSID(nif);
      if (!ssidOnReset.equals(this.ssid) || isDHCP) {
         this.dhcp = true;
      }

      if (this.dhcp) {
         String[] var10001 = new String[]{"netsh", "interface", "ipv4", "set", "dnsservers", null, null};
         String var10004 = this.getName(nif);
         var10001[5] = "name=\"" + var10004 + "\"";
         var10001[6] = "source=dhcp";
         this.runCmd(var10001);
         var10001 = new String[]{"netsh", "interface", "ipv6", "set", "dnsservers", null, null};
         var10004 = this.getName(nif);
         var10001[5] = "name=\"" + var10004 + "\"";
         var10001[6] = "source=dhcp";
         this.runCmd(var10001);
      } else {
         if (requiredDNS.length == 1) {
            String[] var7 = new String[]{"netsh", "interface", "ipv4", "set", "dns", null, null, null};
            String var10 = this.getName(nif);
            var7[5] = "name=\"" + var10 + "\"";
            var7[6] = "static";
            var7[7] = requiredDNS[0];
            this.runCmd(var7);
         }

         if (requiredDNS.length > 1) {
            for(int i = 1; i < requiredDNS.length; ++i) {
               String[] var8 = new String[]{"netsh", "interface", "ipv4", "add", "dns", null, null, null};
               String var11 = this.getName(nif);
               var8[5] = "name=\"" + var11 + "\"";
               var8[6] = "static";
               var8[7] = requiredDNS[i];
               this.runCmd(var8);
            }
         }
      }

      if (isDHCP) {
         this.setupInterfaceAsDHCP(nif);
      }

      this.setIpV6State(nif, "Enable");
   }

   public void changeNetworkInterfaceState(NetworkIF nif, boolean enable, String prompt) {
      String[] var10001 = new String[]{"netsh", "interface", "set", "interface", null, null};
      String var10004 = this.getName(nif);
      var10001[4] = "\"" + var10004 + "\"";
      var10001[5] = enable ? "enable" : "disable";
      this.runCmd(var10001);
   }

   public void close() {
   }

   public void initializeDnsCache() {
      this.runCmd(DNSCacheFlusherCmd.getCmd());
   }

   public boolean isDHCP(NetworkIF nif) {
      return this.isDHCP(nif, "ipv4");
   }

   public void setIpV6State(NetworkIF nif, String state) {
      if (state.equals("off")) {
         state = "Disable";
      } else if (state.equals("on")) {
         state = "Enable";
      }

      this.runCmd(new String[]{"powershell.exe", state + "-NetAdapterBinding", "-Name", this.getName(nif), "-ComponentID", "ms_tcpip6"});
   }

   public String getIpV6State(NetworkIF nif) {
      return nif.getIPv6addr().length > 0 ? "on" : "off";
   }

   public boolean isDHCP(NetworkIF nif, String ip) {
      String[] var10000 = new String[]{"netsh", "interface", ip, "show", "config", null};
      String var10003 = this.getName(nif);
      var10000[5] = "name=\"" + var10003 + "\"";
      String[] cmd = var10000;
      LineContainingPatternProcessor lcpp = new LineContainingPatternProcessor(".*DHCP.*Enabled.*Yes");
      CommandRunner cr = new CommandRunner(cmd, lcpp);
      cr.run();
      return lcpp.result().length() > 0;
   }

   private String getName(NetworkIF nif) {
      String nameToUse;
      if (nif.getName().startsWith("wlan")) {
         nameToUse = "Wi-Fi";
      } else if (nif.getName().startsWith("eth")) {
         nameToUse = "Ethernet";
      } else {
         nameToUse = nif.getDisplayName();
      }

      return nameToUse;
   }

   public boolean isVPN(NetworkIF nif) {
      return nif.getName().startsWith("net") && nif.getIPv4addr().length > 0;
   }

   public void setupWithVPN(NetworkIF nif, String requiredDNS) {
   }

   public void setupInterfaceAsDHCP(NetworkIF nif) {
      String[] var10001 = new String[]{"netsh", "interface", "ipv4", "set", "dns", null, null};
      String var10004 = this.getName(nif);
      var10001[5] = "name=\"" + var10004 + "\"";
      var10001[6] = "source=dhcp";
      this.runCmd(var10001);
   }

   public String getWiFiSSID(NetworkIF nif) {
      LineContainingPatternProcessor lcpp = new LineContainingPatternProcessor("\\s*SSID\\s*:.*");
      this.runCmd(new String[]{"netsh", "wlan", "show", "interfaces"}, lcpp);
      String result = lcpp.result();
      String[] tokens = result.split(":");
      return tokens.length >= 2 ? tokens[1].trim() : result;
   }
}
