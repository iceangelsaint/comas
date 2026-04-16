package com.cogerent.dns;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class DNSLookup {
   public static void main(String[] args) {
      System.out.println(String.join(",", getDNSConfig()));
   }

   public static List getDNSConfig() {
      String os = System.getProperty("os.name").toLowerCase();
      if (os.contains("win")) {
         return getWindowsDNSConfig();
      } else {
         return !os.contains("nix") && !os.contains("nux") && !os.contains("mac") ? null : getUnixDNSConfig();
      }
   }

   private static List getWindowsDNSConfig() {
      ArrayList<String> dnsServers = new ArrayList();

      try {
         Process process = Runtime.getRuntime().exec("ipconfig /all");
         BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
         boolean dnsSection = false;

         String line;
         while((line = reader.readLine()) != null) {
            if (line.trim().startsWith("DNS Servers")) {
               dnsSection = true;
            } else if (dnsSection && !line.trim().isEmpty()) {
               dnsServers.add(line.trim());
            }
         }

         reader.close();
      } catch (IOException e) {
         e.printStackTrace();
      }

      return dnsServers;
   }

   private static List getUnixDNSConfig() {
      ArrayList<String> dnsServers = new ArrayList();

      try {
         Process process = Runtime.getRuntime().exec("cat /etc/resolv.conf");
         BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

         String line;
         while((line = reader.readLine()) != null) {
            if (line.trim().startsWith("nameserver")) {
               String[] parts = line.split("\\s+");
               if (parts.length >= 2) {
                  dnsServers.add(parts[1]);
               }
            }
         }

         reader.close();
      } catch (IOException e) {
         e.printStackTrace();
      }

      return dnsServers;
   }
}
