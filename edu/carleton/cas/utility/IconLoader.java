package edu.carleton.cas.utility;

import edu.carleton.cas.constants.ClientShared;
import java.awt.Image;
import java.util.ArrayList;
import javax.swing.ImageIcon;

public abstract class IconLoader {
   public static ImageIcon getIcon(String root, String size) {
      String resource = String.format("/images/%s-icon-%s.png", root, size);
      return new ImageIcon(IconLoader.class.getResource(resource));
   }

   public static ImageIcon getDefaultIcon(String root) {
      if (ClientShared.isMacOS()) {
         return getIcon(root, "64x64");
      } else {
         return ClientShared.isWindowsOS() ? getIcon(root, "48x48") : getIcon(root, "64x64");
      }
   }

   public static ImageIcon getDefaultIcon() {
      return getDefaultIcon("social-sharing");
   }

   public static ImageIcon getIcon(int optionType) {
      if (optionType == 2) {
         return getDefaultIcon("warning");
      } else {
         return optionType == 0 ? getDefaultIcon("error") : getDefaultIcon();
      }
   }

   public static ArrayList getImages() {
      ArrayList<Image> images = new ArrayList();
      String[] sizes = new String[]{"16x16", "24x24", "32x32", "48x48", "64x64", "72x72", "96x96", "128x128"};

      for(String imageSize : sizes) {
         images.add(getIcon("social-sharing", imageSize).getImage());
      }

      return images;
   }
}
