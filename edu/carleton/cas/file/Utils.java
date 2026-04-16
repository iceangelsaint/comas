package edu.carleton.cas.file;

import com.cogerent.utility.PropertiesEditor;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.security.CryptoException;
import edu.carleton.cas.security.CryptoUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class Utils {
   public static ArrayList getResources(String resource, String jarFilePath) {
      ArrayList<String> resources = new ArrayList();
      if (jarFilePath != null) {
         try {
            Throwable var3 = null;
            Object var4 = null;

            try {
               FileSystem fs = FileSystems.newFileSystem(Paths.get(jarFilePath), (ClassLoader)null);

               try {
                  Path jarRoot = fs.getPath(resource);
                  Throwable var7 = null;
                  Object var8 = null;

                  try {
                     Stream<Path> paths = Files.walk(jarRoot);

                     try {
                        paths.forEach((path) -> resources.add(path.toString()));
                     } finally {
                        if (paths != null) {
                           paths.close();
                        }

                     }
                  } catch (Throwable var31) {
                     if (var7 == null) {
                        var7 = var31;
                     } else if (var7 != var31) {
                        var7.addSuppressed(var31);
                     }

                     throw var7;
                  }
               } finally {
                  if (fs != null) {
                     fs.close();
                  }

               }
            } catch (Throwable var33) {
               if (var3 == null) {
                  var3 = var33;
               } else if (var3 != var33) {
                  var3.addSuppressed(var33);
               }

               throw var3;
            }
         } catch (IOException var34) {
         }
      }

      return resources;
   }

   public static File unpackArchive(URL url, File targetDir) throws IOException {
      if (!targetDir.exists()) {
         targetDir.mkdirs();
      }

      String var10000 = String.valueOf(targetDir);
      String zip = var10000 + File.separator + "arc.zip";
      ReadableByteChannel in = Channels.newChannel(url.openStream());
      FileOutputStream os = new FileOutputStream(zip);
      FileChannel out = os.getChannel();
      out.transferFrom(in, 0L, Long.MAX_VALUE);
      os.close();
      out.close();
      File zipFile = new File(zip);
      zipFile.deleteOnExit();
      return unpackArchive(new File(zip), targetDir);
   }

   public static File unpackArchive(InputStream is, File targetDir) throws IOException {
      if (!targetDir.exists()) {
         targetDir.mkdirs();
      }

      String var10000 = String.valueOf(targetDir);
      String zip = var10000 + File.separator + "arc.zip";
      ReadableByteChannel in = Channels.newChannel(is);
      FileOutputStream os = new FileOutputStream(zip);
      FileChannel out = os.getChannel();
      out.transferFrom(in, 0L, Long.MAX_VALUE);
      os.close();
      out.close();
      File zipFile = new File(zip);
      zipFile.deleteOnExit();
      return unpackArchive(new File(zip), targetDir);
   }

   public static File unpackArchive(File theFile, File targetDir) throws IOException {
      if (!theFile.exists()) {
         throw new IOException(theFile.getAbsolutePath() + " does not exist");
      } else if (!buildDirectory(targetDir)) {
         throw new IOException("Could not create directory: " + String.valueOf(targetDir));
      } else {
         ZipFile zipFile = new ZipFile(theFile);
         Enumeration<?> entries = zipFile.entries();

         while(entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry)entries.nextElement();
            if (!entry.getName().startsWith("__MACOSX")) {
               String var10003 = File.separator;
               File file = new File(targetDir, var10003 + entry.getName());
               if (!buildDirectory(file.getParentFile())) {
                  zipFile.close();
                  throw new IOException("Could not create directory: " + String.valueOf(file.getParentFile()));
               }

               if (!entry.isDirectory()) {
                  copyInputStream(zipFile.getInputStream(entry), new BufferedOutputStream(new FileOutputStream(file)));
                  file.setLastModified(entry.getLastModifiedTime().toMillis());
               } else if (!buildDirectory(file)) {
                  zipFile.close();
                  throw new IOException("Could not create directory: " + String.valueOf(file));
               }
            }
         }

         zipFile.close();
         return theFile;
      }
   }

   public static void copyInputStream(InputStream in, OutputStream out) throws IOException {
      copyInputStream(in, out, (String)null);
   }

   public static void copyInputStream(InputStream in, OutputStream out, String append) throws IOException {
      byte[] buffer = new byte[65536];

      try {
         for(int len = in.read(buffer); len >= 0; len = in.read(buffer)) {
            out.write(buffer, 0, len);
         }

         if (append != null) {
            out.write(append.getBytes());
         }
      } finally {
         try {
            in.close();
         } catch (Exception var13) {
         }

         try {
            out.close();
         } catch (Exception var12) {
         }

      }

   }

   public static File getAndStoreFile(InputStream is, String name, File targetDir) throws IOException {
      Logger.output("Saved " + name + " in " + String.valueOf(targetDir));
      InputStream in = new BufferedInputStream(is, 65536);
      File file = new File(targetDir, name);
      OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
      copyInputStream(in, out);
      return file;
   }

   public static File getAndStoreURL(URL url, File targetDir) throws IOException {
      String name = (new File(url.getFile())).getName();
      Logger.output("Saved " + name + " in " + String.valueOf(targetDir));
      InputStream in = new BufferedInputStream(url.openStream(), 65536);
      File file = new File(targetDir, name);
      OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
      copyInputStream(in, out);
      return file;
   }

   public static File getAndStoreURL(URL url, File targetDir, String property, String cookie) throws IOException {
      String name = (new File(url.getFile())).getName();
      URLConnection conn = url.openConnection();
      conn.setRequestProperty(property, cookie);
      BufferedInputStream in = new BufferedInputStream(conn.getInputStream(), 65536);
      File file = new File(targetDir, name);
      OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
      copyInputStream(in, out);
      return file;
   }

   public static String getURL(URL url) {
      StringBuffer buff = new StringBuffer();

      try {
         URLConnection conn = url.openConnection();
         BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

         String inputLine;
         while((inputLine = br.readLine()) != null) {
            buff.append(inputLine);
         }

         br.close();
      } catch (MalformedURLException e) {
         Level var7 = Level.WARNING;
         String var8 = url.toString();
         Logger.debug(var7, "UTILS " + var8 + ":" + e.getMessage());
      } catch (IOException e) {
         Level var10000 = Level.WARNING;
         String var10001 = url.toString();
         Logger.debug(var10000, "UTILS " + var10001 + ":" + e.getMessage());
      }

      return buff.toString();
   }

   public static boolean isPortOpen(String host, int port) {
      return isReachable(host, port, ClientShared.CONNECTION_TIMEOUT_IN_MSECS);
   }

   public static String getURL(String sURL) {
      return getURL(sURL, 0);
   }

   public static String getURL(String sURL, int timeout) {
      StringBuffer buff = new StringBuffer();

      try {
         URL url = new URL(sURL);
         URLConnection conn = url.openConnection();
         if (timeout > 0) {
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
         }

         BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

         String inputLine;
         while((inputLine = br.readLine()) != null) {
            buff.append(inputLine);
         }

         br.close();
      } catch (MalformedURLException e) {
         Logger.debug(Level.WARNING, "UTILS " + sURL + ":" + e.getMessage());
      } catch (IOException e) {
         Logger.debug(Level.WARNING, "UTILS " + sURL + ":" + e.getMessage());
      }

      return buff.toString();
   }

   public static Properties getPropertiesFromFile(String name) {
      FileInputStream fis = null;

      Properties p;
      try {
         fis = new FileInputStream(new File(name));
         p = new Properties();
         p.load(fis);
      } catch (IOException var12) {
         p = null;
      } finally {
         try {
            if (fis != null) {
               fis.close();
            }
         } catch (IOException var11) {
         }

      }

      return p;
   }

   public static Properties getPropertiesFromXMLFile(String name) {
      FileInputStream fis = null;

      Properties p;
      try {
         fis = new FileInputStream(new File(name));
         p = new Properties();
         p.loadFromXML(fis);
      } catch (IOException var12) {
         p = null;
      } finally {
         try {
            if (fis != null) {
               fis.close();
            }
         } catch (IOException var11) {
         }

      }

      return p;
   }

   public static boolean savePropertiesToFile(Properties p, String comments, String name) {
      FileOutputStream fos = null;

      boolean rtn;
      try {
         fos = new FileOutputStream(new File(name));
         p.store(fos, comments);
         rtn = true;
      } catch (IOException var14) {
         rtn = false;
      } finally {
         try {
            if (fos != null) {
               fos.close();
            }
         } catch (IOException var13) {
         }

      }

      return rtn;
   }

   public static boolean savePropertiesToXMLFile(Properties p, String comments, String name) {
      FileOutputStream fos = null;

      boolean rtn;
      try {
         fos = new FileOutputStream(new File(name));
         p.storeToXML(fos, comments);
         rtn = true;
      } catch (IOException var14) {
         rtn = false;
      } finally {
         try {
            if (fos != null) {
               fos.close();
            }
         } catch (IOException var13) {
         }

      }

      return rtn;
   }

   public static Properties getProperties(String sURL, String property, String cookie) {
      Properties properties = new Properties();
      InputStream is = null;

      try {
         URL url = new URL(sURL);
         URLConnection conn = url.openConnection();
         conn.setRequestProperty(property, cookie);
         is = conn.getInputStream();
         properties.load(is);
      } catch (MalformedURLException e) {
         Logger.debug(Level.WARNING, "UTILS " + sURL + ":" + e.getMessage());
      } catch (IOException e) {
         Logger.debug(Level.WARNING, "UTILS " + sURL + ":" + e.getMessage());
      } finally {
         if (is != null) {
            try {
               is.close();
            } catch (IOException var16) {
            }
         }

      }

      return properties;
   }

   public static PropertiesEditor getPropertiesEditor(String sURL, String property, String cookie) {
      PropertiesEditor properties = new PropertiesEditor();
      InputStream is = null;

      try {
         URL url = new URL(sURL);
         URLConnection conn = url.openConnection();
         if (property != null && cookie != null) {
            conn.setRequestProperty(property, cookie);
         }

         is = conn.getInputStream();
         properties.load(is);
      } catch (MalformedURLException e) {
         Logger.debug(Level.WARNING, "UTILS " + sURL + ":" + e.getMessage());
      } catch (IOException e) {
         Logger.debug(Level.WARNING, "UTILS " + sURL + ":" + e.getMessage());
      } finally {
         if (is != null) {
            try {
               is.close();
            } catch (IOException var16) {
            }
         }

      }

      return properties;
   }

   public static Properties getProperties(URL url) {
      Properties properties = new Properties();
      InputStream is = null;

      try {
         URLConnection conn = url.openConnection();
         is = conn.getInputStream();
         properties.load(is);
      } catch (IOException e) {
         Level var10000 = Level.WARNING;
         String var10001 = String.valueOf(url);
         Logger.debug(var10000, "UTILS " + var10001 + ":" + e.getMessage());
      } finally {
         if (is != null) {
            try {
               is.close();
            } catch (IOException var11) {
            }
         }

      }

      return properties;
   }

   public static Properties getProperties(String sURL) {
      try {
         return getProperties(new URL(sURL));
      } catch (MalformedURLException e) {
         Logger.debug(Level.WARNING, "UTILS " + sURL + ":" + e.getMessage());
         return new Properties();
      }
   }

   public static Properties getEncryptedProperties(String sURL, String key) {
      Properties properties = new Properties();

      try {
         URL url = new URL(sURL);
         URLConnection conn = url.openConnection();
         ByteArrayOutputStream bos = new ByteArrayOutputStream(65536);
         CryptoUtils.decrypt(key, conn.getInputStream(), bos);
         properties.load(new ByteArrayInputStream(bos.toByteArray()));
      } catch (MalformedURLException e) {
         Logger.debug(Level.WARNING, "UTILS " + sURL + ":" + e.getMessage());
      } catch (IOException e) {
         Logger.debug(Level.WARNING, "UTILS " + sURL + ":" + e.getMessage());
      } catch (CryptoException e) {
         Logger.debug(Level.WARNING, "UTILS " + sURL + ":" + e.getMessage());
      }

      return properties;
   }

   public static boolean buildDirectory(File file) {
      return file.exists() || file.mkdirs();
   }

   public static boolean isURLOk(String seURL, String type) {
      try {
         HttpURLConnection url = (HttpURLConnection)(new URL(seURL)).openConnection();
         url.setRequestMethod("HEAD");
         url.connect();
         int responseCode = url.getResponseCode();
         if (type == null) {
            return responseCode == 200;
         } else {
            return responseCode == 200 && type.equals(url.getContentType());
         }
      } catch (Exception var4) {
         return false;
      }
   }

   public static int getIntegerOrDefaultInRange(Properties config, String key, int defaultValue, int min, int max) {
      int value = getIntegerOrDefault(config, key, defaultValue);
      if (value >= min && value <= max) {
         return value;
      } else {
         String msg = String.format("%s outside range (%d,%d), using %d", key, min, max, defaultValue);
         Logger.log(Level.CONFIG, msg, "");
         return defaultValue;
      }
   }

   public static int getIntegerOrDefault(Properties config, String key, int defaultValue) {
      String valueAsString = config.getProperty(key);
      if (valueAsString == null) {
         return defaultValue;
      } else {
         int value;
         try {
            value = Integer.parseInt(valueAsString.trim());
         } catch (NumberFormatException var7) {
            value = defaultValue;
            String msg = String.format("%s is not an integer (%s), using %d", key, valueAsString, defaultValue);
            Logger.log(Level.CONFIG, msg, "");
         }

         return value;
      }
   }

   public static float getFloatOrDefaultInRange(Properties config, String key, float defaultValue, float min, float max) {
      float value = getFloatOrDefault(config, key, defaultValue);
      if (!(value < min) && !(value > max)) {
         return value;
      } else {
         String msg = String.format("%s outside range (%.02f,%.02f), using %.02f", key, min, max, defaultValue);
         Logger.log(Level.CONFIG, msg, "");
         return defaultValue;
      }
   }

   public static float getFloatOrDefault(Properties config, String key, float defaultValue) {
      String valueAsString = config.getProperty(key);
      if (valueAsString == null) {
         return defaultValue;
      } else {
         float value;
         try {
            value = Float.parseFloat(valueAsString);
         } catch (NumberFormatException var7) {
            value = defaultValue;
            String msg = String.format("%s is not a real number (%s), using %.02f", key, valueAsString, defaultValue);
            Logger.log(Level.CONFIG, msg, "");
         }

         return value;
      }
   }

   public static String getStringOrDefault(Properties config, String key, String defaultValue) {
      String valueAsString = config.getProperty(key);
      return valueAsString == null ? defaultValue : valueAsString.trim();
   }

   public static boolean getBooleanOrDefault(Properties config, String key, boolean defaultValue) {
      String valueAsString = config.getProperty(key);
      if (valueAsString == null) {
         return defaultValue;
      } else {
         valueAsString = valueAsString.trim().toLowerCase();
         if (valueAsString.equals("true")) {
            return true;
         } else if (valueAsString.equals("false")) {
            return false;
         } else {
            String msg = String.format("%s not a boolean (%s), using %b", key, valueAsString, defaultValue);
            Logger.log(Level.CONFIG, msg, "");
            return defaultValue;
         }
      }
   }

   public static String getStringOrDefaultInSet(Properties config, String key, String defaultValue, String[] allowedValues) {
      String value = getStringOrDefault(config, key, defaultValue);

      for(String allowedValue : allowedValues) {
         if (allowedValue.equalsIgnoreCase(value)) {
            return allowedValue;
         }
      }

      String msg = String.format("%s not in allowed set of values %s, using %s", key, Arrays.toString(allowedValues), defaultValue);
      Logger.log(Level.CONFIG, msg, "");
      return defaultValue;
   }

   public static String[] getStringsOrDefaultInSet(Properties config, String key, String[] defaultValue, String[] allowedValues) {
      HashSet<String> values = new HashSet();
      String value = getStringOrDefault(config, key, (String)null);
      if (value == null) {
         return defaultValue;
      } else {
         String[] tokens = value.split(",");

         for(String token : tokens) {
            token = token.trim();

            for(String allowedValue : allowedValues) {
               if (allowedValue.equalsIgnoreCase(token)) {
                  values.add(token);
                  break;
               }
            }
         }

         if (!values.isEmpty()) {
            return (String[])values.toArray(new String[values.size()]);
         } else {
            if (defaultValue != null) {
               String msg = String.format("%s not in allowed set of values %s, using [%s]", key, Arrays.toString(allowedValues), String.join(",", defaultValue));
               Logger.log(Level.CONFIG, msg, "");
            }

            return defaultValue;
         }
      }
   }

   public static String removeExtensionFull(File f) {
      return removeExtension(f == null ? null : f.getAbsolutePath());
   }

   public static String removeExtension(File f) {
      return removeExtension(f == null ? null : f.getName());
   }

   public static String removeExtension(String s) {
      return s != null && s.lastIndexOf(".") > 0 ? s.substring(0, s.lastIndexOf(".")) : s;
   }

   public static String convertMsecsToHoursMinutesSeconds(long millis, boolean shortString, boolean round) {
      long total_seconds = round ? Math.round((double)millis / (double)1000.0F) : millis / 1000L;
      long hours = total_seconds / 3600L;
      long minutes = (total_seconds - hours * 3600L) / 60L;
      long seconds = total_seconds - hours * 3600L - minutes * 60L;
      String hourQualifier = hours == 1L ? "hour" : "hours";
      String minuteQualifier = minutes == 1L ? "min" : "mins";
      String secondsQualifier = seconds == 1L ? "sec" : "secs";
      if (hours == 0L) {
         return String.format("%d %s %d %s", minutes, minuteQualifier, seconds, secondsQualifier);
      } else {
         return shortString ? String.format("%d:%d:%d", hours, minutes, seconds) : String.format("%d %s %d %s %d %s", hours, hourQualifier, minutes, minuteQualifier, seconds, secondsQualifier);
      }
   }

   public static boolean isTrueOrYes(Object value) {
      return value == null ? false : isTrueOrYes(value.toString(), false);
   }

   public static boolean isTrueOrYes(String value, boolean defaultValue) {
      if (value != null && value.length() <= 5) {
         if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("yes")) {
            return !value.equalsIgnoreCase("false") && !value.equalsIgnoreCase("no") ? defaultValue : false;
         } else {
            return true;
         }
      } else {
         return defaultValue;
      }
   }

   public static String convertMsecsToHoursMinutesSeconds(long millis) {
      return convertMsecsToHoursMinutesSeconds(millis, false, true);
   }

   public static String localHTMLPage(String msg) {
      return String.format("<html><head><title>CoMaS</title>%s%s</head><body><div class=\"w3-container w3-center\"><br/><img alt=\"CoMaS\" src=\"%s\"><h1>%s</h1></div></body></html>", SystemWebResources.getStylesheet(), SystemWebResources.getIcon(), SystemWebResources.getAppImage(), msg);
   }

   public static String printException(Throwable e) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      Throwable var2 = null;
      Object var3 = null;

      try {
         PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);

         try {
            e.printStackTrace(ps);
            ps.close();
         } finally {
            if (ps != null) {
               ps.close();
            }

         }
      } catch (Throwable var10) {
         if (var2 == null) {
            var2 = var10;
         } else if (var2 != var10) {
            var2.addSuppressed(var10);
         }

         throw var2;
      }

      return baos.toString(StandardCharsets.UTF_8);
   }

   public static String printAllStackFrames(Throwable e) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      StackTraceElement[] ste = e.getStackTrace();
      Throwable var3 = null;
      Object var4 = null;

      try {
         PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);

         try {
            for(StackTraceElement se : ste) {
               ps.println(se);
            }

            ps.close();
         } finally {
            if (ps != null) {
               ps.close();
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

      return baos.toString(StandardCharsets.UTF_8);
   }

   public static String printFirstApplicationStackFrameOrException(Throwable e) {
      StackTraceElement[] ste = e.getStackTrace();
      if (ste != null && ste.length > 0) {
         if (ste.length == 1) {
            String var5 = e.toString();
            return var5 + "\n" + ste[0].toString();
         } else {
            String var4 = e.toString();
            String msg = var4 + "\nFirst: " + String.valueOf(ste[0]) + "\nLast: " + String.valueOf(ste[ste.length - 1]);

            for(int i = 0; i < ste.length; ++i) {
               if (ste[i].getClassName().startsWith("edu.carleton") || ste[i].getClassName().startsWith("com.cogerent")) {
                  return "\nCoMaS: " + msg + "\n" + String.valueOf(ste[i]);
               }
            }

            return msg;
         }
      } else {
         String var10000 = e.toString();
         return var10000 + "\n" + printException(e);
      }
   }

   public static String shuffle(String str) {
      char[] result = str.toCharArray();

      for(int n = result.length; n > 0; --n) {
         int v = ThreadLocalRandom.current().nextInt(n);
         char temp = result[v];
         result[v] = result[n - 1];
         result[n - 1] = temp;
      }

      return new String(result);
   }

   public static boolean containsEmoji(String str) {
      int length = str.length();

      for(int i = 0; i < length; ++i) {
         int type = Character.getType(str.charAt(i));
         if (type == 19 || type == 28) {
            return true;
         }
      }

      return false;
   }

   public static String replaceEmoji(String str, char replacementChar) {
      int length = str.length();
      StringBuilder sb = new StringBuilder(length);

      for(int i = 0; i < length; ++i) {
         int type = Character.getType(str.charAt(i));
         if (type != 19 && type != 28) {
            sb.append(str.charAt(i));
         } else {
            sb.append(replacementChar);
         }
      }

      return sb.toString();
   }

   public static boolean isFlagEmoji(char ch) {
      int flagEmojiStart = 127462;
      int flagEmojiEnd = 127487;
      int codePoint = Character.codePointAt(new char[]{ch}, 0);
      return codePoint >= flagEmojiStart && codePoint <= flagEmojiEnd;
   }

   public static String replaceFlagEmoji(String str, char replacementChar) {
      int length = str.length();
      StringBuilder sb = new StringBuilder(length);

      for(int i = 0; i < length; ++i) {
         if (isFlagEmoji(str.charAt(i))) {
            sb.append(replacementChar);
         } else {
            sb.append(str.charAt(i));
         }
      }

      return sb.toString();
   }

   public static boolean isReachable(String hostname, int port, int timeout) {
      try {
         Throwable var3 = null;
         Object var4 = null;

         try {
            Socket socket = new Socket();

            try {
               socket.connect(new InetSocketAddress(hostname, port), timeout);
            } finally {
               if (socket != null) {
                  socket.close();
               }

            }

            return true;
         } catch (Throwable var13) {
            if (var3 == null) {
               var3 = var13;
            } else if (var3 != var13) {
               var3.addSuppressed(var13);
            }

            throw var3;
         }
      } catch (Exception var14) {
         return false;
      }
   }

   public static boolean isReachable(String host) {
      String[] tokens = host.split(":");

      try {
         return tokens.length == 2 ? isReachable(tokens[0].trim(), Integer.parseInt(tokens[1].trim()), ClientShared.CONNECTION_TIMEOUT_IN_MSECS) : isReachable(host.trim(), Integer.parseInt(ClientShared.PORT), ClientShared.CONNECTION_TIMEOUT_IN_MSECS);
      } catch (Exception var3) {
         return false;
      }
   }

   public static String detectLinuxDistro() {
      if (!ClientShared.isLinuxOS()) {
         return "Not a Linux OS";
      } else {
         Path osRelease = Paths.get("/etc/os-release");
         if (Files.exists(osRelease, new LinkOption[0])) {
            try {
               List<String> lines = Files.readAllLines(osRelease);
               Map<String, String> props = new HashMap();

               for(String line : lines) {
                  if (line.contains("=")) {
                     String[] parts = line.split("=", 2);
                     String key = parts[0].trim();
                     String value = parts[1].replace("\"", "").trim();
                     props.put(key, value);
                  }
               }

               return (String)props.getOrDefault("PRETTY_NAME", (String)props.getOrDefault("NAME", "Unknown Linux"));
            } catch (IOException var8) {
               return "Unknown Linux (error reading /etc/os-release)";
            }
         } else {
            return "Unknown Linux (no /etc/os-release)";
         }
      }
   }

   public static long destroyPIDForcibly(long pid) {
      Optional<ProcessHandle> oldProc = ProcessHandle.of(pid);
      if (oldProc.isPresent() && ((ProcessHandle)oldProc.get()).isAlive()) {
         ProcessHandle.Info info = ((ProcessHandle)oldProc.get()).info();
         Optional<String> command = info.command();
         if (command.isPresent()) {
            boolean terminated = ((ProcessHandle)oldProc.get()).destroyForcibly();
            if (terminated) {
               return pid;
            }
         }
      }

      return -1L;
   }

   public static long destroyPID(long pid) {
      Optional<ProcessHandle> oldProc = ProcessHandle.of(pid);
      if (oldProc.isPresent() && ((ProcessHandle)oldProc.get()).isAlive()) {
         ProcessHandle.Info info = ((ProcessHandle)oldProc.get()).info();
         Optional<String> command = info.command();
         if (command.isPresent()) {
            boolean terminated = ((ProcessHandle)oldProc.get()).destroy();
            if (terminated) {
               return pid;
            }
         }
      }

      return -1L;
   }
}
