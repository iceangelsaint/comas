package com.cogerent.dns;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Timer;
import se.unlogic.eagledns.EagleDNS;
import se.unlogic.eagledns.Status;

public class DNS {
   private DNSConfiguration dnsConfiguration;
   private EagleDNS server;

   public DNS(File hosts, Timer timer, String[] dnsServers) throws IOException {
      this.dnsConfiguration = new DNSConfiguration(hosts, timer, dnsServers);
   }

   public DNS(String sURL, String property, String cookie, Timer timer, String[] dnsServers) throws IOException {
      this.dnsConfiguration = new DNSConfiguration(sURL, property, cookie, timer, dnsServers);
   }

   public boolean isViable() {
      return this.dnsConfiguration.isViable();
   }

   public boolean isStarted() {
      if (this.server == null) {
         return false;
      } else {
         return this.server.getStatus() == Status.STARTED;
      }
   }

   public void start() throws IOException {
      this.dnsConfiguration.start();
      this.createServer();
   }

   private void saveXMLData() {
      File saved = new File("comas-stats-old.xml");
      saved.delete();
      (new File("comas-stats.xml")).renameTo(saved);
      saved = new File("comas-old.log");
      saved.delete();
      (new File("comas.log")).renameTo(saved);
   }

   public void stop() {
      this.dnsConfiguration.stop();
      (new File("named.conf.local")).delete();
      this.removeFiles("zones");
      this.removeFiles("conf");
      this.saveXMLData();
   }

   private void removeFiles(String folder) {
      File fileFolder = new File(folder);
      File[] folderFiles = fileFolder.listFiles();
      if (folderFiles != null) {
         for(File folderFile : folderFiles) {
            folderFile.delete();
         }
      }

      fileFolder.delete();
   }

   private void createServer() throws IOException {
      if (this.server == null) {
         (new File("zones")).mkdirs();
         (new File("conf")).mkdirs();
         Files.copy(EagleDNS.class.getResourceAsStream("/conf/config.xml"), Paths.get("conf", "config.xml"), new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
         Files.copy(EagleDNS.class.getResourceAsStream("/conf/log4j.xml"), Paths.get("conf", "log4j.xml"), new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
         this.server = new EagleDNS("conf" + File.separator + "config.xml", "conf" + File.separator + "log4j.xml");
      }

   }
}
