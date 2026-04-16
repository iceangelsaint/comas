package edu.carleton.cas.utility;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IPAddressChecker {
   public static final String address = "address.";
   public static final String defaultKey = "default";
   public static final String allow = "allow.";
   public static final String deny = "deny.";
   private static boolean default_allow = true;
   private static boolean default_deny = false;
   private static final String ip_address_regex = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$";
   private Properties config;
   private ArrayList deny_addresses;
   private ArrayList allow_addresses;
   private String localHost;

   public IPAddressChecker(Properties config) throws UnknownHostException {
      this.config = config;
      String id = config.getProperty("id");
      this.allow_addresses = this.getAddresses("allow.");
      if (id != null) {
         this.getAddresses(this.allow_addresses, id + ".allow.");
      }

      this.deny_addresses = this.getAddresses("deny.");
      if (id != null) {
         this.getAddresses(this.deny_addresses, id + ".deny.");
      }

      String value = config.getProperty(getDefault(), "true");
      if (value.equalsIgnoreCase("true")) {
         default_allow = true;
      } else if (value.equalsIgnoreCase("false")) {
         default_allow = false;
      }

      String address = config.getProperty("LOCAL_ADDRESS");
      if (address == null) {
         address = config.getProperty("LOCAL_HOST");
         if (address == null) {
            this.localHost = InetAddress.getLocalHost().getHostAddress();
         } else if (Pattern.matches("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$", address)) {
            this.localHost = address;
         } else {
            this.localHost = InetAddress.getLocalHost().getHostAddress();
         }
      } else if (Pattern.matches("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$", address)) {
         this.localHost = address;
      } else {
         this.localHost = InetAddress.getLocalHost().getHostAddress();
      }

   }

   public static final String getDefault() {
      return "address.default";
   }

   ArrayList getAddresses(String type) {
      return this.getAddresses(new ArrayList(), type);
   }

   ArrayList getAddresses(ArrayList addresses, String type) {
      int i = 1;
      String base = "address." + type;

      for(String value = this.config.getProperty(base + i); value != null; value = this.config.getProperty(base + i)) {
         addresses.add(value.trim());
         ++i;
      }

      return addresses;
   }

   public boolean allow() {
      for(String address : this.allow_addresses) {
         if (this.match(address)) {
            return true;
         }
      }

      return default_allow;
   }

   public boolean deny() {
      for(String address : this.deny_addresses) {
         if (this.match(address)) {
            return true;
         }
      }

      return default_deny;
   }

   public boolean match(String address) {
      if (address.charAt(0) == '^' && address.charAt(address.length() - 1) == '$') {
         Pattern p = Pattern.compile(address);
         Matcher m = p.matcher(this.localHost);
         return m.matches();
      } else {
         return this.localHost.equals(address);
      }
   }
}
