package edu.carleton.cas.file;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public abstract class DirectoryUtils {
   public static void destroyDirectory(String dir) throws IOException {
      Path directory = Paths.get(dir);
      Files.walkFileTree(directory, new SimpleFileVisitor() {
         public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
         }

         public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
         }
      });
   }

   public static void destroyDirectoryContents(String dir) throws IOException {
      Path directory = Paths.get(dir);
      Files.walkFileTree(directory, new SimpleFileVisitor() {
         public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
         }

         public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
         }
      });
   }

   public static void destroyDirectoryOnExit(String dir) throws IOException {
      Path directory = Paths.get(dir);
      Files.walkFileTree(directory, new SimpleFileVisitor() {
         public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            file.toFile().deleteOnExit();
            return FileVisitResult.CONTINUE;
         }

         public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            dir.toFile().deleteOnExit();
            return FileVisitResult.CONTINUE;
         }
      });
   }

   public static void copyDirectory(String source, String target) throws IOException {
      final Path targetPath = Paths.get(target);
      final Path sourcePath = Paths.get(source);
      Files.walkFileTree(sourcePath, new SimpleFileVisitor() {
         public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir)));
            return FileVisitResult.CONTINUE;
         }

         public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.copy(file, targetPath.resolve(sourcePath.relativize(file)));
            return FileVisitResult.CONTINUE;
         }
      });
   }
}
