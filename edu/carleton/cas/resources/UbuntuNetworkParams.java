package edu.carleton.cas.resources;

import oshi.software.os.NetworkParams;

public class UbuntuNetworkParams implements NetworkParams {
   public String[] getDnsServers() {
      String[] cmd = new String[]{"nmcli", "-t"};
      NmcliOutputProcessor nmcli = new NmcliOutputProcessor();
      CommandRunner cr = new CommandRunner(cmd, nmcli);
      cr.run();
      String dnsServer = nmcli.result();
      String[] servers = dnsServer.split("[,; ]");
      return servers;
   }

   public String getDomainName() {
      String[] cmd = new String[]{"domainname", "-f"};
      SingleLineOutputProcessor hnop = new SingleLineOutputProcessor();
      CommandRunner cr = new CommandRunner(cmd, hnop);
      cr.run();
      String domainname = hnop.result().trim();
      return domainname;
   }

   public String getHostName() {
      String[] cmd = new String[]{"hostname"};
      SingleLineOutputProcessor hnop = new SingleLineOutputProcessor();
      CommandRunner cr = new CommandRunner(cmd, hnop);
      cr.run();
      String hostname = hnop.result().trim();
      return hostname.length() == 0 ? System.getenv("HOSTNAME") : hostname;
   }

   public String getIpv4DefaultGateway() {
      String[] cmd = new String[]{"ip", "-4", "route"};
      IpV4ROutputProcessor ip = new IpV4ROutputProcessor();
      CommandRunner cr = new CommandRunner(cmd, ip);
      cr.run();
      String ipGateway = ip.result();
      return ipGateway == null ? "" : ipGateway;
   }

   public String getIpv6DefaultGateway() {
      String[] cmd = new String[]{"ip", "-6", "route"};
      IpV6ROutputProcessor ip = new IpV6ROutputProcessor();
      CommandRunner cr = new CommandRunner(cmd, ip);
      cr.run();
      String ipGateway = ip.result();
      return ipGateway == null ? "" : ipGateway;
   }
}
