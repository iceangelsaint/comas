package edu.carleton.cas.utility;

import java.awt.Dimension;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.media.MediaLocator;

public class JpegMovieMaker {
   private static final String INPUT_DIR = System.getProperty("user.home") + "/comp1405-exam-2017/";
   private static boolean VERBOSE = false;
   private static boolean DELETE_JPG = true;

   public static boolean makeVideo(String dir, String fileName, int screenWidth, int screenHeight, int interval) throws IOException {
      Vector<String> imgLst = listOfJpegFiles(dir);
      if (imgLst.isEmpty()) {
         System.err.println("No jpg files in " + dir);
         return false;
      } else {
         JpegImagesToMovie imageToMovie = new JpegImagesToMovie();
         MediaLocator oml;
         if ((oml = JpegImagesToMovie.createMediaLocator(fileName)) == null) {
            System.err.println("Cannot build media locator from: " + fileName);
            return false;
         } else {
            Dimension dim;
            if (screenWidth != 0 && screenHeight != 0) {
               dim = new Dimension(screenWidth, screenHeight);
            } else {
               dim = getImageDimension((String)imgLst.get(0));
            }

            int framesPerSecond = Math.max(1, imgLst.size() / interval);
            return imageToMovie.doIt(dim.width, dim.height, framesPerSecond, imgLst, oml);
         }
      }
   }

   private static Vector listOfJpegFiles(String dir) {
      File f = new File(dir);
      FileFilter filter = new FileFilter() {
         public boolean accept(File p) {
            return p.getName().endsWith(".jpg");
         }
      };
      Vector<String> v = new Vector();
      if (f.isDirectory()) {
         File[] acctFiles = f.listFiles(filter);

         for(File file : acctFiles) {
            try {
               if (VERBOSE) {
                  System.out.println("Processing " + file.getCanonicalPath());
               }
            } catch (IOException e) {
               System.err.println("I/O error: " + e.getMessage());
            }

            v.add(file.getAbsolutePath());
         }
      }

      return v;
   }

   private static Dimension getImageDimension(String resourceFile) throws IOException {
      Throwable var1 = null;
      Object var2 = null;

      try {
         ImageInputStream in = ImageIO.createImageInputStream(new File(resourceFile));

         Dimension var7;
         try {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
               throw new IOException("Could not get image size for " + resourceFile);
            }

            ImageReader reader = (ImageReader)readers.next();

            try {
               reader.setInput(in);
               var7 = new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
               reader.dispose();
            }
         } finally {
            if (in != null) {
               in.close();
            }

         }

         return var7;
      } catch (Throwable var19) {
         if (var1 == null) {
            var1 = var19;
         } else if (var1 != var19) {
            var1.addSuppressed(var19);
         }

         throw var1;
      }
   }

   public static void main(String[] args) {
      if (args.length > 0 && args[1].equals("-v")) {
         VERBOSE = true;
      }

      int movies = 0;
      File file = new File(INPUT_DIR);
      File[] listOfFiles = file.listFiles();

      for(int i = 0; i < listOfFiles.length; ++i) {
         String dir = listOfFiles[i].getAbsolutePath() + "/screens/";
         String movie = dir + listOfFiles[i].getName() + ".mov";

         try {
            if (!listOfFiles[i].getName().startsWith(".DS_")) {
               System.out.println("Processing " + listOfFiles[i].getName());
               if (makeVideo(dir, movie, 0, 0, 30)) {
                  ++movies;
               }

               if (DELETE_JPG) {
                  File folder = new File(dir);
                  File[] fList = folder.listFiles();

                  for(int j = 0; j < fList.length; ++j) {
                     String pes = fList[j].getName();
                     if (pes.endsWith(".jpg")) {
                        fList[j].delete();
                     }
                  }
               }
            }
         } catch (IOException e) {
            e.printStackTrace();
         }
      }

      System.out.println("There were " + movies + " movies produced");
   }
}
