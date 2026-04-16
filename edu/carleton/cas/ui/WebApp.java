package edu.carleton.cas.ui;

import com.cogerent.launcher.LauncherChecker;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.jetty.embedded.EmailLoginServlet;
import edu.carleton.cas.jetty.embedded.EndServlet;
import edu.carleton.cas.jetty.embedded.EndedServlet;
import edu.carleton.cas.jetty.embedded.HostServlet;
import edu.carleton.cas.jetty.embedded.LoginServlet;
import edu.carleton.cas.jetty.embedded.PingServlet;
import edu.carleton.cas.jetty.embedded.ProgressServlet;
import edu.carleton.cas.jetty.embedded.QuitServlet;
import edu.carleton.cas.jetty.embedded.RedirectServlet;
import edu.carleton.cas.jetty.embedded.ServletProcessor;
import edu.carleton.cas.jetty.embedded.TerminateServlet;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.resources.WebAppPageLauncher;
import edu.carleton.cas.utility.ClientConfiguration;
import edu.carleton.cas.utility.Sleeper;
import java.awt.Desktop;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class WebApp implements Runnable {
   String[] args;
   Invigilator invigilator;
   String startServlet;

   public WebApp(String[] args, String startServlet) {
      this.args = args;
      this.startServlet = startServlet;
   }

   public static void main(String[] args) throws Exception {
      WebApp wa = new WebApp(args, LoginServlet.getMapping());
      wa.run();
   }

   public static void main1(String[] args) throws Exception {
      WebApp wa = new WebApp(args, EmailLoginServlet.getMapping());
      wa.run();
   }

   public void run() {
      ClientShared.SERVER_CHOSEN = null;
      ClientShared.processCommandLineArgs(this.args);
      ClientConfiguration clientConfiguration = new ClientConfiguration(ClientShared.COMAS_DIRECTORY + File.separator + ClientShared.COMAS_DOT_INI);
      clientConfiguration.load();
      clientConfiguration.sanitize();
      ClientShared.deleteOldVersions();
      if (ClientShared.SERVER_CHOSEN == null) {
         ServerChooser server = new ServerChooser(WebAlert.refreshAlwaysOnTopFrame(), clientConfiguration);
         ClientShared.SERVER_CHOSEN = server.select();
         if (ClientShared.SERVER_CHOSEN == null) {
            System.exit(0);
         }
      }

      if (clientConfiguration.getCheckConnection() && !Utils.isReachable(ClientShared.SERVER_CHOSEN)) {
         WebAlert.errorDialog("The server " + ClientShared.SERVER_CHOSEN + " is unavailable");
         System.exit(-1);
      }

      DisappearingAlert.Alert alert = null;

      try {
         Desktop.getDesktop().browse(clientConfiguration.getStartupScreenURI());
      } catch (IOException | URISyntaxException | UnsupportedOperationException | NullPointerException var27) {
         DisappearingAlert da = new DisappearingAlert();
         alert = da.show("Starting " + ClientShared.SERVER_CHOSEN + " client, please wait ...");
      }

      this.obtainSystemWebResourcesValues(clientConfiguration);
      this.invigilator = new Invigilator(clientConfiguration);
      if (this.invigilator.getServletProcessor() == null) {
         WebAlert.errorDialog("CoMaS is already running");
         System.exit(-1);
      }

      long comasPID = ProcessHandle.current().pid();
      this.killOldSessions(comasPID);
      int port = clientConfiguration.getPort();

      try {
         int myPort = this.invigilator.getServletProcessor().getPort();
         if (port == myPort) {
            LauncherChecker.log(String.format("Previous session port is the same as this session: %d", myPort));
         } else if (port > 0 && Utils.isReachable("localhost", port, 1000)) {
            LauncherChecker.log(String.format("Server on port %d found", port));
            Utils.getURL("http://localhost:" + port + "/terminate", 2000);
         }
      } catch (Exception var26) {
      }

      try {
         long pid = clientConfiguration.getPID();
         if (pid > 0L && pid != comasPID && Utils.destroyPIDForcibly(pid) == pid) {
            LauncherChecker.log(String.format("CoMaS session (PID %d) is already running", pid));
         }
      } catch (Exception var24) {
      } finally {
         Thread hook = new Thread() {
            public void run() {
               ClientConfiguration cc = WebApp.this.invigilator.getClientConfiguration();
               cc.setPID(-1L);
               cc.save();
            }
         };
         Runtime.getRuntime().addShutdownHook(hook);
      }

      clientConfiguration.setPID(comasPID);
      clientConfiguration.save();
      WebDialog webDialog = new WebDialog("CoMaS", this.invigilator);
      this.invigilator.addObserver(webDialog);
      ServletProcessor sp = this.invigilator.getServletProcessor();

      while(!sp.isRunning()) {
         Sleeper.sleep(1000);
      }

      TerminateServlet ts = new TerminateServlet(this.invigilator);
      sp.addServlet(ts, "/terminate");
      LoginServlet ls = new LoginServlet(this.invigilator);
      ls.addServletHandler();
      EmailLoginServlet els = new EmailLoginServlet(this.invigilator);
      els.addServletHandler();
      EndServlet es = new EndServlet(this.invigilator);
      es.addServletHandler();
      EndedServlet ens = new EndedServlet(this.invigilator);
      ens.addServletHandler();
      QuitServlet qs = new QuitServlet(this.invigilator, "Your session is ending");
      sp.addServlet(qs, "/quit");
      RedirectServlet rs = new RedirectServlet(this.invigilator);
      sp.addServlet(rs, "/");
      ProgressServlet ps = new ProgressServlet(this.invigilator);
      ps.addServletHandler();
      PingServlet ping = new PingServlet(this.invigilator);
      sp.addServlet(ping, "/ping");
      HostServlet hs = new HostServlet(this.invigilator);
      hs.addServletHandler();
      this.invigilator.getHardwareAndSoftwareMonitor().addServletHandlers();
      this.invigilator.setInvigilatorState(InvigilatorState.loggingIn);
      String startPage = sp.getService(this.startServlet);
      WebAppPageLauncher wapl = new WebAppPageLauncher(startPage, this.invigilator);
      wapl.start();
      sp.setupCloseScenarioDetection();
      clientConfiguration.setProperty("session.store.web_server.port", "" + sp.getPort());
      System.out.println("session.store.web_server=" + sp.getService());
      clientConfiguration.save();
      if (alert != null) {
         alert.close();
      }

   }

   private void killOldSessions(long myPID) {
      final String prefix = ClientShared.COMAS_DOT + "pid";
      Path pidFilePath = Paths.get(ClientShared.DIR, prefix + myPID);

      try {
         Files.writeString(pidFilePath, Long.toString(myPID));
         File currentDirectory = new File(ClientShared.DIR);
         final File[] oldSessionFiles = currentDirectory.listFiles(new FileFilter() {
            public boolean accept(File f) {
               return f.getName().startsWith(prefix);
            }
         });
         if (oldSessionFiles != null) {
            for(File sessionFile : oldSessionFiles) {
               String pidAsString = sessionFile.getName().substring(prefix.length());

               try {
                  long pid = Long.parseUnsignedLong(pidAsString);
                  if (pid != myPID && Utils.destroyPIDForcibly(pid) == pid) {
                     sessionFile.delete();
                  }
               } catch (NumberFormatException var19) {
               }
            }

            Thread hook = new Thread() {
               public void run() {
                  File[] var4;
                  for(File sessionFile : var4 = oldSessionFiles) {
                     if (sessionFile.exists()) {
                        sessionFile.delete();
                     }
                  }

               }
            };
            Runtime.getRuntime().addShutdownHook(hook);
         }
      } catch (IOException var20) {
      } finally {
         File pidFile = pidFilePath.toFile();
         pidFile.deleteOnExit();
      }

   }

   private void obtainSystemWebResourcesValues(ClientConfiguration clientConfiguration) {
      String name = SystemWebResources.getVariable("name");
      String id = SystemWebResources.getVariable("id");
      String passcode = SystemWebResources.getVariable("passcode");
      String entity = SystemWebResources.getVariable("entity");
      String[] tokens = ClientShared.SERVER_CHOSEN.split(":");
      if (tokens.length == 1) {
         ClientShared.CMS_HOST = tokens[0].trim();
      } else if (tokens.length == 2) {
         ClientShared.CMS_HOST = tokens[0].trim();
         ClientShared.PORT = tokens[1].trim();
      }

      if (clientConfiguration.getAgreedToMonitor() == 0L) {
         ClientShared.BASE_CMS = ClientShared.service(ClientShared.PROTOCOL, ClientShared.CMS_HOST, ClientShared.PORT, "/CMS/rest/");
         ClientShared.SYSTEM_LOGIN_CONFIGURATION_URL = ClientShared.BASE_CMS + "exam/login.ini";
         Properties configs = Utils.getProperties(ClientShared.SYSTEM_LOGIN_CONFIGURATION_URL, "Cookie", "token=0.7.15");
         configs.forEach((k, v) -> {
            String key = ((String)k).trim();
            String value = ((String)v).trim();
            if (key.equalsIgnoreCase(name) || key.equalsIgnoreCase(id) || key.equalsIgnoreCase(passcode) || key.equalsIgnoreCase(entity)) {
               SystemWebResources.setResource(key, value);
               clientConfiguration.setProperty(key, value);
            }

         });
      } else {
         Properties configs = clientConfiguration.getConfiguration();
         SystemWebResources.setLocalResource("name", configs.getProperty(name));
         SystemWebResources.setLocalResource("id", configs.getProperty(id));
         SystemWebResources.setLocalResource("passcode", configs.getProperty(passcode));
         SystemWebResources.setLocalResource("entity", configs.getProperty(entity));
      }

   }
}
