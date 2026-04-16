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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;

public class BrowserTabsFromServer extends Thread {
   private static final String[] browserStartCmd = new String[]{"open -a Safari %s", "start msedge %s", "google-chrome %s", "firefox %s"};
   private String cmd;
   private Process process;
   private final Properties properties;
   private final Invigilator login;
   private final boolean useDefaultBrowser;

   public BrowserTabsFromServer(Invigilator login) {
      this(login.getProperties(), login);
   }

   public BrowserTabsFromServer(Properties properties, Invigilator login) {
      this.properties = properties;
      this.useDefaultBrowser = PropertyValue.getValue(login, "session", "use_default_browser", false);
      edu.carleton.cas.logging.Logger.debug(Level.INFO, ClientShared.getOS().toString());
      if (ClientShared.isMacOS()) {
         this.cmd = browserStartCmd[0];
      } else if (ClientShared.isWindowsOS()) {
         this.cmd = browserStartCmd[1];
      } else {
         this.cmd = browserStartCmd[2];
      }

      this.login = login;
      this.setName("browser");
      this.setUncaughtExceptionHandler(login);
   }

   public void run() {
      if (!this.login.isInEndingState()) {
         this.login.getServletProcessor().waitForService();
         ArrayList<String> services = this.servicesToRun("service.load.");
         if (services.size() == 0) {
            services = this.servicesToRun("session.page.load.");
         }

         if (services.size() != 0) {
            if (ClientShared.isMacOS()) {
               Collections.reverse(services);
               this.runBrowserCommand(new String[]{"sh", "-c"}, services);
            } else if (ClientShared.isWindowsOS()) {
               this.runBrowserCommand(new String[]{"cmd", "/c"}, services);
            } else if (ClientShared.isLinuxOS() && !this.runBrowserCommand(new String[]{"sh", "-c"}, services)) {
               this.cmd = browserStartCmd[3];
               this.runBrowserCommand(new String[]{"sh", "-c"}, services);
            }

         }
      }
   }

   private boolean runBrowserCommand(String[] shellCmd, ArrayList services) {
      boolean okay;
      if (this.useDefaultBrowser) {
         okay = this.runOSDefaultBrowserCommand(services);
         if (!okay) {
            okay = this.runOSBrowserCommand(shellCmd, services);
         }
      } else {
         okay = this.runOSBrowserCommand(shellCmd, services);
      }

      return okay;
   }

   private boolean runOSDefaultBrowserCommand(ArrayList services) {
      boolean returnValue = true;

      for(String service : services) {
         try {
            Desktop.getDesktop().browse(new URI(service));
         } catch (URISyntaxException | UnsupportedOperationException | IOException var6) {
            WebAlert.warning("Could not create session browser pages");
            returnValue = false;
         }
      }

      return returnValue;
   }

   private boolean runOSBrowserCommand(String[] shellCmd, ArrayList services) {
      boolean returnValue = true;
      StringBuilder buff = new StringBuilder();

      for(String service : services) {
         buff.append(" ");
         String sanitizedService = service.replace("'", "\\'");
         buff.append(sanitizedService);
      }

      String cmdToRun = String.format(this.cmd, buff.toString());

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

   private ArrayList servicesToRun(String baseString) {
      ArrayList<String> services = new ArrayList();
      int i = 1;

      for(String serviceString = this.properties.getProperty(baseString + i); serviceString != null; serviceString = this.properties.getProperty(baseString + i)) {
         String serviceToBeLoaded = serviceString.trim();
         String service;
         if (serviceToBeLoaded.startsWith("http")) {
            service = this.resolveVariables(serviceToBeLoaded);
         } else if (serviceToBeLoaded.startsWith("/") && !serviceToBeLoaded.contains("/rest/")) {
            service = this.resolveVariables(serviceToBeLoaded);
            String var10000 = this.login.getServletProcessor().getService();
            service = var10000 + service.substring(1);
         } else {
            if (serviceToBeLoaded.startsWith("/")) {
               serviceToBeLoaded = serviceToBeLoaded.substring(1);
            }

            service = String.format("%s://%s:%s/%s", ClientShared.PROTOCOL, ClientShared.DIRECTORY_HOST, ClientShared.PORT, this.resolveVariables(serviceToBeLoaded));
         }

         services.add(service);
         ++i;
      }

      return services;
   }

   private String resolveVariables(String stringWithVariables) {
      String resolvedData = stringWithVariables.replace("${ID}", this.login.getID());
      resolvedData = resolvedData.replace("${COURSE}", this.login.getCourse());
      resolvedData = resolvedData.replace("${HOST}", ClientShared.DIRECTORY_HOST);
      resolvedData = resolvedData.replace("${PORT}", ClientShared.PORT);
      int var10002 = this.login.getServletProcessor().getPort();
      resolvedData = resolvedData.replace("${LOCAL_PORT}", "" + var10002);
      String var15 = this.login.getServletProcessor().getHost();
      resolvedData = resolvedData.replace("${LOCAL_HOST}", "" + var15);
      resolvedData = resolvedData.replace("${ACTIVITY}", this.login.getActivity());
      resolvedData = resolvedData.replace("${NAME}", this.login.getName().replace(" ", "%20"));
      resolvedData = resolvedData.replace("${IP_ADDRESS}", this.login.getHardwareAndSoftwareMonitor().getIPv4Address());
      String wanAddress = this.login.getProperty("LOCAL_ADDRESS");
      resolvedData = resolvedData.replace("${WAN_ADDRESS}", wanAddress != null ? wanAddress : "?");
      String password = this.properties.getProperty("student.directory.PASSWORD");
      if (password == null) {
         password = this.properties.getProperty("student.directory." + "PASSWORD".toLowerCase());
      }

      resolvedData = resolvedData.replace("${PASSWORD}", password);
      return resolvedData;
   }
}
