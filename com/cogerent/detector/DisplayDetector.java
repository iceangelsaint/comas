package com.cogerent.detector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DisplayDetector {
   public static void main(String[] args) throws Exception {
      List<DisplayInfo> displays = getDisplays();
      if (displays != null) {
         for(DisplayInfo d : displays) {
            String var10001 = d.manufacturer;
            System.out.println("\nManufacturer: " + var10001);
            var10001 = d.model;
            System.out.println("Model:        " + var10001);
            var10001 = d.productCode;
            System.out.println("Product Code: " + var10001);
            var10001 = d.serial;
            System.out.println("Serial:       " + var10001);
            var10001 = d.connectionType;
            System.out.println("Connection:   " + var10001);
            System.out.println("Status:       " + (isSuspicious(d) ? "Likely VIRTUAL" : "Physical"));
         }

      }
   }

   public static List getDisplays() throws IOException {
      String os = System.getProperty("os.name").toLowerCase();
      List<DisplayInfo> displays = null;
      if (os.contains("win")) {
         displays = getWindowsDisplays();
      } else if (os.contains("mac")) {
         displays = getMacDisplays();
      }

      return displays;
   }

   public static boolean displayCheck() throws IOException {
      List<DisplayInfo> displays = getDisplays();
      if (displays == null) {
         return true;
      } else {
         for(DisplayInfo d : displays) {
            if (isSuspicious(d)) {
               return false;
            }
         }

         return true;
      }
   }

   private static List getWindowsDisplays() throws IOException {
      List<DisplayInfo> displays = new ArrayList();
      Process p = (new ProcessBuilder(new String[]{"powershell", "-Command", "Get-CimInstance -Namespace root\\wmi -ClassName WmiMonitorID | ForEach-Object { $_ }"})).start();
      String output = readProcess(p);
      Pattern manufacturerPat = Pattern.compile("ManufacturerName\\s*:\\s*(.+)");
      Pattern modelPat = Pattern.compile("UserFriendlyName\\s*:\\s*(.+)");
      Pattern prodPat = Pattern.compile("ProductCodeID\\s*:\\s*(.+)");
      Pattern serialPat = Pattern.compile("SerialNumberID\\s*:\\s*(.+)");
      String[] blocks = output.split("\\r?\\n\\r?\\n");

      for(String block : blocks) {
         String manufacturer = extractValue(block, manufacturerPat);
         String model = extractValue(block, modelPat);
         String prod = extractValue(block, prodPat);
         String serial = extractValue(block, serialPat);
         DisplayInfo info = new DisplayInfo();
         info.manufacturer = clean(manufacturer);
         info.model = clean(model);
         info.productCode = clean(prod);
         info.serial = clean(serial);
         info.connectionType = detectConnectionTypeWindows(info);
         displays.add(info);
      }

      return displays;
   }

   private static List getMacDisplays() throws IOException {
      List<DisplayInfo> displays = new ArrayList();
      Process p = (new ProcessBuilder(new String[]{"bash", "-c", "ioreg -l | grep IODisplayEDID"})).start();
      String output = readProcess(p);
      Pattern hexPat = Pattern.compile("<([0-9a-fA-F]+)>");
      Matcher m = hexPat.matcher(output);

      while(m.find()) {
         byte[] edid = hexStringToByteArray(m.group(1));
         DisplayInfo info = parseEDID(edid);
         info.connectionType = detectConnectionTypeMac(info);
         displays.add(info);
      }

      return displays;
   }

   private static String extractValue(String block, Pattern pattern) {
      Matcher m = pattern.matcher(block);
      return m.find() ? m.group(1).trim() : "";
   }

   private static String readProcess(Process p) throws IOException {
      Throwable var1 = null;
      Object var2 = null;

      try {
         BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

         String var10000;
         try {
            StringBuilder sb = new StringBuilder();

            String line;
            while((line = r.readLine()) != null) {
               sb.append(line).append("\n");
            }

            var10000 = sb.toString();
         } finally {
            if (r != null) {
               r.close();
            }

         }

         return var10000;
      } catch (Throwable var11) {
         if (var1 == null) {
            var1 = var11;
         } else if (var1 != var11) {
            var1.addSuppressed(var11);
         }

         throw var1;
      }
   }

   private static String clean(String s) {
      return s == null ? "" : s.replaceAll("[^\\p{Print}]", "").trim();
   }

   private static byte[] hexStringToByteArray(String s) {
      int len = s.length();
      byte[] data = new byte[len / 2];

      for(int i = 0; i < len; i += 2) {
         data[i / 2] = (byte)((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
      }

      return data;
   }

   public static DisplayInfo parseEDID(byte[] edid) {
      DisplayInfo info = new DisplayInfo();
      if (edid.length < 128) {
         return info;
      } else {
         info.edid = edid;
         int raw = (edid[8] & 255) << 8 | edid[9] & 255;
         info.manufacturer = "" + (char)((raw >> 10 & 31) + 64) + (char)((raw >> 5 & 31) + 64) + (char)((raw & 31) + 64);
         info.productCode = String.format("%02X%02X", edid[11], edid[10]);
         info.serial = String.format("%02X%02X%02X%02X", edid[15], edid[14], edid[13], edid[12]);
         info.model = parseMonitorNameFromEDID(edid);
         return info;
      }
   }

   public static String parseMonitorNameFromEDID(byte[] edid) {
      for(int i = 54; i <= 125; i += 18) {
         if (edid[i] == 0 && edid[i + 1] == 0 && edid[i + 2] == 0 && edid[i + 3] == -4) {
            StringBuilder sb = new StringBuilder();

            for(int j = i + 5; j < i + 18 && edid[j] != 10; ++j) {
               sb.append((char)edid[j]);
            }

            return sb.toString().trim();
         }
      }

      return "";
   }

   public static boolean isSuspicious(DisplayInfo d) {
      String mfg = d.manufacturer != null ? d.manufacturer.toUpperCase() : "";
      String model = d.model != null ? d.model.toLowerCase() : "";
      String conn = d.connectionType != null ? d.connectionType.toLowerCase() : "";
      String productCode = d.productCode != null ? d.productCode.toLowerCase() : "";
      if (mfg.matches("MSF[T]?|VSC|DLK|FAK|MST|MSI")) {
         return true;
      } else if (!model.contains("virtual") && !model.contains("displaylink") && !model.contains("dummy") && !model.contains("rdp") && !model.contains("spacedesk") && !model.contains("sidecar") && !model.contains("airplay")) {
         if (!conn.contains("usb") && !conn.contains("virtual") && !conn.contains("wireless") && !conn.contains("airplay")) {
            return mfg.isEmpty() || model.isEmpty() && productCode.isEmpty();
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   public static String detectConnectionTypeWindows(DisplayInfo info) {
      return "unknown";
   }

   public static String detectConnectionTypeMac(DisplayInfo info) {
      return "unknown";
   }
}
