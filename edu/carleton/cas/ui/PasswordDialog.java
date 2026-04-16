package edu.carleton.cas.ui;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.utility.IconLoader;
import java.awt.GridLayout;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;

public class PasswordDialog {
   private static final int MAX_PASSWORD_LEN = 32;
   private final int DEFAULT_OPTION;
   int count;
   CountDownLatch latch;
   JPanel panel;
   JPasswordField pass;
   JOptionPane pane;
   String[] options;
   JFrame frame;
   JDialog dialog;
   String passwordInput;
   boolean waitForLatch;

   public PasswordDialog(JFrame frame, CountDownLatch latch, Invigilator invigilator) {
      this(frame, latch, 32, "Please enter your " + invigilator.getHardwareAndSoftwareMonitor().getVendor() + " password:");
   }

   public PasswordDialog(JFrame frame, CountDownLatch latch, int maxPasswordLength, String question) {
      this.DEFAULT_OPTION = 0;
      this.count = 0;
      this.passwordInput = "";
      this.latch = latch;
      this.waitForLatch = latch != null;
      this.panel = new JPanel();
      this.frame = frame;
      frame.add(this.panel);
      GridLayout layout = new GridLayout(0, 1);
      this.panel.setLayout(layout);
      JLabel label = new JLabel(question);
      this.pass = new JPasswordField(maxPasswordLength);
      this.panel.add(label);
      this.panel.add(this.pass);
      this.options = new String[]{"OK", "Cancel"};
      this.pane = new JOptionPane(this.panel, -1, 1, IconLoader.getIcon(-1), this.options, this.options[0]);
   }

   public static PasswordDialog create(Invigilator invigilator) {
      return new PasswordDialog(WebAlert.refreshAlwaysOnTopFrame(), WebAppDialogCoordinator.getCoordinator(), invigilator);
   }

   public String getPasswordInput() {
      this.dialog.setVisible(false);
      return this.passwordInput;
   }

   public void setWait(boolean waitValue) {
      this.waitForLatch = waitValue;
   }

   public CountDownLatch getPassword() {
      return this.getPassword("User Password Entry");
   }

   public int getCount() {
      return this.count;
   }

   public CountDownLatch getPassword(final String dialogTitle) {
      ++this.count;

      try {
         if (this.latch != null && this.waitForLatch) {
            this.latch.await((long)ClientShared.THIRTY_SECONDS_IN_MSECS, TimeUnit.MILLISECONDS);
         }
      } catch (InterruptedException var3) {
      }

      final CountDownLatch cdl = new CountDownLatch(1);
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            PasswordDialog.this.passwordInput = "";
            PasswordDialog.this.dialog = PasswordDialog.this.pane.createDialog(PasswordDialog.this.frame, dialogTitle);
            PasswordDialog.this.dialog.addComponentListener(new ComponentListener() {
               public void componentShown(ComponentEvent e) {
                  PasswordDialog.this.pass.requestFocusInWindow();
               }

               public void componentResized(ComponentEvent e) {
               }

               public void componentMoved(ComponentEvent e) {
               }

               public void componentHidden(ComponentEvent e) {
               }
            });
            PasswordDialog.this.dialog.setVisible(true);
            PasswordDialog.this.frame.setAlwaysOnTop(true);
            PasswordDialog.this.frame.toFront();
            PasswordDialog.this.frame.repaint();
            PasswordDialog.this.frame.requestFocus();
            if (PasswordDialog.this.pane.getValue() == PasswordDialog.this.options[0]) {
               char[] password = PasswordDialog.this.pass.getPassword();
               PasswordDialog.this.passwordInput = new String(password);
            } else {
               PasswordDialog.this.passwordInput = "";
            }

            cdl.countDown();
         }
      });
      return cdl;
   }
}
