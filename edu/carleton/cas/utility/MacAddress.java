package edu.carleton.cas.utility;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class MacAddress {
   public static String getMACAddress() throws UnknownHostException, SocketException {
      InetAddress localHost = InetAddress.getLocalHost();
      NetworkInterface ni = NetworkInterface.getByInetAddress(localHost);
      if (ni != null) {
         byte[] hardwareAddress = ni.getHardwareAddress();
         return convertToMacAddress(hardwareAddress, "-");
      } else {
         return null;
      }
   }

   public static String getMACAddress(String host) throws UnknownHostException, SocketException {
      InetAddress localHost = InetAddress.getByName(host);
      NetworkInterface ni = NetworkInterface.getByInetAddress(localHost);
      if (ni != null) {
         byte[] hardwareAddress = ni.getHardwareAddress();
         return convertToMacAddress(hardwareAddress, "-");
      } else {
         return null;
      }
   }

   public static String getMACAddresses() throws SocketException {
      return getMACAddresses("-");
   }

   public static String getMACAddresses(String join) throws SocketException {
      Enumeration<NetworkInterface> ni = NetworkInterface.getNetworkInterfaces();
      StringBuffer buf = new StringBuffer();

      while(ni.hasMoreElements()) {
         String address = convertToMacAddress(((NetworkInterface)ni.nextElement()).getHardwareAddress(), join);
         if (address.length() > 0) {
            buf.append(address);
            if (ni.hasMoreElements() && join.length() > 0) {
               buf.append(" ");
            }
         }
      }

      return buf.toString();
   }

   private static String convertToMacAddress(byte[] hardwareAddress, String join) {
      if (hardwareAddress == null) {
         return "";
      } else {
         String[] hexadecimal = new String[hardwareAddress.length];

         for(int i = 0; i < hardwareAddress.length; ++i) {
            hexadecimal[i] = String.format("%02X", hardwareAddress[i]);
         }

         String macAddress = String.join(join, hexadecimal);
         return macAddress;
      }
   }

   public static String getInterfaceDisplayNames() throws SocketException {
      Enumeration<NetworkInterface> ni = NetworkInterface.getNetworkInterfaces();
      StringBuffer buf = new StringBuffer();

      while(ni.hasMoreElements()) {
         String address = ((NetworkInterface)ni.nextElement()).getDisplayName();
         if (address.length() > 0) {
            buf.append(address);
            if (ni.hasMoreElements()) {
               buf.append(" ");
            }
         }
      }

      return buf.toString();
   }

   public static void main(String[] args) throws SocketException {
      System.out.println(getMACAddresses(""));
      System.out.println(getMACAddresses());
      System.out.println(getInterfaceDisplayNames());
   }
}
