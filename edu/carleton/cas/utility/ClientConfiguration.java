package edu.carleton.cas.utility;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.file.Utils;
import java.io.File;
import java.io.FileFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;

public class ClientConfiguration {
   public static final String COMAS_DOT = "comas.";
   public static final String COMAS_DOT_REMOVE = "comas.remove.";
   public static final int DEFAULT_MAX_ENTRIES = 10;
   private String name;
   private Properties configuration = new Properties();
   private int maxEntries;
   private String comments;

   public ClientConfiguration(String name) {
      this.name = name.trim();
      this.maxEntries = 10;
   }

   public Properties getConfiguration() {
      return new Properties(this.configuration);
   }

   public Set propertyNames() {
      return this.configuration.stringPropertyNames();
   }

   public boolean delete() {
      File f = new File(this.name);
      if (f.exists()) {
         f.deleteOnExit();
         return true;
      } else {
         return false;
      }
   }

   public void remove() {
      File f = new File(this.name);
      File dir = f.getParentFile();
      File[] loginDotJar = dir.listFiles(new FileFilter() {
         public boolean accept(File file) {
            String name = file.getName();
            if (name.equals("Login.jar")) {
               return false;
            } else {
               return name.startsWith("Login") && name.endsWith(".jar");
            }
         }
      });
      if (loginDotJar != null) {
         for(int i = 0; i < loginDotJar.length; ++i) {
            loginDotJar[i].deleteOnExit();
         }
      }

   }

   public boolean load() {
      Properties p = Utils.getPropertiesFromFile(this.name);
      if (p == null) {
         p = Utils.getProperties(this.getClass().getResource("/" + ClientShared.COMAS_DOT_INI));
         if (p == null) {
            return false;
         }

         this.configuration = p;
      } else {
         this.configuration = p;
      }

      this.maxEntries = this.getMaxEntries();
      return !this.configuration.isEmpty();
   }

   public boolean loadFromXML() {
      String nameWithXML = Utils.removeExtension(this.name) + ".xml";
      Properties p = Utils.getPropertiesFromXMLFile(nameWithXML);
      if (p == null) {
         p = Utils.getProperties(this.getClass().getResource("/" + ClientShared.COMAS_DOT_XML));
         if (p == null) {
            return false;
         }

         this.configuration = p;
      } else {
         this.configuration = p;
      }

      this.maxEntries = this.getMaxEntries();
      return !this.configuration.isEmpty();
   }

   public boolean save() {
      return this.save(this.comments);
   }

   public boolean save(String comments) {
      this.comments = comments;
      return Utils.savePropertiesToFile(this.configuration, comments, this.name);
   }

   public boolean saveToXML(String comments) {
      String nameWithXML = Utils.removeExtension(this.name) + ".xml";
      return Utils.savePropertiesToXMLFile(this.configuration, comments, nameWithXML);
   }

   private String key(int i) {
      return "comas." + i;
   }

   public boolean hasOneHost() {
      if (!this.hasHost()) {
         return false;
      } else {
         for(int i = 2; i <= this.maxEntries; ++i) {
            if (this.hasHost(i)) {
               return false;
            }
         }

         return true;
      }
   }

   public boolean hasHost() {
      return this.hasHost(1);
   }

   public boolean hasHost(int i) {
      return this.configuration.containsKey(this.key(i));
   }

   public String getHost() {
      return this.getHost(1);
   }

   public String getHost(int i) {
      return this.configuration.getProperty(this.key(i));
   }

   public String[] getHosts() {
      int i = 1;
      ArrayList<String> possibilities = new ArrayList();
      if (ClientShared.LOOK_FOR_SERVICES) {
         possibilities.add(ClientShared.DEFAULT_HOST);
      }

      for(; this.hasHost(i); ++i) {
         String host = this.getHost(i).trim();
         if (!possibilities.contains(host)) {
            possibilities.add(host);
         }
      }

      return (String[])possibilities.toArray(new String[possibilities.size()]);
   }

   public void setHost(String host) {
      this.setHost(1, host);
   }

   public void setHost(int i, String host) {
      if (i > 0 && i <= this.maxEntries && host != null) {
         this.configuration.setProperty(this.key(i), host);
      }

   }

   public void setRecentHost(String host) {
      String[] hosts = this.getHosts();
      int hostIndex = 2;

      for(int i = 0; i < hosts.length; ++i) {
         if (!host.equals(hosts[i])) {
            this.setHost(hostIndex, hosts[i]);
            ++hostIndex;
         }
      }

      for(int i = this.maxEntries - 1; i < hosts.length; ++i) {
         this.configuration.remove(this.key(i + 1));
      }

      this.setHost(host);
   }

   public void addHost(String newHost) {
      String[] hosts = this.getHosts();
      String trimmedHost = newHost.trim();
      int index;
      if (hosts != null) {
         index = hosts.length + 1;

         for(String host : hosts) {
            if (host.equals(trimmedHost)) {
               return;
            }
         }
      } else {
         index = 1;
      }

      this.configuration.setProperty("comas." + index, trimmedHost);
   }

   public void removeHost(String oldHost) {
      String trimmedHost = oldHost.trim();
      int i = 1;
      int index = -1;

      for(String host = this.configuration.getProperty(this.key(i)); host != null; host = this.configuration.getProperty(this.key(i))) {
         if (trimmedHost.equals(host)) {
            this.configuration.remove(this.key(i));
            index = i;
         }

         ++i;
      }

      if (index > 0) {
         for(int j = index; j < i; ++j) {
            String var7 = this.configuration.getProperty(this.key(j + 1));
            if (var7 != null) {
               this.configuration.setProperty(this.key(j), var7);
            }
         }
      }

   }

   public boolean removeProperty(String property) {
      if (property == null) {
         return false;
      } else if (property.startsWith("comas.")) {
         return false;
      } else {
         return this.configuration.remove(property) != null;
      }
   }

   public void setProperty(String property, String value) {
      if (property != null && value != null && !property.startsWith("comas.")) {
         this.configuration.setProperty(property, value);
      }

   }

   public String getFirst() {
      String value = this.configuration.getProperty("first");
      return value != null ? value.trim() : null;
   }

   public String getLast() {
      String value = this.configuration.getProperty("last");
      return value != null ? value.trim() : null;
   }

   public String getID() {
      String value = this.configuration.getProperty("id");
      return value != null ? value.trim() : null;
   }

   public String getEmail() {
      String value = this.configuration.getProperty("email");
      return value != null ? value.trim() : null;
   }

   public String getCourse() {
      String value = this.configuration.getProperty("session.course");
      return value != null ? value.trim() : null;
   }

   public String getActivity() {
      String value = this.configuration.getProperty("session.activity");
      return value != null ? value.trim() : null;
   }

   public String getPassword() {
      String value = this.configuration.getProperty("password");
      return value != null ? value.trim().toLowerCase() : "false";
   }

   public long getLastSession() {
      String value = this.configuration.getProperty("session.last");
      if (value != null) {
         value = value.trim().toLowerCase();

         try {
            return Long.parseLong(value);
         } catch (NumberFormatException var3) {
            return System.currentTimeMillis();
         }
      } else {
         return System.currentTimeMillis();
      }
   }

   public long getAgreedToMonitor() {
      String value = this.configuration.getProperty("session.agreedToMonitor");
      if (value != null) {
         value = value.trim().toLowerCase();

         try {
            return Long.parseLong(value);
         } catch (NumberFormatException var3) {
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   public long getReadMOTD() {
      String value = this.configuration.getProperty("session.readMOTD");
      if (value != null) {
         value = value.trim().toLowerCase();

         try {
            return Long.parseLong(value);
         } catch (NumberFormatException var3) {
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   public URI getStartupScreenURI() throws URISyntaxException {
      String value = this.configuration.getProperty("session.startup_screen");
      if (value != null) {
         value = value.trim();
         return new URI(value);
      } else {
         return null;
      }
   }

   public String getIPv4Address() {
      String value = this.configuration.getProperty("session.IPv4Address");
      if (value != null) {
         value = value.trim().toLowerCase();
         return value;
      } else {
         return "unknown";
      }
   }

   public String getFolder() {
      String value = this.configuration.getProperty("session.last.folder");
      if (value != null) {
         value = value.trim();
         return value;
      } else {
         return ClientShared.DIR;
      }
   }

   public long getPID() {
      String value = this.configuration.getProperty("session.pid");
      if (value != null) {
         value = value.trim();

         try {
            return Long.parseLong(value);
         } catch (NumberFormatException var3) {
            this.setPID(-1L);
         }
      }

      return -1L;
   }

   public int getPort() {
      String value = this.configuration.getProperty("session.store.web_server.port");
      if (value != null) {
         value = value.trim();

         try {
            int port = Integer.parseInt(value);
            if (port > 0 && port < 65535) {
               return port;
            }
         } catch (NumberFormatException var3) {
         }
      }

      return -1;
   }

   public boolean getCheckConnection() {
      String value = this.configuration.getProperty("session.check_connection");
      if (value != null) {
         value = value.trim();
         return value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true");
      } else {
         return false;
      }
   }

   public int getMaxEntries() {
      String value = this.configuration.getProperty("maxEntries");
      if (value != null) {
         value = value.trim().toLowerCase();

         try {
            return Integer.parseInt(value);
         } catch (NumberFormatException var3) {
            this.setMaxEntries(10);
            return 10;
         }
      } else {
         this.setMaxEntries(10);
         return 10;
      }
   }

   public void forgetHosts() {
      int i = 1;

      for(String host = this.configuration.getProperty(this.key(i)); host != null; host = this.configuration.getProperty(this.key(i))) {
         this.configuration.remove(this.key(i));
         ++i;
      }

   }

   public void setFirst(String value) {
      this.setProperty("first", value);
   }

   public void setLast(String value) {
      this.setProperty("last", value);
   }

   public void setID(String value) {
      this.setProperty("id", value);
   }

   public void setEmail(String value) {
      this.setProperty("email", value);
   }

   public void setCourse(String value) {
      this.setProperty("session.course", value);
   }

   public void setActivity(String value) {
      this.setProperty("session.activity", value);
   }

   public void setLastSession(long value) {
      this.setProperty("session.last", String.format("%d", value));
   }

   public void setMaxEntries(int value) {
      this.setProperty("maxEntries", String.format("%d", value));
   }

   public void setIPv4Address(String address) {
      this.setProperty("session.IPv4Address", address);
   }

   public void removeAgreedToMonitor() {
      this.configuration.remove("session.agreedToMonitor");
   }

   public void setAgreedToMonitor(long value) {
      this.setProperty("session.agreedToMonitor", String.format("%d", value));
   }

   public void setReadMOTD(long value) {
      this.setProperty("session.readMOTD", String.format("%d", value));
   }

   public void setFolder(String folder) {
      this.setProperty("session.last.folder", folder);
   }

   public void setStartupScreenURI(String uri) {
      if (uri != null && uri.trim().length() != 0) {
         this.setProperty("session.startup_screen", uri.trim());
      } else {
         this.configuration.remove("session.startup_screen");
      }

   }

   public void setPID(long pid) {
      this.setProperty("session.pid", String.format("%d", pid));
   }

   public int sanitize() {
      String[] sessionVariables = new String[]{".activity", ".course", ".last", ".last.folder", ".IPv4Address", ".pid", ".agreedToMonitor", ".readMOTD", ".startup_screen", ".use_default_browser", ".check_connection"};
      String[] systemVariables = new String[]{".resources.entity", ".resources.passcode", ".resources.name", ".resources.id"};
      String[] mailVariables = new String[]{".to", ".from", ".cc", ".bcc", ".subject", ".body"};
      String[] webserverVariables = new String[]{".port", ".host", ".window_open", ".page_refresh", ".alert_timeout", ".variable_refresh"};
      int count = 0;

      for(String name : this.configuration.stringPropertyNames()) {
         boolean ok = false;
         if (name.startsWith("comas.")) {
            ok = true;
         } else if (name.equals("first")) {
            ok = true;
         } else if (name.equals("last")) {
            ok = true;
         } else if (name.equals("maxEntries")) {
            ok = true;
         } else if (name.equals("id")) {
            ok = true;
         } else if (name.equals("email")) {
            ok = true;
         } else if (name.startsWith("session.store.")) {
            ok = true;
         } else if (name.startsWith("web_server.")) {
            for(String variable : webserverVariables) {
               if (name.endsWith(variable)) {
                  ok = true;
                  break;
               }
            }
         } else if (name.startsWith("session.")) {
            for(String variable : sessionVariables) {
               if (name.endsWith(variable)) {
                  ok = true;
                  break;
               }
            }
         } else if (name.startsWith("system.")) {
            for(String variable : systemVariables) {
               if (name.endsWith(variable)) {
                  ok = true;
                  break;
               }
            }
         } else if (name.startsWith("mail.")) {
            for(String variable : mailVariables) {
               if (name.endsWith(variable)) {
                  ok = true;
                  break;
               }
            }
         }

         if (!ok) {
            this.removeProperty(name);
            ++count;
         }
      }

      return count;
   }
}
