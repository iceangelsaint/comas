package edu.carleton.cas.utility;

import edu.carleton.cas.constants.ClientShared;

public abstract class DNSCacheFlusherCmd {
   public static final String[] windowsCmd = new String[]{"ipconfig", "/flushdns"};
   public static final String[] macOSCmd = new String[]{"sudo", "killall", "-HUP", "mDNSResponder", ";", "sudo", "killall", "mDNSResponderHelper", ";", "sudo", "dscacheutil", "-flushcache"};
   public static final String[] linuxCmd = new String[]{"systemd-resolve", "--flush-caches"};
   public static final String[] altLinuxCmd = new String[]{"resolvectl", "flush-caches"};
   public static final String[] unknown = new String[]{"unknown"};

   public static String[] getCmd() {
      if (ClientShared.isLinuxOS()) {
         return linuxCmd;
      } else if (ClientShared.isWindowsOS()) {
         return windowsCmd;
      } else {
         return ClientShared.isMacOS() ? macOSCmd : unknown;
      }
   }

   public static String[] getAltCmd() {
      return ClientShared.isLinuxOS() ? altLinuxCmd : unknown;
   }

   public static String[] copyToClipboard() {
      if (ClientShared.isLinuxOS()) {
         ClipboardManager.setContents(toString(linuxCmd));
         return linuxCmd;
      } else if (ClientShared.isWindowsOS()) {
         ClipboardManager.setContents(toString(windowsCmd));
         return windowsCmd;
      } else if (ClientShared.isMacOS()) {
         ClipboardManager.setContents(toString(macOSCmd));
         return macOSCmd;
      } else {
         return unknown;
      }
   }

   private static String toString(String[] args) {
      StringBuffer sb = new StringBuffer();

      for(String arg : args) {
         sb.append(arg);
         sb.append(' ');
      }

      return sb.toString();
   }
}
