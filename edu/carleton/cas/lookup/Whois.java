package edu.carleton.cas.lookup;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.net.whois.WhoisClient;

public class Whois {
   public static ConcurrentHashMap cache = new ConcurrentHashMap();
   public static final String EMPTY = "";
   public static final String DEFAULT_SERVER = "whois.arin.net";
   public static String cached_result;

   public static void main(String[] args) {
      try {
         InetAddress ia = InetAddress.getByName("yyz10s14-in-f1.1e100.net");
         System.out.println(getWhois(ia.getHostAddress().toString(), "whois.arin.net"));
         System.out.println("\n\n**********WHOIS LOOKUP******************");
         System.out.println(getOrganization("yyz10s14-in-f1.1e100.net", "whois.arin.net"));
         System.out.println(getOrganization("174.93.24.165", "whois.arin.net"));
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   public static String getWhois(String domainName) {
      return getWhois(domainName, "whois.arin.net");
   }

   public static String getWhois(String domainName, String server) {
      String domainAddress;
      try {
         InetAddress address = InetAddress.getByName(domainName);
         if (address.isSiteLocalAddress()) {
            cache.put(domainName, "Local Area Network");
            cached_result = "Local Area Network";
            return cached_result;
         }

         domainAddress = address.getHostAddress().toString();
      } catch (UnknownHostException var7) {
         domainAddress = domainName;
      }

      WhoisClient whois = new WhoisClient();
      cached_result = "";

      try {
         whois.connect(server);
         cached_result = whois.query(domainAddress);
         whois.disconnect();
      } catch (Exception var6) {
      }

      return cached_result;
   }

   public static String getData(String domainName, String server, String entity) {
      String result = getWhois(domainName, server);
      String org = "";
      int index = result.indexOf(entity);
      if (index != -1) {
         int index2 = result.indexOf("\n", index);
         org = result.substring(index + entity.length(), index2).trim();
      }

      return org;
   }

   public static String getOrganization(String domainName) {
      return getOrganization(domainName, "whois.arin.net");
   }

   public static String getOrganization(String domainName, String server) {
      String organization = (String)cache.get(domainName);
      if (organization == null) {
         organization = getData(domainName, server, "Organization:");
         if (organization.equals("")) {
            String[] lines = cached_result.split("\n");

            for(String line : lines) {
               if (line.length() != 0 && line.charAt(0) != '#') {
                  int index = line.indexOf(40);
                  if (index == -1) {
                     organization = line;
                  } else {
                     organization = line.substring(0, index - 1);
                  }
                  break;
               }
            }
         }

         cache.put(domainName, organization);
      }

      return organization;
   }
}
