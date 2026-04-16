package edu.carleton.cas.resources;

import edu.carleton.cas.background.SessionConfigurationModeMonitor;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.constants.ClientShared.OS;

public class NetworkIFManagerFactory {
   // $FF: synthetic field
   private static volatile int[] $SWITCH_TABLE$edu$carleton$cas$constants$ClientShared$OS;

   public static NetworkIFManagerInterface create(SessionConfigurationModeMonitor scnm) {
      ClientShared.OS os = ClientShared.getOS();
      switch (os) {
         case windows:
            return new NetworkIFManagerForWindows(scnm);
         case macOS:
            return new NetworkIFManagerForMacOS(scnm);
         case linux:
            return new NetworkIFManagerForLinux(scnm);
         default:
            return null;
      }
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$edu$carleton$cas$constants$ClientShared$OS() {
      int[] var10000 = $SWITCH_TABLE$edu$carleton$cas$constants$ClientShared$OS;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[OS.values().length];

         try {
            var0[OS.linux.ordinal()] = 4;
         } catch (NoSuchFieldError var4) {
         }

         try {
            var0[OS.macOS.ordinal()] = 3;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[OS.unknown.ordinal()] = 1;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[OS.windows.ordinal()] = 2;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$edu$carleton$cas$constants$ClientShared$OS = var0;
         return var0;
      }
   }
}
