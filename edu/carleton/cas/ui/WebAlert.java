package edu.carleton.cas.ui;

import edu.carleton.cas.utility.IconLoader;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public abstract class WebAlert {
   private static final String COMAS_ALERT = "CoMaS Alert!";
   private static final Icon DEFAULT_ICON = IconLoader.getDefaultIcon();

   public static void exitAfterAlert(final String msg, final int code) {
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            WebAlert.errorDialog(msg);
            System.exit(code);
         }
      });
   }

   public static void error(final String msg) {
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            WebAlert.errorDialog(msg);
         }
      });
   }

   public static void warning(final String msg) {
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            WebAlert.warningDialog(msg);
         }
      });
   }

   public static int confirmDialog(String message, String title) {
      return JOptionPane.showConfirmDialog(refreshAlwaysOnTopFrame(), message, title, 0, 2, IconLoader.getIcon(2));
   }

   public static void informationDialog(String message, String title) {
      JOptionPane.showMessageDialog(refreshAlwaysOnTopFrame(), message, title, 1, IconLoader.getIcon(1));
   }

   public static void errorDialog(String message) {
      JOptionPane.showMessageDialog(refreshAlwaysOnTopFrame(), message, "CoMaS Alert!", 0, IconLoader.getIcon(0));
   }

   public static void warningDialog(String message, String title) {
      JOptionPane.showMessageDialog(refreshAlwaysOnTopFrame(), message, title, 2, IconLoader.getIcon(2));
   }

   public static void warningDialog(String message) {
      warningDialog(message, "CoMaS Alert!");
   }

   public static void alert(final String message) {
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            WebAlert.alertDialog(message, "CoMaS Alert!");
         }
      });
   }

   public static void alertDialog(String message) {
      alertDialog(message, "CoMaS Alert!");
   }

   public static void alertDialog(String message, String title) {
      JOptionPane.showMessageDialog(refreshAlwaysOnTopFrame(), message, title, 1, DEFAULT_ICON);
   }

   public static JFrame refreshAlwaysOnTopFrame() {
      JFrame alwaysOnTopFrame = new JFrame();
      alwaysOnTopFrame.setAlwaysOnTop(true);
      return alwaysOnTopFrame;
   }
}
