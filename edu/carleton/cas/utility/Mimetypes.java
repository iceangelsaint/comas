package edu.carleton.cas.utility;

import edu.carleton.cas.logging.Logger;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.logging.Level;

public class Mimetypes {
   public static final String MIMETYPE_XML = "application/xml";
   public static final String MIMETYPE_HTML = "text/html";
   public static final String MIMETYPE_OCTET_STREAM = "application/octet-stream";
   public static final String MIMETYPE_GZIP = "application/x-gzip";
   private static Mimetypes mimetypes = null;
   private HashMap extensionToMimetypeMap = new HashMap();

   private Mimetypes() {
   }

   public static synchronized Mimetypes getInstance() {
      if (mimetypes != null) {
         return mimetypes;
      } else {
         mimetypes = new Mimetypes();
         InputStream is = mimetypes.getClass().getResourceAsStream("/mime.types");
         if (is != null) {
            try {
               mimetypes.loadAndReplaceMimetypes(is);
            } catch (IOException var10) {
               Logger.log(Level.WARNING, "", "Failed to load mime types from 'mime.types'");
            } finally {
               try {
                  is.close();
               } catch (IOException var9) {
               }

            }
         } else {
            Logger.log(Level.WARNING, "", "Unable to find 'mime.types' file in classpath");
         }

         return mimetypes;
      }
   }

   public void loadAndReplaceMimetypes(InputStream is) throws IOException {
      BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
      String line = null;

      while((line = br.readLine()) != null) {
         line = line.trim();
         if (!line.startsWith("#") && line.length() != 0) {
            StringTokenizer st = new StringTokenizer(line, " \t");
            if (st.countTokens() > 1) {
               String mimetype = st.nextToken();

               while(st.hasMoreTokens()) {
                  String extension = st.nextToken();
                  this.extensionToMimetypeMap.put(extension.toLowerCase(), mimetype);
               }
            }
         }
      }

   }

   public String getMimetype(String fileName) {
      int lastPeriodIndex = fileName.lastIndexOf(".");
      if (lastPeriodIndex > 0 && lastPeriodIndex + 1 < fileName.length()) {
         String ext = fileName.substring(lastPeriodIndex + 1).toLowerCase();
         if (this.extensionToMimetypeMap.keySet().contains(ext)) {
            String mimetype = (String)this.extensionToMimetypeMap.get(ext);
            return mimetype;
         }
      } else {
         Logger.log(Level.FINE, "", "File name has no extension, mime type cannot be recognised for: " + fileName);
      }

      return "application/octet-stream";
   }

   public String getMimetype(File file) {
      return this.getMimetype(file.getName());
   }
}
