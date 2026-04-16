package com.cogerent.launcher;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.jetty.embedded.ProgressServlet;
import edu.carleton.cas.jetty.embedded.SessionEndTask;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.security.Checksum;
import edu.carleton.cas.security.JarVerifier;
import edu.carleton.cas.ui.WebAlert;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

public final class LauncherChecker {
   private static final String COMAS_LAUNCHER_DOT_JAR_FORMAT = "CoMaS-Launcher-%s.jar";
   private static final int CHECK_PROBLEM = 1;
   private static final int UPGRADE_PROBLEM = 2;
   private static final int UPGRADE_FAILED_PROBLEM = 3;
   private boolean jarIsSigned;
   private Certificate[] certs;
   private final Invigilator invigilator;

   public LauncherChecker(Invigilator invigilator) {
      this.invigilator = invigilator;
   }

   public boolean checkAndUpgradeIfRequired() {
      String workingDir = System.getProperty("user.dir");
      boolean appEndOnFailure = Utils.isTrueOrYes(this.invigilator.getProperty("application.terminate"));
      String clientLauncherHash = this.invigilator.getProperty("application.launcher.hash");
      String clientLauncherUpgradeURL = this.invigilator.getProperty("application.launcher.upgrade.url");
      String clientLauncherVersion = this.invigilator.getProperty("application.launcher.version");
      if (clientLauncherHash != null && clientLauncherVersion != null && clientLauncherUpgradeURL != null) {
         clientLauncherHash = clientLauncherHash.trim();
         clientLauncherVersion = clientLauncherVersion.trim();
         clientLauncherUpgradeURL = clientLauncherUpgradeURL.trim();

         try {
            ClassLoader cl = this.getClass().getClassLoader();

            ClassLoader lcl;
            for(lcl = null; cl != null; cl = cl.getParent()) {
               if (cl instanceof URLClassLoader) {
                  lcl = cl;
               }
            }

            Class<?> lc = Class.forName("edu.carleton.cas.application.LauncherConfigurationFactory", false, lcl);
            Method classMethod = lc.getMethod("getDefault");
            Object o = classMethod.invoke((Object)null);
            if (o != null) {
               Method instanceMethod = o.getClass().getMethod("getStringProperty", String.class);
               Object launcherVersion = instanceMethod.invoke(o, "application.version");
               if (launcherVersion == null) {
                  launcherVersion = instanceMethod.invoke(o, "application.launcher.version");
               }

               if (launcherVersion == null) {
                  launcherVersion = clientLauncherVersion;
               }

               Object launcherHash = instanceMethod.invoke(o, "application.launcher.hash");
               if (launcherHash == null) {
                  launcherHash = clientLauncherHash;
               }

               Object clientPath = instanceMethod.invoke(o, "application.client");
               String actualCheckSumOfLauncherFile = this.checkLauncherHash(clientLauncherVersion, launcherVersion, workingDir, launcherHash, clientLauncherHash);
               Object codeMustBeSignedAsObject = instanceMethod.invoke(o, "application.launcher.signed");
               if (Utils.isTrueOrYes(codeMustBeSignedAsObject)) {
                  if (!this.jarIsSigned) {
                     this.invigilator.logArchiver.put(Level.INFO, "Session terminated due to launcher not being signed");
                     this.endSessionBecauseOfProblem(1);
                  } else {
                     Object certMustBeChecked = instanceMethod.invoke(o, "application.launcher.certificate_validity_check");
                     boolean certificateValidityCheck = Utils.isTrueOrYes(certMustBeChecked);
                     X509Certificate x509Cert = (X509Certificate)this.certs[0];
                     if (certificateValidityCheck) {
                        x509Cert.checkValidity();
                     }

                     this.invigilator.setLauncherCertificate(x509Cert);
                     if (clientPath != null) {
                        File clientFile = new File(clientPath.toString());
                        boolean isSigned = this.isSignedJarFile(clientFile);
                        if (isSigned) {
                           x509Cert = (X509Certificate)this.certs[0];
                           if (certificateValidityCheck) {
                              x509Cert.checkValidity();
                           }

                           this.invigilator.setClientCertificate(x509Cert);
                        }
                     }
                  }
               }

               this.upgradeLauncher(launcherVersion, clientLauncherUpgradeURL, workingDir, clientLauncherVersion, clientLauncherHash, actualCheckSumOfLauncherFile);
               return true;
            } else {
               if (appEndOnFailure) {
                  this.invigilator.logArchiver.put(Level.INFO, "Session terminated due to launcher factory initialization problem");
                  this.endSessionBecauseOfProblem(1);
               } else {
                  this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Launcher factory was not initialized");
               }

               return true;
            }
         } catch (Exception e) {
            if (appEndOnFailure) {
               this.invigilator.logArchiver.put(Level.INFO, "Session terminated due to exception:\n" + Utils.printException(e));
               this.endSessionBecauseOfProblem(1);
            } else {
               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Launcher hash code check failed:\n" + Utils.printException(e));
            }

            return false;
         }
      } else {
         return true;
      }
   }

   public static void log(String log) {
      System.err.println(log);
   }

   private String checkLauncherHash(String clientVersion, Object launcherVersion, String workingDir, Object launcherHash, String clientHash) throws Exception {
      String checkSum = "";
      String launcherVersionAsString = launcherVersion.toString().trim();
      String jarLoaded = String.format("CoMaS-Launcher-%s.jar", launcherVersionAsString);
      File comasLauncherDotJar = new File(workingDir + File.separator + jarLoaded);
      String launcherHashAsString = launcherHash.toString().trim();
      if (comasLauncherDotJar.exists()) {
         this.jarIsSigned = this.isSignedJarFile(comasLauncherDotJar);
         String prefix;
         if (this.jarIsSigned) {
            prefix = "Signed";
         } else {
            prefix = "Unsigned";
         }

         checkSum = Checksum.getSHA256Checksum(comasLauncherDotJar.getAbsolutePath());
         if (!clientHash.equals(launcherHashAsString)) {
            String msg = String.format("%s launcher hash code disagreement:\n%s\n%s\nWorking folder is %s", prefix, clientHash, launcherHashAsString, workingDir);
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, msg);
         } else if (!launcherHashAsString.equals(checkSum)) {
            String msg = String.format("%s launcher hash code is not okay:\n%s\n%s", prefix, checkSum, launcherHashAsString);
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, msg);
         }
      } else {
         checkSum = "";
         this.jarIsSigned = false;
         this.certs = null;
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, String.format("Cannot find %s", comasLauncherDotJar));
      }

      return checkSum;
   }

   private void upgradeLauncher(Object launcherVersion, String clientLauncherUpgradeURL, String workingDir, String clientLauncherVersion, String clientLauncherHash, String actualCheckSumOfLauncherFile) {
      if (!actualCheckSumOfLauncherFile.equals(clientLauncherHash)) {
         try {
            String jarLoaded = String.format("CoMaS-Launcher-%s.jar", launcherVersion);
            final File comasLauncherDotJar = new File(workingDir + File.separator + jarLoaded);
            String jarRenamed = String.format("CoMaS-Launcher-%s.jar", "old-" + String.valueOf(launcherVersion));
            Files.copy(Paths.get(workingDir, jarLoaded), Paths.get(workingDir, jarRenamed), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            URL url = new URL(ClientShared.BASE_CMS + clientLauncherUpgradeURL);
            final File newLauncherJar = Utils.getAndStoreURL(url, new File(workingDir), "Cookie", "token=" + clientLauncherVersion);
            if (newLauncherJar.exists()) {
               String checkSum = Checksum.getSHA256Checksum(newLauncherJar.getAbsolutePath());
               if (checkSum.equals(clientLauncherHash)) {
                  this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Upgrade performed using " + url.getFile());
                  Thread hook = new Thread(new Runnable() {
                     public void run() {
                        if (!comasLauncherDotJar.getName().equals(newLauncherJar.getName())) {
                           comasLauncherDotJar.delete();
                           newLauncherJar.renameTo(comasLauncherDotJar);
                        }

                     }
                  });
                  Runtime.getRuntime().addShutdownHook(hook);
                  this.endSessionBecauseOfProblem(2);
                  this.invigilator.setProperty("session." + String.valueOf(ClientShared.getOS()) + ".halt", "true");
                  this.invigilator.halt(ClientShared.THIRTY_SECONDS_IN_MSECS);
               }
            } else {
               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Upgrade jar could not be found");
            }
         } catch (Exception e) {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Upgrade jar could not be downloaded: " + String.valueOf(e));
            this.endSessionBecauseOfProblem(3);
         }
      }

   }

   private void endSessionBecauseOfProblem(int problemState) {
      String msg = "";
      if (problemState == 1) {
         msg = "There is a problem with the code signature of your application.\n\nACTION: Please reinstall the application.";
      } else if (problemState == 2) {
         msg = "CoMaS is being upgraded. This may take 1 minute.\nYour session will now end.\n\nACTION: Please restart session with the new version.";
      } else if (problemState == 3) {
         msg = "CoMaS needs to be upgraded.\n\nACTION: You must run CoMaS using \"Run as administrator\" to do this.";
      }

      this.invigilator.setInvigilatorState(InvigilatorState.ending);
      Thread t = new Thread(new SessionEndTask(this.invigilator, ProgressServlet.getSingleton(), "Session ended due to launcher upgrade"));
      t.start();
      this.invigilator.setEndOfSessionStateFlags();
      this.invigilator.setState("Terminated");
      WebAlert.errorDialog(msg);
   }

   private boolean isSignedJarFile(File f) {
      JarVerifier jv = new JarVerifier(f);
      boolean rtn = jv.checkJarEntries();
      this.certs = jv.getCertificates();
      return rtn;
   }
}
