package edu.carleton.cas.resources;

import com.cogerent.utility.PropertyValue;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.ui.WebAlert;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;

public class WebAppPageLauncher extends Thread {
   private static final String[] browserStartCmd = new String[]{"open -a Safari %s", "start msedge %s", "google-chrome %s", "firefox %s"};
   private String cmd;
   private Process process;
   private final String pageToLaunch;
   private final Invigilator login;
   private final boolean useDefaultBrowser;

   public WebAppPageLauncher(String pageToLaunch, Invigilator login) {
      edu.carleton.cas.logging.Logger.debug(Level.INFO, ClientShared.getOS().toString());
      this.useDefaultBrowser = PropertyValue.getValue(login, "session", "use_default_browser", false);
      if (ClientShared.isMacOS()) {
         this.cmd = browserStartCmd[0];
      } else if (ClientShared.isWindowsOS()) {
         this.cmd = browserStartCmd[1];
      } else {
         this.cmd = browserStartCmd[2];
      }

      this.pageToLaunch = pageToLaunch;
      this.login = login;
      this.setName("web app launcher");
      this.setUncaughtExceptionHandler(login);
   }

   public void run() {
      if (ClientShared.isMacOS()) {
         this.runBrowserCommand(new String[]{"sh", "-c"});
      } else if (ClientShared.isWindowsOS()) {
         this.runBrowserCommand(new String[]{"cmd", "/c"});
      } else if (ClientShared.isLinuxOS() && !this.runBrowserCommand(new String[]{"sh", "-c"})) {
         this.cmd = browserStartCmd[3];
         this.runBrowserCommand(new String[]{"sh", "-c"});
      }

   }

   private boolean runBrowserCommand(String[] shellCmd) {
      boolean okay;
      if (this.useDefaultBrowser) {
         okay = this.runOSDefaultBrowserCommand();
         if (!okay) {
            okay = this.runOSBrowserCommand(shellCmd);
         }
      } else {
         okay = this.runOSBrowserCommand(shellCmd);
      }

      return okay;
   }

   private boolean runOSDefaultBrowserCommand() {
      boolean returnValue = true;

      try {
         Desktop.getDesktop().browse(new URI(this.pageToLaunch));
      } catch (URISyntaxException | UnsupportedOperationException | IOException var3) {
         WebAlert.warning("Could not create session browser pages");
         returnValue = false;
      }

      return returnValue;
   }

   private boolean runOSBrowserCommand(String[] shellCmd) {
      boolean returnValue = true;
      String cmdToRun = String.format(this.cmd, this.pageToLaunch);

      try {
         edu.carleton.cas.logging.Logger.debug(Level.INFO, cmdToRun);
         this.process = Runtime.getRuntime().exec(new String[]{shellCmd[0], shellCmd[1], cmdToRun});
         InputStream stdout = this.process.getInputStream();
         BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));

         String line;
         while((line = reader.readLine()) != null) {
            edu.carleton.cas.logging.Logger.debug(Level.FINE, line);
         }

         returnValue = true;
      } catch (Exception e) {
         this.login.logArchiver.put(Level.DIAGNOSTIC, String.format("Failed to run %s: %s", cmdToRun, e.toString()));
         returnValue = false;
      } finally {
         this.close();
      }

      return returnValue;
   }

   public void close() {
   }
}
