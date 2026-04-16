package com.cogerent.dns;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import org.xbill.DNS.Name;
import org.xbill.DNS.TextParseException;

public class DNSZone {
   public String zoneString = "$TTL    60\n$ORIGIN microsoft.com.\n@    IN    SOA    ns1.microsoft.com. admin.microsoft.com(\n                  670\n             604800\n              86400\n            2419200\n              60 )\n\n@                  IN NS ns1.microsoft.com.\n@                  60 IN A 192.168.2.21\nns1.microsoft.com.   60 IN A 192.168.2.21\n\nwww.microsoft.com.                60 IN A  20.112.250.133";
   public static String zoneStringFormat = "$TTL    60\n$ORIGIN %s.\n@    IN    SOA    ns1.%s. admin.%s. (\n                  670\n             604800\n              86400\n            2419200\n              60 )\n\n@                  IN NS ns1.%s.\n@                  60 IN A %s\nns1.%s.   60 IN A %s\n\nwww.%s.                60 IN A  %s";
   public static String zoneStringFormat2 = "$TTL    60\n$ORIGIN %s.\n@    IN    SOA    ns1.%s. admin.%s. (\n                  670\n             604800\n              86400\n            2419200\n              60 )\n\n@                  IN NS ns1.%s.\n@                  60 IN A %s\nns1.%s.   60 IN A %s\n\n";

   public static String makeZone(String domain, String ipDNS) {
      return String.format(zoneStringFormat2, domain, domain, domain, domain, ipDNS, domain, ipDNS);
   }

   public static String makeZone(String domain, String ipDNS, String ip) {
      return String.format(zoneStringFormat, domain, domain, domain, domain, ipDNS, domain, ipDNS, domain, ip);
   }

   public static File makeZoneFile(String domain, String ipDNS, String ip) throws IOException {
      File f = File.createTempFile("zone", "tmp");
      PrintWriter pw = new PrintWriter(f);
      pw.append(makeZone(domain, ipDNS, ip));
      pw.close();
      return f;
   }

   private static String domain(Name name) {
      String s = name.toString(true);
      String[] tokens = s.split("\\.");
      String domain = "";
      System.out.println(tokens.length);
      if (tokens.length > 2) {
         for(int i = tokens.length - 2; i < tokens.length; ++i) {
            domain = domain + tokens[i];
            if (i < tokens.length - 1) {
               domain = domain + ".";
            }
         }
      } else {
         domain = s;
      }

      return domain;
   }

   public static void main(String[] args) throws TextParseException {
      System.out.println(makeZone("microsoft.com", "192.168.2.21", "20.112.250.133"));
      Name n1 = new Name("www.microsoft.com", Name.root);
      Name n2 = new Name("microsoft.com", Name.root);
      PrintStream var10000 = System.out;
      boolean var10001 = n1.subdomain(n2);
      var10000.println("Test: " + var10001);
      System.out.println("n1: " + domain(n1));
      System.out.println("n2: " + domain(n2));
   }
}
