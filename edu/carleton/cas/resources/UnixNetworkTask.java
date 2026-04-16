package edu.carleton.cas.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class UnixNetworkTask extends AbstractNetworkTask {
   public UnixNetworkTask(Logger logger, ResourceMonitor monitor) {
      super(logger, monitor);
      this.cmd = new String[]{"lsof", "-i"};
   }

   public boolean isIllegal(String line) {
      if (this.isProcessTrusted(line)) {
         return false;
      } else if (line.contains("(ESTABLISHED)")) {
         int index = line.indexOf("->");
         int startIPv6 = line.indexOf("[", index);
         int endIPv6;
         if (startIPv6 > 0) {
            endIPv6 = line.indexOf("]", startIPv6);
         } else {
            endIPv6 = -1;
         }

         if (startIPv6 > 0 && endIPv6 > 0) {
            this.remoteHost = line.substring(startIPv6 + 1, endIPv6 - 1);
         } else {
            int end = line.indexOf(":", index);
            this.remoteHost = line.substring(index + 2, end);
         }

         try {
            InetAddress address = InetAddress.getByName(this.remoteHost);
            if (address.isLoopbackAddress()) {
               return false;
            }
         } catch (UnknownHostException var6) {
         }

         return !this.isAllowed(this.remoteHost);
      } else {
         return false;
      }
   }
}
