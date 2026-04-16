package edu.carleton.cas.resources;

import oshi.hardware.NetworkIF;

public interface NetworkIFManagerInterface {
   void setDnsServers(NetworkIF var1, String var2);

   void resetDnsServers(NetworkIF var1, String[] var2, boolean var3);

   void initializeDnsCache();

   void changeNetworkInterfaceState(NetworkIF var1, boolean var2, String var3);

   void close();

   boolean isDHCP(NetworkIF var1);

   void setIpV6State(NetworkIF var1, String var2);

   String getIpV6State(NetworkIF var1);

   boolean isVPN(NetworkIF var1);

   void setupWithVPN(NetworkIF var1, String var2);

   void setupInterfaceAsDHCP(NetworkIF var1);

   String getWiFiSSID(NetworkIF var1);

   String getSSID();
}
