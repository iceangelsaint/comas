package edu.carleton.cas.utility;

import edu.carleton.cas.constants.ClientShared;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class ScreenShotPermissionTest implements Runnable {
   private static final int DEFAULT_OFFSET = 50;
   private static final int DEFAULT_WAIT_IN_MSECS = 1000;
   private final File folder;
   private final int time;
   private int offset;
   private boolean result;

   public ScreenShotPermissionTest(File folder, int time, int offset) {
      if (time > 0) {
         this.time = time;
      } else {
         this.time = 1000;
      }

      this.folder = folder;
      if (offset > 0) {
         this.offset = offset;
      } else {
         this.offset = 50;
      }

   }

   private boolean bufferedImagesEqual(BufferedImage img1, BufferedImage img2) {
      int img_offset = Math.min(this.offset, img1.getWidth());
      img_offset = Math.min(img_offset, img1.getHeight());
      img_offset = Math.min(img_offset, img2.getWidth());
      img_offset = Math.min(img_offset, img2.getHeight());
      if (img1.getWidth() == img2.getWidth() && img1.getHeight() == img2.getHeight()) {
         for(int x = img_offset; x < img1.getWidth() - img_offset; ++x) {
            for(int y = img_offset; y < img1.getHeight() - img_offset; ++y) {
               if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean hasPermission() {
      return !this.result;
   }

   public void run() {
      if (!ClientShared.isMacOS()) {
         this.result = false;
      } else {
         this.result = true;

         try {
            BufferedImage[] image1 = Displays.getImages();
            File parent = this.folder.getParentFile();
            if (!parent.exists() || !parent.canWrite()) {
               throw new IOException("Cannot write to " + parent.getName());
            }

            if (!this.folder.mkdir()) {
               throw new IOException("Cannot create folder " + this.folder.getName());
            }

            Sleeper.sleep(this.time);
            BufferedImage[] image2 = Displays.getImages();

            for(int i = 0; i < image1.length && this.result; ++i) {
               this.result = this.bufferedImagesEqual(image1[i], image2[i]);
            }

            this.folder.delete();
         } catch (Exception var5) {
            this.result = true;
         }

      }
   }

   public static void main(String[] args) {
      String var10000 = System.getProperty("user.home");
      String home = var10000 + File.separator + "Desktop" + File.separator;
      int rint = ThreadLocalRandom.current().nextInt(100000, 999999);
      ScreenShotPermissionTest sst = new ScreenShotPermissionTest(new File(home + rint), 1000, 50);
      sst.run();
      if (sst.hasPermission()) {
         System.out.println("You have permission to record the screen");
      } else {
         System.out.println("You do not have permission to record the screen");
      }

   }
}
