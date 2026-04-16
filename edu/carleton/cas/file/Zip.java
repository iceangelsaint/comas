package edu.carleton.cas.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Zip {
   public static void pack(String sourceDirPath, String zipFilePath) throws IOException {
      Path p = Files.createFile(Paths.get(zipFilePath));
      Throwable var3 = null;
      Object var4 = null;

      try {
         ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p));

         try {
            Path pp = Paths.get(sourceDirPath);
            Files.walk(pp).filter((path) -> !Files.isDirectory(path, new LinkOption[0])).forEach((path) -> {
               ZipEntry zipEntry = new ZipEntry(pp.relativize(path).toString());
               zipEntry.setLastModifiedTime(FileTime.fromMillis(path.toFile().lastModified()));

               try {
                  zs.putNextEntry(zipEntry);
                  zs.write(Files.readAllBytes(path));
                  zs.closeEntry();
               } catch (Exception e) {
                  System.err.println(e);
               }

            });
         } finally {
            if (zs != null) {
               zs.close();
            }

         }

      } catch (Throwable var12) {
         if (var3 == null) {
            var3 = var12;
         } else if (var3 != var12) {
            var3.addSuppressed(var12);
         }

         throw var3;
      }
   }
}
