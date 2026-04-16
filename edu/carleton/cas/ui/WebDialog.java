package edu.carleton.cas.ui;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.jetty.embedded.ProgressServlet;
import edu.carleton.cas.jetty.embedded.SessionEndTask;
import edu.carleton.cas.utility.IconLoader;
import edu.carleton.cas.utility.Observable;
import edu.carleton.cas.utility.Observer;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.desktop.QuitStrategy;
import java.awt.desktop.ScreenSleepEvent;
import java.awt.desktop.ScreenSleepListener;
import java.awt.desktop.SystemSleepEvent;
import java.awt.desktop.SystemSleepListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class WebDialog extends JFrame implements Observer {
   WindowAdapter windowAdapter;
   AtomicBoolean confirmedQuit = new AtomicBoolean(false);
   Invigilator invigilator;
   Thread thread;
   private static final long serialVersionUID = 1L;

   public WebDialog(String name, Invigilator invigilator) {
      super(name);
      this.invigilator = invigilator;
      this.thread = null;
      this.initWindowEventHandlers();
      this.setIconImages(IconLoader.getImages());
      this.pack();
      this.setLocationRelativeTo((Component)null);
      this.setVisible(false);
   }

   private void initWindowEventHandlers() {
      if (ClientShared.isMacOS()) {
         System.setProperty("apple.eawt.quitStrategy", "CLOSE_ALL_WINDOWS");
         System.setProperty("apple.awt.application.name", this.getTitle());
         System.setProperty("apple.laf.useScreenMenuBar", "true");
         System.setProperty("com.apple.mrj.application.apple.menu.about.name", this.getTitle());
      }

      this.windowAdapter = new WindowAdapter() {
         public void windowClosing(WindowEvent e) {
            super.windowClosing(e);
            WebDialog.this.quitProcessing();
         }

         public void windowClosed(WindowEvent e) {
            super.windowClosed(e);
            System.exit(0);
         }
      };

      try {
         if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            desktop.addAppEventListener(new SystemSleepListener() {
               public void systemAboutToSleep(SystemSleepEvent e) {
                  WebDialog.this.invigilator.setStateAndAuthenticate("Login Sleeping", WebDialog.this.invigilator.createProblemSetEvent("sleeping"));
               }

               public void systemAwoke(SystemSleepEvent e) {
                  WebDialog.this.invigilator.setStateAndAuthenticate("Login Awake", WebDialog.this.invigilator.createProblemClearEvent("sleeping"));
               }
            });
            desktop.addAppEventListener(new ScreenSleepListener() {
               public void screenAboutToSleep(ScreenSleepEvent e) {
                  WebDialog.this.invigilator.setStateAndAuthenticate("Login Screen off", WebDialog.this.invigilator.createProblemSetEvent("screen_off"));
               }

               public void screenAwoke(ScreenSleepEvent e) {
                  WebDialog.this.invigilator.setStateAndAuthenticate("Login Screen on", WebDialog.this.invigilator.createProblemClearEvent("screen_off"));
               }
            });

            try {
               desktop.setAboutHandler((e) -> WebAlert.informationDialog("CoMaS v0.8.75", "About CoMaS"));
            } catch (UnsupportedOperationException var14) {
            }

            try {
               desktop.setPreferencesHandler((e) -> {
                  String var10000 = this.invigilator.getSessionContext();
                  WebAlert.informationDialog("Session Context: " + var10000 + " Start: " + String.valueOf(new Date(this.invigilator.getActualStartTime() == 0L ? System.currentTimeMillis() : this.invigilator.getActualStartTime())) + "\n" + this.invigilator.getSessionStartContext() + "\n\nVendor: " + this.invigilator.getHardwareAndSoftwareMonitor().getVendor() + " Processor: " + this.invigilator.getHardwareAndSoftwareMonitor().getProcessorIdentifier() + "\nIdentifier: " + this.invigilator.getHardwareAndSoftwareMonitor().getComputerIdentifier() + "\nOS: " + this.invigilator.getHardwareAndSoftwareMonitor().getOS() + String.format("\nMemory Available: %.01fGB Total: %.01fGB", this.invigilator.getHardwareAndSoftwareMonitor().getAvailableMemory(), this.invigilator.getHardwareAndSoftwareMonitor().getTotalMemory()) + "\n" + this.invigilator.getHardwareAndSoftwareMonitor().getNetworkingSummary(), "CoMaS Context");
               });
            } catch (UnsupportedOperationException var13) {
            }

            try {
               desktop.disableSuddenTermination();
            } catch (UnsupportedOperationException var12) {
            }

            try {
               desktop.setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS);
            } catch (UnsupportedOperationException var11) {
            }

            try {
               desktop.setQuitHandler((e, r) -> {
                  r.cancelQuit();
                  this.quitProcessing();
               });
            } catch (UnsupportedOperationException var10) {
            }
         }
      } finally {
         this.setDefaultCloseOperation(0);
         this.addWindowListener(this.windowAdapter);
      }

   }

   private void quitProcessing() {
      StringBuffer pMsg = new StringBuffer();
      if (!this.invigilator.isConnected()) {
         pMsg.append("You are not currently connected.\nPlease check your network connection.\n");
      }

      String[] processes = this.invigilator.processesAccessingFolderOfInterest();
      if (processes != null && processes.length > 0) {
         pMsg.append("Please close all applications accessing exam files as some files may still be open.\n\nApplications: ");

         for(String process : processes) {
            pMsg.append("\n");
            pMsg.append(process);
            this.invigilator.getHardwareAndSoftwareMonitor().addFinalizedProcess(process);
         }
      }

      this.confirmQuit(pMsg);
   }

   private void confirmQuit(StringBuffer msg) {
      int res;
      if (this.confirmedQuit.get()) {
         res = 0;
      } else {
         if (msg.length() > 0) {
            msg.append("\n\n");
         }

         msg.append("Are you sure you want to end session?");
         res = WebAlert.confirmDialog(msg.toString(), "End CoMaS session?");
      }

      if (res == 0) {
         this.confirmedQuit.set(true);
         if (this.thread == null) {
            this.thread = new Thread(new SessionEndTask(this.invigilator, ProgressServlet.getSingleton()));
            this.thread.start();
         }
      } else {
         this.confirmedQuit.set(false);
      }

   }

   public void update(final Observable resource, final Object arg) {
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            if (WebDialog.this.invigilator.isInInvigilatorState(InvigilatorState.ending)) {
               if (arg != null && Utils.getBooleanOrDefault(WebDialog.this.invigilator.getProperties(), "alert.show_on_exit", true)) {
                  String msg = arg.toString();
                  if (msg.startsWith("Archive")) {
                     WebAlert.alertDialog("Final " + msg);
                  } else {
                     WebAlert.alertDialog(msg);
                  }
               }
            } else if (arg != null) {
               WebAlert.alertDialog(arg.toString());
            } else if (resource == WebDialog.this.invigilator && WebDialog.this.invigilator.getAlert() != null) {
               WebAlert.alertDialog(WebDialog.this.invigilator.getAlert());
               WebDialog.this.invigilator.resetAlert();
            }

         }
      });
   }
}
