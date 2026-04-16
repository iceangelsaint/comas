package com.cogerent.detector;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DisplayDetectorPro {
   public static void main(String[] args) throws Exception {
      String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
      if (os.contains("win")) {
         List<DisplayInfo> displays = getWindowsDisplays();
         printWindows(displays);
      } else if (os.contains("mac")) {
         List<DisplayInfo> displays = getMacDisplays();
         printMac(displays);
      } else {
         System.err.println("Unsupported OS: " + os);
      }

   }

   public static List getDisplays() throws Exception {
      String os = System.getProperty("os.name").toLowerCase();
      List<DisplayInfo> displays = null;
      if (os.contains("win")) {
         displays = getWindowsDisplays();
      } else if (os.contains("mac")) {
         displays = getMacDisplays();
      }

      return displays;
   }

   public static boolean displayCheck() throws Exception {
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

   private static List getWindowsDisplays() throws Exception {
      List<DisplayInfo> displays = new ArrayList();
      System.out.println("=== Windows Display Enumeration ===");
      String psMonitors = "Get-CimInstance -Namespace root\\wmi -ClassName WmiMonitorID | Select-Object InstanceName, ManufacturerName, UserFriendlyName, ProductCodeID, SerialNumberID | ConvertTo-Csv -NoTypeInformation";
      String csv = run(new String[]{"powershell", "-NoProfile", "-Command", psMonitors});
      List<Map<String, String>> rows = parseCsv(csv);
      String psConn = "Get-CimInstance -Namespace root\\wmi -ClassName WmiMonitorConnectionParams | Select-Object InstanceName, VideoOutputTechnology | ConvertTo-Csv -NoTypeInformation";
      List<Map<String, String>> connRows = parseCsv(run(new String[]{"powershell", "-NoProfile", "-Command", psConn}));
      Map<String, String> techByInstance = new HashMap();

      for(Map r : connRows) {
         String inst = safe((String)r.get("InstanceName"));
         String tech = safe((String)r.get("VideoOutputTechnology"));
         if (!inst.isEmpty()) {
            techByInstance.put(instKey(inst), tech);
         }
      }

      for(Map r : rows) {
         String instance = safe((String)r.get("InstanceName"));
         String manuf = decodePowershellByteArray((String)r.get("ManufacturerName"));
         String model = decodePowershellByteArray((String)r.get("UserFriendlyName"));
         String prod = hexFromNumbers((String)r.get("ProductCodeID"));
         String serial = decodePowershellByteArray((String)r.get("SerialNumberID"));
         String connTech = (String)techByInstance.getOrDefault(instKey(instance), "");
         String connLabel = decodeWinVideoOutputTechnology(connTech);
         DisplayInfo d = new DisplayInfo();
         d.instance = instance;
         d.manufacturer = manuf;
         d.model = model;
         d.productCode = prod;
         d.serial = serial;
         d.connectionType = connLabel;
         displays.add(d);
      }

      return displays;
   }

   private static void printWindows(List displays) {
      if (displays != null) {
         int idx = 0;

         for(DisplayInfo d : displays) {
            ++idx;
            System.out.println("\nDisplay #" + idx);
            System.out.println("  Instance:     " + d.instance);
            System.out.println("  Manufacturer: " + d.manufacturer);
            System.out.println("  Model:        " + d.model);
            System.out.println("  Product Code: " + d.productCode);
            System.out.println("  Serial:       " + d.serial);
            PrintStream var10000 = System.out;
            String var10001 = d.connectionType.isEmpty() ? "Unknown" : d.connectionType;
            var10000.println("  Connection:   " + var10001);
            System.out.println("  EDID Checksum: Not checked (raw EDID not retrieved via WMI)");
            boolean suspicious = isSuspicious(d);
            if (suspicious) {
               System.out.println("  Status:       Likely VIRTUAL");
               System.out.println("  Reasons:      " + String.join("; ", reasonsFor(d)));
            } else {
               System.out.println("  Status:       Physical");
            }
         }

         System.out.println("\nGPU Connector Cross-Check (Windows):");
         System.out.println("  Skipped: No reliable connector-count via shell/WMI. Use a native DXGI helper if needed.");
      }
   }

   private static String instKey(String instance) {
      return instance == null ? "" : instance.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
   }

   private static String decodePowershellByteArray(String field) {
      if (field == null) {
         return "";
      } else {
         String[] parts = field.trim().split("\\s+");
         ByteArrayOutputStream baos = new ByteArrayOutputStream();

         for(String p : parts) {
            try {
               int v = Integer.parseInt(p);
               if (v == 0) {
                  break;
               }

               baos.write(v & 255);
            } catch (NumberFormatException var8) {
            }
         }

         return (new String(baos.toByteArray(), StandardCharsets.US_ASCII)).trim();
      }
   }

   private static String hexFromNumbers(String field) {
      if (field == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder();

         String[] var5;
         for(String p : var5 = field.trim().split("\\s+")) {
            try {
               int v = Integer.parseInt(p);
               sb.append(String.format("%02X", v & 255));
            } catch (NumberFormatException var7) {
            }
         }

         return sb.toString();
      }
   }

   private static String decodeWinVideoOutputTechnology(String codeStr) {
      try {
         int code = Integer.parseInt(codeStr.trim());
         switch (code) {
            case -1:
               return "Other";
            case 0:
               return "Unknown";
            case 1:
               return "HD15 (VGA)";
            case 2:
               return "S-Video";
            case 3:
               return "Composite";
            case 4:
               return "Component";
            case 5:
               return "DVI";
            case 6:
               return "HDMI";
            case 7:
            case 9:
            case 13:
            default:
               return "TechCode " + code;
            case 8:
               return "LVDS/Internal";
            case 10:
               return "DisplayPort";
            case 11:
               return "Wireless Display";
            case 12:
               return "Embedded DisplayPort";
            case 14:
               return "USB/Network (Indirect)";
         }
      } catch (Exception var2) {
         return "";
      }
   }

   private static List getMacDisplays() throws Exception {
      List<DisplayInfo> displays = new ArrayList();
      System.out.println("=== macOS Display Enumeration ===");
      String ioreg = run(new String[]{"bash", "-c", "ioreg -lw0 | grep IODisplayEDID"});
      Matcher m = Pattern.compile("<([0-9a-fA-F]+)>").matcher(ioreg);
      List<byte[]> edids = new ArrayList();

      while(m.find()) {
         edids.add(hexToBytes(m.group(1)));
      }

      String sp = run(new String[]{"bash", "-c", "system_profiler SPDisplaysDataType"});
      List<String> connTypes = extractByLabel(sp, "Connection Type");
      int idx = 0;

      for(byte[] edid : edids) {
         ++idx;
         DisplayInfo d = parseEDID(edid);
         String connection = idx <= connTypes.size() ? (String)connTypes.get(idx - 1) : "Unknown";
         d.connectionType = connection;
         displays.add(d);
      }

      return displays;
   }

   private static void printMac(List displays) throws Exception {
      if (displays != null) {
         int idx = 0;

         for(DisplayInfo d : displays) {
            ++idx;
            System.out.println("\nDisplay #" + idx);
            System.out.println("  Manufacturer: " + d.manufacturer);
            PrintStream var10000 = System.out;
            String var10001 = d.model.isEmpty() ? "Unknown" : d.model;
            var10000.println("  Model:        " + var10001);
            var10001 = d.productCode;
            System.out.println("  Product Code: " + var10001);
            var10001 = d.serial;
            System.out.println("  Serial:       " + var10001);
            var10001 = d.connectionType;
            System.out.println("  Connection:   " + var10001);
            System.out.println("  EDID Checksum: " + (validEdidChecksum(d.edid) ? "OK" : "INVALID"));
            boolean suspicious = isSuspicious(d) || !validEdidChecksum(d.edid);
            if (suspicious) {
               Set<String> reasons = reasonsFor(d);
               if (!validEdidChecksum(d.edid)) {
                  reasons.add("EDID checksum invalid");
               }

               System.out.println("  Status:       Likely VIRTUAL");
               System.out.println("  Reasons:      " + String.join("; ", reasons));
            } else {
               System.out.println("  Status:       Physical");
            }
         }

         String fbCountStr = run(new String[]{"bash", "-c", "ioreg -l | grep IOFramebuffer | wc -l"}).trim();
         int fbCount = parseIntSafe(fbCountStr, 0);
         System.out.println("\nGPU Output Cross-Check (macOS):");
         System.out.println("  Physical GPU outputs (IOFramebuffer): " + fbCount);
         System.out.println("  Connected displays detected:          " + displays.size());
         if (fbCount > 0 && displays.size() > fbCount) {
            System.out.println("  WARNING: More displays than GPU connectors → likely virtual/indirect displays present.");
         } else {
            System.out.println("  No obvious extra displays beyond GPU connector count.");
         }

      }
   }

   public static DisplayInfo parseEDID(byte[] edid) {
      DisplayInfo d = new DisplayInfo();
      if (edid != null && edid.length >= 128) {
         d.edid = edid;
         int raw = (edid[8] & 255) << 8 | edid[9] & 255;
         d.manufacturer = "" + (char)((raw >> 10 & 31) + 64) + (char)((raw >> 5 & 31) + 64) + (char)((raw & 31) + 64);
         d.productCode = String.format("%02X%02X", edid[11], edid[10]);
         d.serial = String.format("%02X%02X%02X%02X", edid[15], edid[14], edid[13], edid[12]);
         d.model = parseEdidModel(edid);
         return d;
      } else {
         return d;
      }
   }

   public static String parseEdidModel(byte[] edid) {
      for(int i = 54; i <= 125; i += 18) {
         if (i + 18 <= edid.length && edid[i] == 0 && edid[i + 1] == 0 && edid[i + 2] == 0 && (edid[i + 3] & 255) == 252) {
            StringBuilder sb = new StringBuilder();

            for(int j = i + 5; j < i + 18; ++j) {
               byte b = edid[j];
               if (b == 10 || b == 0) {
                  break;
               }

               sb.append((char)(b & 255));
            }

            return sb.toString().trim();
         }
      }

      return "";
   }

   public static boolean validEdidChecksum(byte[] edid) {
      if (edid != null && edid.length >= 128) {
         int sum = 0;

         for(int i = 0; i < 128; ++i) {
            sum = sum + (edid[i] & 255) & 255;
         }

         return sum == 0;
      } else {
         return false;
      }
   }

   public static boolean isSuspicious(DisplayInfo d) {
      String mfg = safe(d.manufacturer).toUpperCase(Locale.ROOT);
      String model = safe(d.model).toLowerCase(Locale.ROOT);
      String conn = safe(d.connectionType).toLowerCase(Locale.ROOT);
      String productCode = safe(d.productCode).toLowerCase(Locale.ROOT);
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

   public static Set reasonsFor(DisplayInfo d) {
      Set<String> reasons = new LinkedHashSet();
      String mfg = safe(d.manufacturer).toUpperCase(Locale.ROOT);
      String model = safe(d.model).toLowerCase(Locale.ROOT);
      String conn = safe(d.connectionType).toLowerCase(Locale.ROOT);
      String productCode = safe(d.productCode).toLowerCase(Locale.ROOT);
      if (mfg.matches("MSF[T]?|VSC|DLK|FAK|MST|MSI")) {
         reasons.add("Manufacturer ID suspicious (" + mfg + ")");
      }

      if (model.contains("virtual")) {
         reasons.add("Model mentions 'virtual'");
      }

      if (model.contains("displaylink")) {
         reasons.add("DisplayLink model");
      }

      if (model.contains("dummy")) {
         reasons.add("Dummy/ghost model");
      }

      if (model.contains("rdp")) {
         reasons.add("RDP/remote hint");
      }

      if (model.contains("spacedesk")) {
         reasons.add("Spacedesk hint");
      }

      if (model.contains("sidecar") || model.contains("airplay")) {
         reasons.add("Sidecar/AirPlay hint");
      }

      if (conn.contains("usb")) {
         reasons.add("USB connection");
      }

      if (conn.contains("wireless") || conn.contains("airplay")) {
         reasons.add("Wireless/AirPlay connection");
      }

      if (conn.contains("virtual")) {
         reasons.add("Virtual connection");
      }

      if (mfg.isEmpty() || model.isEmpty() && productCode.isEmpty()) {
         reasons.add("Missing EDID fields");
      }

      return reasons;
   }

   private static String run(String[] cmd) throws IOException, InterruptedException {
      Process p = (new ProcessBuilder(cmd)).redirectErrorStream(true).start();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      Throwable var3 = null;
      Object var4 = null;

      try {
         InputStream in = p.getInputStream();

         try {
            byte[] buf = new byte[8192];

            int r;
            while((r = in.read(buf)) != -1) {
               baos.write(buf, 0, r);
            }
         } finally {
            if (in != null) {
               in.close();
            }

         }
      } catch (Throwable var13) {
         if (var3 == null) {
            var3 = var13;
         } else if (var3 != var13) {
            var3.addSuppressed(var13);
         }

         throw var3;
      }

      p.waitFor();
      return baos.toString(StandardCharsets.UTF_8);
   }

   private static List parseCsv(String csv) {
      List<Map<String, String>> out = new ArrayList();
      List<String> lines = new ArrayList();

      try {
         Throwable var3 = null;
         Object var4 = null;

         try {
            BufferedReader br = new BufferedReader(new StringReader(csv));

            String line;
            try {
               while((line = br.readLine()) != null) {
                  if (!line.trim().isEmpty()) {
                     lines.add(line);
                  }
               }
            } finally {
               if (br != null) {
                  br.close();
               }

            }
         } catch (Throwable var15) {
            if (var3 == null) {
               var3 = var15;
            } else if (var3 != var15) {
               var3.addSuppressed(var15);
            }

            throw var3;
         }
      } catch (IOException var16) {
      }

      if (lines.isEmpty()) {
         return out;
      } else {
         String[] headers = splitCsvLine((String)lines.get(0));

         for(int i = 1; i < lines.size(); ++i) {
            String[] vals = splitCsvLine((String)lines.get(i));
            Map<String, String> row = new HashMap();

            for(int c = 0; c < headers.length && c < vals.length; ++c) {
               row.put(headers[c], stripCsvQuotes(vals[c]));
            }

            out.add(row);
         }

         return out;
      }
   }

   private static String[] splitCsvLine(String line) {
      List<String> parts = new ArrayList();
      StringBuilder cur = new StringBuilder();
      boolean inQ = false;

      for(int i = 0; i < line.length(); ++i) {
         char ch = line.charAt(i);
         if (ch == '"') {
            if (inQ && i + 1 < line.length() && line.charAt(i + 1) == '"') {
               cur.append('"');
               ++i;
            } else {
               inQ = !inQ;
            }
         } else if (ch == ',' && !inQ) {
            parts.add(cur.toString());
            cur.setLength(0);
         } else {
            cur.append(ch);
         }
      }

      parts.add(cur.toString());
      return (String[])parts.toArray(new String[0]);
   }

   private static String stripCsvQuotes(String s) {
      s = s == null ? "" : s.trim();
      if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
         s = s.substring(1, s.length() - 1).replace("\"\"", "\"");
      }

      return s;
   }

   private static byte[] hexToBytes(String hex) {
      int len = hex.length();
      byte[] out = new byte[len / 2];

      for(int i = 0; i < len; i += 2) {
         out[i / 2] = (byte)(Character.digit(hex.charAt(i), 16) << 4 | Character.digit(hex.charAt(i + 1), 16));
      }

      return out;
   }

   private static List extractByLabel(String text, String label) {
      List<String> vals = new ArrayList();
      Pattern p = Pattern.compile("^\\s*" + Pattern.quote(label) + "\\s*:\\s*(.+)$", 8);
      Matcher m = p.matcher(text);

      while(m.find()) {
         vals.add(m.group(1).trim());
      }

      return vals;
   }

   private static int parseIntSafe(String s, int dflt) {
      try {
         return Integer.parseInt(s.trim());
      } catch (Exception var3) {
         return dflt;
      }
   }

   private static String safe(String s) {
      return s == null ? "" : s.trim();
   }
}
