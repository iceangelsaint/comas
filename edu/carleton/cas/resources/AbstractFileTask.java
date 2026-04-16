package edu.carleton.cas.resources;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.logging.Level;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class AbstractFileTask extends AbstractTask {
   protected static ArrayList FILE_TYPES = new ArrayList();
   protected static ArrayList FILE_PATTERNS;
   protected static ArrayList IGNORE_TYPES;
   protected static ArrayList IGNORE_PATTERNS;
   protected final HashMap openFiles = new HashMap();
   protected String processName = "";

   static {
      FILE_TYPES.add(".pdf");
      FILE_TYPES.add(".txt");
      FILE_TYPES.add(".doc");
      FILE_TYPES.add(".docx");
      FILE_TYPES.add(".xls");
      FILE_TYPES.add(".xlsx");
      FILE_TYPES.add(".ppt");
      FILE_TYPES.add(".pptx");
      FILE_TYPES.add(".py");
      FILE_TYPES.add(".java");
      FILE_TYPES.add(".html");
      FILE_TYPES.add(".htm");
      FILE_TYPES.add(".htxt");
      FILE_PATTERNS = new ArrayList();
      IGNORE_TYPES = new ArrayList();
      IGNORE_PATTERNS = new ArrayList();
   }

   public AbstractFileTask(Logger logger, ResourceMonitor monitor) {
      super(logger, monitor);
   }

   public static ArrayList getFileTypes() {
      return FILE_TYPES;
   }

   public static boolean isFileOfInterest(String name) {
      for(String ignore : IGNORE_TYPES) {
         if (name.contains(ignore)) {
            return false;
         }
      }

      for(Pattern pattern : IGNORE_PATTERNS) {
         Matcher matcher = pattern.matcher(name);
         if (matcher.matches()) {
            return false;
         }
      }

      for(String type : FILE_TYPES) {
         if (name.endsWith(type)) {
            return true;
         }
      }

      for(Pattern pattern : FILE_PATTERNS) {
         Matcher matcher = pattern.matcher(name);
         if (matcher.matches()) {
            return true;
         }
      }

      return false;
   }

   public boolean isIllegal(String line) {
      if (line.endsWith("comas-system-log.html")) {
         return false;
      } else if (line.endsWith("comas-base-log.html")) {
         return false;
      } else {
         for(String ignore : IGNORE_TYPES) {
            if (line.contains(ignore)) {
               return false;
            }
         }

         for(Pattern pattern : IGNORE_PATTERNS) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
               return false;
            }
         }

         for(String type : FILE_TYPES) {
            if (line.endsWith(type)) {
               return true;
            }
         }

         for(Pattern pattern : FILE_PATTERNS) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
               return true;
            }
         }

         return false;
      }
   }

   protected String getFolderOfInterest() {
      return "exam";
   }

   public String[] checkProcessesAccessingFolderOfInterest() {
      return this.processesAccessingFolderOfInterest(this.monitor.activityFolder + File.separator + "exam", false);
   }

   public String[] processesAccessingFolderOfInterest() {
      return this.processesAccessingFolderOfInterest(this.monitor.activityFolder + File.separator + "exam", true);
   }

   public String[] processesAccessingFolderOfInterest(String dir, boolean clear) {
      HashSet<String> processes = new HashSet();
      File[] files = (new File(dir)).listFiles();
      if (files != null) {
         synchronized(this.openFiles) {
            this.openFiles.forEach((k, v) -> {
               for(File file : files) {
                  String name = file.getName();
                  v.forEach((n) -> {
                     if (n.contains(name)) {
                        processes.add(k);
                     }

                  });
               }

            });
            if (clear) {
               this.openFiles.clear();
            }
         }
      }

      return (String[])processes.toArray(new String[processes.size()]);
   }

   protected void getProcessNameAndFileName(String _processName, String fileName) {
      synchronized(this.openFiles) {
         HashSet<String> files = (HashSet)this.openFiles.get(_processName);
         if (files == null) {
            files = new HashSet();
            this.openFiles.put(_processName, files);
         }

         files.add(fileName);
      }
   }

   public static void configure(Properties properties) {
      String os = ClientShared.getOSString();
      int i = 1;

      for(String ftype = properties.getProperty("monitor.file.type." + i); ftype != null; ftype = properties.getProperty("monitor.file.type." + i)) {
         ftype = ftype.trim();
         if (!FILE_TYPES.contains(ftype)) {
            FILE_TYPES.add(ftype);
         }

         ++i;
      }

      i = 1;

      for(String var17 = properties.getProperty("monitor.file.ignore." + i); var17 != null; var17 = properties.getProperty("monitor.file.ignore." + i)) {
         var17 = var17.trim();
         if (!IGNORE_TYPES.contains(var17)) {
            IGNORE_TYPES.add(var17);
         }

         ++i;
      }

      i = 1;

      for(String var19 = properties.getProperty("monitor.file.ignore.pattern." + i); var19 != null; var19 = properties.getProperty("monitor.file.ignore.pattern." + i)) {
         var19 = var19.trim();

         try {
            Pattern pattern = Pattern.compile(var19);
            if (!IGNORE_PATTERNS.contains(pattern)) {
               IGNORE_PATTERNS.add(pattern);
            }
         } catch (PatternSyntaxException var8) {
            edu.carleton.cas.logging.Logger.log(Level.WARNING, var19, " is not a pattern. It has been ignored");
         }

         ++i;
      }

      i = 1;

      for(String var21 = properties.getProperty("monitor.file.type.pattern." + i); var21 != null; var21 = properties.getProperty("monitor.file.type.pattern." + i)) {
         var21 = var21.trim();

         try {
            Pattern pattern = Pattern.compile(var21);
            if (!FILE_PATTERNS.contains(pattern)) {
               FILE_PATTERNS.add(pattern);
            }
         } catch (PatternSyntaxException var7) {
            edu.carleton.cas.logging.Logger.log(Level.WARNING, var21, " is not a pattern. It has been ignored");
         }

         ++i;
      }

      i = 1;

      for(String var23 = properties.getProperty("monitor." + os + ".file.type." + i); var23 != null; var23 = properties.getProperty("monitor." + os + ".file.type." + i)) {
         var23 = var23.trim();
         if (!FILE_TYPES.contains(var23)) {
            FILE_TYPES.add(var23);
         }

         ++i;
      }

      i = 1;

      for(String var25 = properties.getProperty("monitor." + os + ".file.ignore." + i); var25 != null; var25 = properties.getProperty("monitor." + os + ".file.ignore." + i)) {
         var25 = var25.trim();
         if (!IGNORE_TYPES.contains(var25)) {
            IGNORE_TYPES.add(var25);
         }

         ++i;
      }

      i = 1;

      for(String var27 = properties.getProperty("monitor." + os + ".file.ignore.pattern." + i); var27 != null; var27 = properties.getProperty("monitor." + os + ".file.ignore.pattern." + i)) {
         var27 = var27.trim();

         try {
            Pattern pattern = Pattern.compile(var27);
            if (!IGNORE_PATTERNS.contains(pattern)) {
               IGNORE_PATTERNS.add(pattern);
            }
         } catch (PatternSyntaxException var6) {
            edu.carleton.cas.logging.Logger.log(Level.WARNING, var27, " is not a pattern. It has been ignored");
         }

         ++i;
      }

      i = 1;

      for(String var29 = properties.getProperty("monitor." + os + ".file.type.pattern." + i); var29 != null; var29 = properties.getProperty("monitor." + os + ".file.type.pattern." + i)) {
         var29 = var29.trim();

         try {
            Pattern pattern = Pattern.compile(var29);
            if (!FILE_PATTERNS.contains(pattern)) {
               FILE_PATTERNS.add(pattern);
            }
         } catch (PatternSyntaxException var5) {
            edu.carleton.cas.logging.Logger.log(Level.WARNING, var29, " is not a pattern. It has been ignored");
         }

         ++i;
      }

   }
}
