package edu.carleton.cas.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class WindowsNetworkTask extends AbstractNetworkTask {
   int indexOfForeign = -1;
   boolean indexOk = false;

   public WindowsNetworkTask(Logger logger, ResourceMonitor monitor) {
      super(logger, monitor);
      this.cmd = new String[]{"netstat", "-f"};
   }

   public boolean isIllegal(String line) {
      if (!this.indexOk) {
         this.indexOfForeign = line.indexOf("Foreign");
         this.indexOk = this.indexOfForeign > -1;
      }

      if (this.indexOk && line.contains("ESTABLISHED")) {
         int startIPv6 = line.indexOf("[", this.indexOfForeign);
         int endIPv6;
         if (startIPv6 > 0) {
            endIPv6 = line.indexOf("]", startIPv6);
         } else {
            endIPv6 = -1;
         }

         if (startIPv6 > 0 && endIPv6 > 0) {
            this.remoteHost = line.substring(startIPv6 + 1, endIPv6 - 1);
         } else {
            int end = line.indexOf(":", this.indexOfForeign);
            this.remoteHost = line.substring(this.indexOfForeign, end);
         }

         if (this.remoteHost.contains("akamaitechnologies")) {
            return false;
         } else {
            try {
               InetAddress address = InetAddress.getByName(this.remoteHost);
               if (address.isLoopbackAddress()) {
                  return false;
               }
            } catch (UnknownHostException var5) {
            }

            if (line.contains("127.0.0.1")) {
               return false;
            } else {
               return !this.isAllowed(this.remoteHost);
            }
         }
      } else {
         return false;
      }
   }
}
