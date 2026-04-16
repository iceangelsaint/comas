package edu.carleton.cas.utility;

import edu.carleton.cas.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.logging.Level;

public class DriveSpace {
   public static void main(String[] args) {
      NumberFormat nf = NumberFormat.getNumberInstance();

      for(Path root : FileSystems.getDefault().getRootDirectories()) {
         System.out.print(String.valueOf(root) + ": ");

         try {
            FileStore store = Files.getFileStore(root);
            PrintStream var10000 = System.out;
            String var10001 = nf.format(store.getUsableSpace());
            var10000.println("available=" + var10001 + ", total=" + nf.format(store.getTotalSpace()) + ", %free=" + nf.format((double)store.getUsableSpace() * (double)100.0F / (double)store.getTotalSpace()));
         } catch (IOException e) {
            System.out.println("error querying space: " + e.toString());
         }
      }

      System.out.println("\nCheck using DriveSpace.free(): " + nf.format(free()) + "%");
   }

   public static double free(File file) {
      return file != null ? (double)file.getFreeSpace() * (double)100.0F / (double)file.getTotalSpace() : (double)0.0F;
   }

   public static double freeMB(File file) {
      return file != null ? (double)file.getFreeSpace() / (double)1048576.0F : (double)0.0F;
   }

   public static double free() {
      double min = (double)100.0F;

      for(Path root : FileSystems.getDefault().getRootDirectories()) {
         try {
            FileStore store = Files.getFileStore(root);
            double actual = (double)store.getUsableSpace() * (double)100.0F / (double)store.getTotalSpace();
            if (actual < min) {
               min = actual;
            }
         } catch (IOException e) {
            Logger.log(Level.WARNING, "error querying " + String.valueOf(root) + " space: ", e.toString());
         }
      }

      return min;
   }

   public static double freeMB() {
      double min = 1.0E30;

      for(Path root : FileSystems.getDefault().getRootDirectories()) {
         try {
            FileStore store = Files.getFileStore(root);
            double actual = (double)store.getUsableSpace();
            if (actual < min) {
               min = actual;
            }
         } catch (IOException e) {
            Logger.log(Level.WARNING, "error querying " + String.valueOf(root) + " space: ", e.toString());
         }
      }

      return min / (double)1048576.0F;
   }
}
