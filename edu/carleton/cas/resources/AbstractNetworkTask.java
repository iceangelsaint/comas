package edu.carleton.cas.resources;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.lookup.Whois;
import edu.carleton.cas.utility.ClientHelper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;

public abstract class AbstractNetworkTask extends AbstractTask {
   protected HashSet processes;
   protected HashMap hosts;
   protected HashSet hostPatterns;
   protected HashSet corporations;
   protected HashSet corporationPatterns;
   protected HashMap cache;
   protected boolean includeRawLog;
   protected String remoteHost;
   protected String[] cmd;

   public AbstractNetworkTask(Logger logger, ResourceMonitor monitor) {
      super(logger, monitor);

      try {
         String localHost = InetAddress.getLocalHost().getHostAddress();
         this.hosts = new HashMap();
         this.addHost(ClientShared.EXAM_HOST);
         this.addHost(ClientShared.DIRECTORY_HOST);
         this.addHost(ClientShared.UPLOAD_HOST);
         this.addHost(ClientShared.VIDEO_HOST);
         this.addHost(ClientShared.LOG_HOST);
         this.addHost(ClientShared.CMS_HOST);
         this.addHost(ClientShared.WEBSOCKET_HOST);
         this.addHostName("127.0.0.1");
         this.addHostName("localhost");
         this.addHostName(localHost);
         this.addAllHosts();
      } catch (UnknownHostException e) {
         monitor.notifyListeners(monitor.getResourceType(), e.toString());
      }

      try {
         this.hostPatterns = new HashSet();
         this.addAllHostPatterns();
      } catch (Exception e) {
         monitor.notifyListeners(monitor.getResourceType(), e.toString());
      }

      try {
         this.corporations = new HashSet();
         this.addAllCorporations();
      } catch (Exception e) {
         monitor.notifyListeners(monitor.getResourceType(), e.toString());
      }

      try {
         this.corporationPatterns = new HashSet();
         this.addAllCorporationPatterns();
      } catch (Exception e) {
         monitor.notifyListeners(monitor.getResourceType(), e.toString());
      }

      try {
         this.processes = new HashSet();
         this.addAllTrustedProcesses();
      } catch (Exception e) {
         monitor.notifyListeners(monitor.getResourceType(), e.toString());
      }

      this.includeRawLog = Utils.getBooleanOrDefault(monitor.properties, "monitoring.network.include_raw_log", true);
      this.cache = new HashMap();
   }

   private void addHost(String host) throws UnknownHostException {
      InetAddress[] addresses = InetAddress.getAllByName(host);

      for(InetAddress address : addresses) {
         this.hosts.put(address.getHostAddress().replace('.', '-'), Boolean.TRUE);
         this.hosts.put(address.getHostAddress(), Boolean.TRUE);
         this.hosts.put(address.getHostName(), Boolean.TRUE);
         this.hosts.put(address.getCanonicalHostName(), Boolean.TRUE);
      }

   }

   public void addHostName(String host) {
      this.hosts.put(host, Boolean.TRUE);
   }

   private void addAllAllowed(String base, HashSet set) {
      int i = 1;

      for(String allowed = this.monitor.getProperty(base + i); allowed != null; allowed = this.monitor.getProperty(base + i)) {
         set.add(allowed.trim());
         ++i;
      }

   }

   private void addAllAllowed(String base, HashMap set) {
      int i = 1;

      for(String allowed = this.monitor.getProperty(base + i); allowed != null; allowed = this.monitor.getProperty(base + i)) {
         set.put(allowed.trim(), Boolean.TRUE);
         ++i;
      }

   }

   public void addAllHosts() {
      this.addAllAllowed("host.allow.", this.hosts);
   }

   public void addAllTrustedProcesses() {
      String os = ClientShared.getOSString();
      this.addAllAllowed("process.allow.", this.processes);
      this.addAllAllowed("process." + os + ".allow.", this.processes);
   }

   private void addAllAllowedPatterns(String base, HashSet set) throws PatternSyntaxException {
      int i = 1;

      for(String allowed = this.monitor.getProperty(base + i); allowed != null; allowed = this.monitor.getProperty(base + i)) {
         set.add(Pattern.compile(allowed.trim()));
         ++i;
      }

   }

   public void addAllHostPatterns() throws PatternSyntaxException {
      this.hostPatterns.add(Pattern.compile("0\\..*"));
      String os = ClientShared.getOSString();
      this.addAllAllowedPatterns("host.allow.pattern.", this.hostPatterns);
      this.addAllAllowedPatterns("host." + os + ".allow.pattern.", this.hostPatterns);
   }

   public void addAllCorporations() {
      this.addAllAllowed("corporation.allow.", this.corporations);
   }

   public void addAllCorporationPatterns() throws PatternSyntaxException {
      String os = ClientShared.getOSString();
      this.addAllAllowedPatterns("corporation.allow.pattern.", this.corporationPatterns);
      this.addAllAllowedPatterns("corporation." + os + ".allow.pattern.", this.corporationPatterns);
   }

   protected boolean isAllowed(String host) {
      Boolean knownHost = (Boolean)this.hosts.get(host);
      if (knownHost != null) {
         return knownHost;
      } else {
         boolean resultOfPatternLookup = this.isAllowedHostPattern(host);
         if (resultOfPatternLookup) {
            this.hosts.put(host, Boolean.TRUE);
            return resultOfPatternLookup;
         } else {
            String corporation = (String)this.cache.get(host);
            if (corporation != null) {
               return this.isAllowedHostForCorporation(host, corporation);
            } else {
               corporation = askLogService(host, this.monitor.getProperty("location.database.corporation.live", "true"));
               if (corporation != null) {
                  this.cache.put(host, corporation);
                  return this.isAllowedHostForCorporation(host, corporation);
               } else {
                  return false;
               }
            }
         }
      }
   }

   private boolean isAllowedHostForCorporation(String host, String corporation) {
      boolean resultOfLookup = this.isAllowedCorporation(corporation);
      if (resultOfLookup) {
         this.hosts.put(host, Boolean.TRUE);
      } else {
         this.hosts.put(host, Boolean.FALSE);
      }

      return resultOfLookup;
   }

   private boolean isAllowedCorporation(String corporation) {
      return this.corporations.contains(corporation) ? true : this.isAllowedCorporationPattern(corporation);
   }

   protected boolean isProcessTrusted(String line) {
      for(String process : this.processes) {
         if (line.startsWith(process)) {
            return true;
         }
      }

      return false;
   }

   protected boolean isAllowedHostPattern(String host) {
      return this.isAllowedPattern(host, this.hostPatterns);
   }

   protected boolean isAllowedCorporationPattern(String organization) {
      return this.isAllowedPattern(organization, this.corporationPatterns);
   }

   protected boolean isAllowedPattern(String obj, HashSet patterns) {
      for(Pattern pattern : patterns) {
         Matcher matcher = pattern.matcher(obj);
         if (matcher.matches()) {
            return true;
         }
      }

      return false;
   }

   private static String askLogService(String host, String live) {
      if (isPrivateIPv4(host)) {
         return "private";
      } else {
         try {
            Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
            WebTarget webTarget = client.target(ClientShared.BASE_LOG).path("lookup");
            Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
            invocationBuilder.accept(new String[]{"application/json"});
            Form form = new Form();
            form.param("passkey", ClientShared.PASSKEY_LOG);
            form.param("name", host);
            form.param("live", live);
            Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
            return (String)response.readEntity(String.class);
         } catch (Exception var7) {
            return null;
         }
      }
   }

   protected void notify(String host, String line) {
      boolean notify = true;
      String organization = (String)this.cache.get(host);
      if (organization == null) {
         organization = Whois.getOrganization(host);
         notify = !this.isAllowedCorporationPattern(organization);
      }

      if (notify) {
         if (organization.startsWith("No match found")) {
            this.monitor.notifyListeners(this.monitor.getResourceType(), "No organization found:\n" + line);
         } else if (this.includeRawLog) {
            this.monitor.notifyListenersIfNotCached(this.monitor.getResourceType(), host + " " + organization + "\n" + line);
         } else {
            this.monitor.notifyListenersIfNotCached(this.monitor.getResourceType(), host + " " + organization);
         }
      }

   }

   public static boolean isPrivateIPv4(String ipAddress) {
      try {
         InetAddress inetAddress = InetAddress.getByName(ipAddress);
         byte[] addressBytes = inetAddress.getAddress();
         if (addressBytes.length != 4) {
            return false;
         } else {
            int firstOctet = Byte.toUnsignedInt(addressBytes[0]);
            int secondOctet = Byte.toUnsignedInt(addressBytes[1]);
            if (firstOctet == 10) {
               return true;
            } else if (firstOctet == 172 && secondOctet >= 16 && secondOctet <= 31) {
               return true;
            } else {
               return firstOctet == 192 && secondOctet == 168;
            }
         }
      } catch (UnknownHostException var5) {
         return false;
      }
   }

   public void run() {
      if (!this.isRunning()) {
         this.monitor.clear();

         try {
            this.running = true;
            ProcessBuilder builder = new ProcessBuilder(this.cmd);
            builder.redirectErrorStream(true);
            this.process = builder.start();
            InputStream stdout = this.process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
            this.logger.begin();

            String line;
            while((line = reader.readLine()) != null) {
               this.logger.log(line);
               if (this.isIllegal(line)) {
                  this.notify(this.remoteHost, line);
               }
            }

            this.logger.end();
         } catch (Exception e) {
            this.monitor.notifyListeners(this.monitor.getResourceType(), "Network monitoring exception: " + String.valueOf(e));
         } finally {
            this.running = false;
            this.close();
         }

      }
   }

   public static void main(String[] args) {
      String[] testIPs = new String[]{"10.0.0.1", "172.16.0.1", "192.168.1.1", "8.8.8.8", "172.32.0.1"};

      for(String ip : testIPs) {
         System.out.println(ip + " is private: " + isPrivateIPv4(ip));
      }

      try {
         System.out.println("\nLocalhost: " + InetAddress.getLocalHost().getHostAddress());
      } catch (UnknownHostException e1) {
         e1.printStackTrace();
      }

      System.out.println("0.1.0.3 " + "0.1.0.3".matches("0\\..*"));
      System.out.println("1.1.2.3 " + "1.1.2.3".matches("0\\..*"));
      String live = "true";

      try {
         ClientShared.LOG_HOST = "comas-home.cogerent.com";
         ClientShared.updateURLs();
         System.out.println("24.114.96.54 " + askLogService("24.114.96.54", live));
         System.out.println("174.93.24.165 " + askLogService("174.93.24.165", live));
         System.out.println("172.17.131.67 " + askLogService("172.17.131.67", live));
         System.out.println("LAPTOP " + askLogService("LAPTOP", live));
      } catch (Exception e) {
         e.printStackTrace();
      }

   }
}
