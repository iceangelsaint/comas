package edu.carleton.cas.utility;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.logging.Logger;
import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;

public abstract class Displays {
   public static Rectangle getBounds() {
      Rectangle bounds = new Rectangle();

      GraphicsDevice[] var4;
      for(GraphicsDevice gd : var4 = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
         bounds.add(gd.getDefaultConfiguration().getBounds());
      }

      return bounds;
   }

   public BufferedImage fullScreen() throws AWTException {
      Robot robot = new Robot();
      return robot.createScreenCapture(getBounds());
   }

   public static synchronized BufferedImage getImage() throws AWTException {
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice gd = ge.getDefaultScreenDevice();
      return getImage(gd, gd.getDefaultConfiguration().getBounds().x, gd.getDefaultConfiguration().getBounds().y);
   }

   public static synchronized BufferedImage getImage(GraphicsDevice screen, int x, int y) throws AWTException {
      Robot robotForScreen = new Robot(screen);
      JPEGImageWriteParam jpegParams = new JPEGImageWriteParam((Locale)null);
      jpegParams.setCompressionMode(2);
      jpegParams.setCompressionQuality(ClientShared.IMAGE_COMPRESSION);
      Rectangle screenBounds = screen.getDefaultConfiguration().getBounds();
      screenBounds.x = x;
      screenBounds.y = y;
      screenBounds.setSize(screen.getDisplayMode().getWidth(), screen.getDisplayMode().getHeight());
      BufferedImage screenShot = robotForScreen.createScreenCapture(screenBounds);
      return screenShot;
   }

   public static BufferedImage[] getImages() throws AWTException {
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] gDevs = ge.getScreenDevices();
      BufferedImage[] images = new BufferedImage[gDevs.length];

      for(int i = 0; i < gDevs.length; ++i) {
         String var10000 = String.valueOf(gDevs[i].getDefaultConfiguration().getBounds());
         Logger.output(var10000 + " " + gDevs[i].getDisplayMode().getWidth() + " " + gDevs[i].getDisplayMode().getHeight());
         images[i] = getImage(gDevs[i], gDevs[i].getDefaultConfiguration().getBounds().x, gDevs[i].getDefaultConfiguration().getBounds().y);
      }

      return images;
   }

   public static Rectangle[] getScreenBounds() {
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] screens = ge.getScreenDevices();
      Rectangle[] bounds = new Rectangle[screens.length];

      for(int i = 0; i < screens.length; ++i) {
         bounds[i] = screens[i].getDefaultConfiguration().getBounds();
      }

      return bounds;
   }

   public static String logScreenBounds() {
      Rectangle[] bounds = getScreenBounds();
      StringBuilder buf = new StringBuilder("Screen Bounds: ");

      for(Rectangle bound : bounds) {
         buf.append("[width=");
         buf.append(bound.getWidth());
         buf.append(",");
         buf.append("height=");
         buf.append(bound.getHeight());
         buf.append("] ");
      }

      return buf.toString();
   }

   public static int getNumberOfDisplays() {
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] screens = ge.getScreenDevices();
      return screens.length;
   }

   public static boolean isOk(BufferedImage image, float percentageOfPointsToTest) {
      if (!((double)percentageOfPointsToTest < (double)0.0F) && !((double)percentageOfPointsToTest > (double)1.0F)) {
         int totalPoints = Math.round((float)(image.getHeight() * image.getWidth()) * percentageOfPointsToTest);
         return isOk(image, totalPoints);
      } else {
         return false;
      }
   }

   public static boolean isOk(BufferedImage image, int numberOfTestPoints) {
      int height = image.getHeight();
      int width = image.getWidth();
      if (numberOfTestPoints == 0) {
         return true;
      } else if (numberOfTestPoints >= height * width) {
         return isOk(image);
      } else {
         ThreadLocalRandom tlr = ThreadLocalRandom.current();

         for(int i = 0; i < numberOfTestPoints; ++i) {
            int x1 = tlr.nextInt(width);
            int y1 = tlr.nextInt(height);
            int x2 = tlr.nextInt(width);
            int y2 = tlr.nextInt(height);
            if (image.getRGB(x1, y1) != image.getRGB(x2, y2)) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean isOk(BufferedImage image) {
      int height = image.getHeight();
      int width = image.getWidth();
      int value = image.getRGB(0, 0);

      for(int x = 0; x < width; ++x) {
         for(int y = 0; y < height; ++y) {
            if (image.getRGB(x, y) != value) {
               return true;
            }
         }
      }

      return false;
   }

   public static boolean areSame(BufferedImage img1, BufferedImage img2) {
      if (img1 != null && img2 != null) {
         if (img1.getWidth() == img2.getWidth() && img1.getHeight() == img2.getHeight()) {
            for(int y = 0; y < img1.getHeight(); ++y) {
               for(int x = 0; x < img1.getWidth(); ++x) {
                  if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
                     return false;
                  }
               }
            }

            return true;
         } else {
            return false;
         }
      } else {
         return img1 == img2;
      }
   }
}
