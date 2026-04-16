package edu.carleton.cas.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnZip {
   private static final String BASE;
   private static final String IFILE_CACHE;
   private static final String OFILE_CACHE;

   static {
      String var10000 = System.getProperty("user.home");
      BASE = var10000 + File.separator + "scs" + File.separator;
      IFILE_CACHE = BASE + "cache" + File.separator;
      OFILE_CACHE = BASE + "exams" + File.separator;
   }

   public static void main(String[] args) {
      UnZip unZip = new UnZip();
      unZip.doIt();
   }

   public int doIt() {
      System.out.println("Processing: " + IFILE_CACHE);
      File file = new File(IFILE_CACHE);
      File[] listOfFiles = file.listFiles();

      for(int i = 0; i < listOfFiles.length; ++i) {
         String name = listOfFiles[i].getName();
         if (name.endsWith(".zip")) {
            String studentName = name.substring(0, name.length() - 4);
            if (!studentName.startsWith(".DS_S")) {
               System.out.println("Processing " + studentName + ": output in " + OFILE_CACHE + studentName);
               this.unZipIt(listOfFiles[i].getAbsolutePath(), OFILE_CACHE + studentName);
            }
         }
      }

      return listOfFiles.length;
   }

   public void unZipIt(String zipFile, String outputFolder) {
      byte[] buffer = new byte[1024];

      try {
         File folder = new File(outputFolder);
         if (!folder.exists()) {
            folder.mkdirs();
         }

         ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));

         for(ZipEntry ze = zis.getNextEntry(); ze != null; ze = zis.getNextEntry()) {
            String ofileName = ze.getName();
            if (!ofileName.contains("_MACOSX")) {
               String fileName = ofileName.replace('\\', File.separatorChar);
               File newFile = new File(outputFolder + File.separator + fileName);
               File f = new File(newFile.getParent());
               f.mkdirs();

               try {
                  FileOutputStream fos = new FileOutputStream(newFile);

                  int len;
                  while((len = zis.read(buffer)) > 0) {
                     fos.write(buffer, 0, len);
                  }

                  fos.close();
               } catch (FileNotFoundException var13) {
                  System.err.println("Could not find: " + newFile.getAbsolutePath());
               }

               f.setLastModified(ze.getLastModifiedTime().toMillis());
               newFile.setLastModified(ze.getLastModifiedTime().toMillis());
            }
         }

         zis.closeEntry();
         zis.close();
         System.out.println("Finished processing: " + zipFile);
      } catch (IOException ex) {
         ex.printStackTrace();
      }

   }
}
