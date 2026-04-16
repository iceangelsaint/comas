package com.cogerent.dns;

import com.cogerent.utility.PropertiesEditor;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.resources.CommandRunner;
import edu.carleton.cas.resources.LineContainingPatternProcessor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;

public class DNSConfiguration {
   private static final int MAX_TRIES = 5;
   private static final int SECONDS_IN_24_HOURS = 86400;
   private static final String GOOGLE_DNS = "8.8.8.8";
   private static final String[] DEFAULT_DNS_SERVERS = new String[]{"8.8.8.8"};
   private static final String DEFAULT = "${default}";
   private static final String ZONE = "${zone}";
   private static final String[] CONFIGURE_ZONE = new String[]{"${zone}"};
   private static final String NAMED_DOT_CONF_DOT_LOCAL = "named.conf.local";
   private static final String ZONES = "zones";
   private static final Set rw_perms = PosixFilePermissions.fromString("rw-rw-rw-");
   private static final Set rwx_perms = PosixFilePermissions.fromString("rwxrwxrwx");
   private final ConcurrentHashMap zoneMap;
   private String[] defaultServers;
   private String zoneFolder;
   private String zoneReloadCommand;
   private int zoneUpdateFrequency;
   private Timer timer;
   private File hosts;
   private PropertiesEditor pe;
   private ZoneUpdate timerTask;

   public DNSConfiguration(File hosts, Timer timer, String[] dnsServers) throws IOException {
      this.timer = timer;
      this.hosts = hosts;
      this.zoneUpdateFrequency = 60000;
      this.zoneMap = new ConcurrentHashMap();
      this.setDefaultServers(dnsServers);
      this.setup(hosts);
   }

   public DNSConfiguration(String sURL, String property, String cookie, Timer timer, String[] dnsServers) throws IOException {
      this.zoneMap = new ConcurrentHashMap();
      this.timer = timer;
      this.setDefaultServers(dnsServers);
      this.setup(sURL, property, cookie);
   }

   public void setDefaultServers(String[] defaultServers) {
      if (defaultServers != null && defaultServers.length != 0) {
         this.defaultServers = defaultServers;
      } else {
         this.defaultServers = DEFAULT_DNS_SERVERS;
      }

   }

   public synchronized void start() {
      this.scheduleZoneUpdating();
   }

   private void scheduleZoneUpdating() {
      if (this.timer != null) {
         try {
            this.timerTask = new ZoneUpdate(this);
            this.timer.schedule(this.timerTask, (long)this.zoneUpdateFrequency);
            Logger.log(Level.INFO, "Zones will be updated in " + this.zoneUpdateFrequency / 1000, " seconds");
         } catch (IllegalStateException e) {
            Logger.log(Level.WARNING, "Could not schedule zone updates. ", e);
         }
      }

   }

   public boolean isViable() {
      return !this.zoneMap.isEmpty();
   }

   public synchronized void stop() {
      if (this.timer != null) {
         this.timer.cancel();
         Logger.log(Level.INFO, "Stopped updating zones", "");
      }

   }

   public void setup(File hosts) throws IOException {
      if (hosts != null) {
         this.pe = new PropertiesEditor();
         this.pe.load(hosts);
      }

      this.processPropertiesEditor();
   }

   public void setup(String sURL, String property, String cookie) throws IOException {
      this.pe = null;
      int tries = 0;

      do {
         this.pe = this.getPropertiesEditor(sURL, property, cookie);
      } while(this.pe.size() == 0 && tries++ < 5);

      this.processPropertiesEditor();
   }

   public PropertiesEditor getPropertiesEditor(String sURL, String property, String cookie) {
      PropertiesEditor properties = new PropertiesEditor();
      InputStream is = null;

      try {
         URL url = new URL(sURL);
         URLConnection conn = url.openConnection();
         if (property != null && cookie != null) {
            conn.setRequestProperty(property, cookie);
         }

         is = conn.getInputStream();
         properties.load(is);
      } catch (MalformedURLException e) {
         System.err.println(e);
      } catch (IOException e) {
         System.err.println(e);
      } finally {
         if (is != null) {
            try {
               is.close();
            } catch (IOException var17) {
            }
         }

      }

      return properties;
   }

   private synchronized void processPropertiesEditor() throws IOException {
      if (this.pe != null) {
         String defaultServerSpecification = this.pe.getProperty("dns.default_servers");
         if (defaultServerSpecification != null) {
            String[] servers = defaultServerSpecification.split(",");
            if (servers != null) {
               for(int i = 0; i < servers.length; ++i) {
                  servers[i] = servers[i].trim();
               }

               this.setDefaultServers(servers);
            }
         }

         this.zoneFolder = this.pe.getProperty("dns.zone_folder", System.getProperty("user.dir")).trim();
         if (!this.zoneFolder.endsWith(File.separator)) {
            String var10001 = String.valueOf(this.zoneFolder);
            this.zoneFolder = var10001 + File.separator;
         }

         this.zoneReloadCommand = this.pe.getProperty("dns.reload_command", "").trim();
         int existingZoneUpdateFrequency = this.zoneUpdateFrequency;
         this.zoneUpdateFrequency = Utils.getIntegerOrDefaultInRange(this.pe, "dns.update_frequency", this.zoneUpdateFrequency / 1000, 60, 3600) * 1000;
         if (existingZoneUpdateFrequency != this.zoneUpdateFrequency) {
            if (this.timerTask != null) {
               this.timerTask.ignore();
            }

            this.scheduleZoneUpdating();
         }

         this.configureZones();
         this.createZoneConfigurationFiles();
         this.reloadZones();
      }
   }

   private void configureZones() throws IOException {
      this.zoneMap.clear();
      String[] cacheProperty = this.pe.getPropertyValue("dns.cache");
      if (cacheProperty != null) {
         for(String value : cacheProperty) {
            try {
               String[] tokens = value.trim().split(":");
               if (tokens.length == 2) {
                  String[] addressArray = tokens[1].trim().split(",");
                  if (addressArray != null) {
                     Name name = new Name(tokens[0].trim(), Name.root);
                     this.configureZone(name, addressArray);
                  }
               } else {
                  if (tokens.length != 1) {
                     throw new IllegalArgumentException("Illegal number of cache tokens provided: " + tokens.length);
                  }

                  Name name = new Name(tokens[0].trim(), Name.root);
                  this.configureZone(name, CONFIGURE_ZONE);
               }
            } catch (IllegalArgumentException e) {
               Logger.log(Level.WARNING, value + " ", e);
            }
         }
      }

   }

   private void configureZone(Name name, String[] addressArray) throws IOException {
      Name domain = this.domain(name);
      HashSet<Record> existing = (HashSet)this.zoneMap.get(domain);
      if (existing == null) {
         Logger.log(Level.INFO, String.join(",", this.defaultServers) + " used to configure zone: ", domain.toString(true));
         existing = new HashSet();
         this.zoneMap.put(domain, existing);
      }

      for(int i = 0; i < addressArray.length; ++i) {
         String address = addressArray[i].trim();
         if (address.length() != 0) {
            if (!address.equalsIgnoreCase("${zone}")) {
               if (address.equalsIgnoreCase("${default}")) {
                  this.lookupAndAddToExisting(name, 1, existing);
               } else {
                  existing.add(this.makeRecord(name, address));
               }
            } else {
               Record[] records = this.lookup(name, 5);
               if (records != null) {
                  for(Record record : records) {
                     existing.add(record);
                     if (record.getType() == 5) {
                        CNAMERecord crecord = (CNAMERecord)record;
                        this.configureZone(crecord.getTarget(), CONFIGURE_ZONE);
                     }
                  }
               }

               this.lookupAndAddToExisting(name, 1, existing);
            }
         }
      }

   }

   private Record[] lookup(Name name, int type) throws UnknownHostException {
      SimpleResolver resolver = new SimpleResolver(this.defaultServers[0]);
      Lookup lookup = new Lookup(name, type);
      lookup.setResolver(resolver);
      return lookup.run();
   }

   private Record[] lookupAndAddToExisting(Name name, int type, HashSet existing) throws UnknownHostException {
      Record[] records = this.lookup(name, type);
      if (records != null) {
         for(Record record : records) {
            existing.add(record);
         }
      }

      return records;
   }

   private void createZoneConfigurationFiles() {
      this.refreshZoneFiles();
      this.createNamedDotConfDotLocalFile();
      this.createZoneFiles();
   }

   private void refreshZoneFiles() {
      (new File(this.zoneFolder + "named.conf.local")).delete();
      File zoneFileFolder = new File(this.zoneFolder + "zones" + File.separator);
      if (zoneFileFolder.exists() && zoneFileFolder.canRead() && zoneFileFolder.canWrite()) {
         File[] files = zoneFileFolder.listFiles();
         if (files != null) {
            for(File file : files) {
               file.delete();
            }
         }
      }

   }

   private void createZoneFiles() {
      String zonesFolder = this.zoneFolder + "zones" + File.separator;
      Path zonesFolderPath = Paths.get(zonesFolder);
      (new File(zonesFolder)).mkdirs();

      try {
         Files.setPosixFilePermissions(zonesFolderPath, rwx_perms);
      } catch (UnsupportedOperationException | IOException var4) {
      }

      this.zoneMap.forEach((k, v) -> {
         PrintWriter pw = null;
         Path path = Paths.get(zonesFolder + k.toString(true));

         try {
            pw = new PrintWriter(new FileOutputStream(zonesFolder + k.toString(true)));
            if (!this.containsSOA(v)) {
               pw.append(DNSZone.makeZone(k.toString(true), InetAddress.getLocalHost().getHostAddress()));
            }

            for(Record r : v) {
               pw.append(r.toString());
               pw.append("\n");
            }
         } catch (UnknownHostException | FileNotFoundException e) {
            Logger.log(Level.WARNING, "Could not create zone file. ", e);
         } finally {
            if (pw != null) {
               pw.close();
            }

            try {
               Files.setPosixFilePermissions(path, rw_perms);
            } catch (UnsupportedOperationException | IOException var15) {
            }

         }

      });
   }

   private void createNamedDotConfDotLocalFile() {
      try {
         Path path = Paths.get(this.zoneFolder + "named.conf.local");
         PrintWriter pw = new PrintWriter(new FileOutputStream(this.zoneFolder + "named.conf.local"));
         this.zoneMap.forEach((k, v) -> {
            pw.append("zone \"");
            pw.append(k.toString(true));
            pw.append("\" {\n");
            pw.append("\ttype master;\n");
            pw.append("\tfile \"");
            pw.append(this.zoneFolder);
            pw.append("zones");
            pw.append(File.separator);
            pw.append(k.toString(true));
            pw.append("\";\n};\n\n");
         });
         pw.close();
         Files.setPosixFilePermissions(path, rw_perms);
      } catch (UnsupportedOperationException | IOException e) {
         Logger.log(Level.WARNING, "Could not create zone file. ", e);
      }

   }

   private boolean containsSOA(HashSet records) {
      for(Record r : records) {
         if (r.getType() == 6) {
            return true;
         }
      }

      return false;
   }

   private Name domain(Name name) throws TextParseException {
      String nameAsStringWithoutDot = name.toString(true);
      String[] tokens = nameAsStringWithoutDot.split("\\.");
      String domain;
      if (tokens.length > 2) {
         domain = "";

         for(int i = tokens.length - 2; i < tokens.length; ++i) {
            domain = domain + tokens[i];
            if (i < tokens.length - 1) {
               domain = domain + ".";
            }
         }
      } else {
         domain = nameAsStringWithoutDot;
      }

      return new Name(domain, Name.root);
   }

   private Record makeRecord(Name name, String address) throws IOException {
      return Record.fromString(name, 1, 1, 86400L, address, Name.root);
   }

   private void reloadZones() {
      if (this.zoneReloadCommand != null && this.zoneReloadCommand.length() > 0) {
         LineContainingPatternProcessor lcpp = new LineContainingPatternProcessor(".*");
         (new CommandRunner(this.zoneReloadCommand.split(" "), lcpp)).run();
         Logger.log(Level.INFO, lcpp.result(), "");
      }

   }

   private final class ZoneUpdate extends TimerTask {
      final DNSConfiguration dns;
      boolean run;

      ZoneUpdate(DNSConfiguration dns) {
         this.dns = dns;
         this.run = true;
      }

      public void ignore() {
         this.run = false;
      }

      public void run() {
         if (this.run) {
            try {
               DNSConfiguration.this.setup(this.dns.hosts);
            } catch (IOException e) {
               Logger.log(Level.WARNING, "Could not uodate zones. ", e);
            } finally {
               this.dns.scheduleZoneUpdating();
            }

         }
      }
   }
}
