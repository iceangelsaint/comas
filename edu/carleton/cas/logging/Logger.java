package edu.carleton.cas.logging;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.file.Utils;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;

public class Logger {
   private static java.util.logging.Logger logger;
   private static boolean DEBUG = false;

   public static void setup(Class clazz, String name, String systemLogDir, Level level, Invigilator invigilator) throws IOException {
      if (logger == null) {
         Properties properties = invigilator.getProperties();
         File dir = new File(systemLogDir);
         if (dir.exists() && dir.isDirectory() && dir.canWrite()) {
            logger = java.util.logging.Logger.getLogger(clazz.getName());
            Handler[] handlers = logger.getHandlers();

            for(int i = 0; i < handlers.length; ++i) {
               logger.removeHandler(handlers[i]);
            }

            logger.setUseParentHandlers(false);
            logger.setLevel(level);
            String base;
            if (systemLogDir.endsWith(File.separator)) {
               base = systemLogDir + name;
            } else {
               base = systemLogDir + File.separator + name;
            }

            boolean formatRequired = Utils.getBooleanOrDefault(properties, "logs.format.csv", false);
            if (formatRequired) {
               FileHandler handler = new FileHandler(base + "-log.csv");
               handler.setFormatter(new CSVFormatter());
               logger.addHandler(handler);
            }

            String dateFormat = Utils.getStringOrDefault(properties, "logs.format.date", HtmlFormatter.DATE_FORMAT);

            SimpleDateFormat sdf;
            try {
               sdf = new SimpleDateFormat(dateFormat.trim());
               HtmlFormatter.DATE_FORMAT = dateFormat.trim();
            } catch (IllegalArgumentException var14) {
               log(Level.WARNING, "Illegal log date format, using ", HtmlFormatter.DATE_FORMAT);
               sdf = new SimpleDateFormat(HtmlFormatter.DATE_FORMAT);
            }

            formatRequired = Utils.getBooleanOrDefault(properties, "logs.format.html", true);
            if (formatRequired) {
               FileHandler handler = new FileHandler(base + "-log.html");
               String title = Utils.getStringOrDefault(properties, "logs.title", "Logs");
               handler.setFormatter(new HtmlFormatter(title, sdf, invigilator.getServletProcessor().refreshField()));
               logger.addHandler(handler);
            }

            DEBUG = Utils.getBooleanOrDefault(properties, "logs.format.debug", false);
         } else {
            log(Level.WARNING, "Cannot write to ", systemLogDir);
         }
      }

   }

   public static void close() {
      if (logger != null) {
         Handler[] handlers = logger.getHandlers();

         for(int i = 0; i < handlers.length; ++i) {
            handlers[i].close();
         }

         logger = null;
      }

   }

   public static void setLevel(String level) {
      if (logger != null) {
         logger.setLevel(edu.carleton.cas.logging.Level.parse(level));
      }

   }

   public static void log(Level level, String msg, Object obj) {
      if (logger != null) {
         logger.log(level, msg + obj.toString());
      } else {
         debug(level, msg, obj);
      }

   }

   public static void debug(Level level, String msg, Object obj) {
      if (DEBUG) {
         System.err.format("CoMaS[%s]: %s%s\n", level, msg, obj.toString());
      }

   }

   public static void debug(Level level, String msg) {
      if (DEBUG) {
         System.err.format("CoMaS[%s]: %s\n", level, msg);
      }

   }

   public static void output(String msg, Object obj) {
      if (DEBUG) {
         System.out.format("CoMaS[INFO]: %s%s\n", msg, obj.toString());
      }

   }

   public static void output(String msg) {
      if (DEBUG) {
         System.out.format("CoMaS[INFO]: %s\n", msg);
      }

   }

   public void log(Level level, String msg) {
      if (logger != null) {
         logger.log(level, msg);
      }

   }

   public static void log(String level, String msg) {
      if (logger != null) {
         logger.log(edu.carleton.cas.logging.Level.parse(level), msg);
      }

   }
}
