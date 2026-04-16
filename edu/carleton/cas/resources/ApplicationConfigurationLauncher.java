package edu.carleton.cas.resources;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.utility.Named;
import edu.carleton.cas.utility.Password;
import java.io.File;
import java.util.ArrayList;
import java.util.Properties;

public class ApplicationConfigurationLauncher extends Thread {
   private final Properties properties;
   private final Invigilator invigilator;

   public ApplicationConfigurationLauncher(Invigilator invigilator) {
      this(invigilator.getProperties(), invigilator);
   }

   public ApplicationConfigurationLauncher(Properties properties, Invigilator invigilator) {
      this.properties = properties;
      this.invigilator = invigilator;
      this.setName("application configuration");
   }

   public void run() {
      if (!this.invigilator.isInEndingState()) {
         this.invigilator.getServletProcessor().waitForService();

         for(String[] service : this.servicesToRun()) {
            Thread thread = new Thread(new CommandRunner(service));
            thread.setUncaughtExceptionHandler(this.invigilator);
            thread.start();
         }

      }
   }

   public void close() {
   }

   private ArrayList servicesToRun() {
      ArrayList<String[]> services = new ArrayList();
      String baseProperty = "service." + ClientShared.getOSString() + ".load.";
      int i = 1;

      for(String serviceString = this.properties.getProperty(baseProperty + i); serviceString != null; serviceString = this.properties.getProperty(baseProperty + i)) {
         String serviceToBeLoaded = serviceString.trim();
         String service = this.resolveVariables(serviceToBeLoaded);
         services.add(service.split(","));
         ++i;
      }

      return services;
   }

   private String resolveVariables(String stringWithVariables) {
      String resolved = stringWithVariables.replace("${/}", File.separator);
      resolved = resolved.replace("${HOME}", System.getProperty("user.home"));
      resolved = resolved.replace("${TIME}", String.format("%d", System.currentTimeMillis()));
      resolved = resolved.replace("${TEMP}", System.getProperty("java.io.tmpdir"));
      resolved = resolved.replace("${HOST}", ClientShared.DIRECTORY_HOST);
      resolved = resolved.replace("${PORT}", ClientShared.PORT);
      int var10002 = this.invigilator.getServletProcessor().getPort();
      resolved = resolved.replace("${LOCAL_PORT}", "" + var10002);
      String var22 = this.invigilator.getServletProcessor().getHost();
      resolved = resolved.replace("${LOCAL_HOST}", "" + var22);
      resolved = resolved.replace("${RANDOM}", Password.getPassCode());
      resolved = resolved.replace("${ID}", this.invigilator.getID());
      resolved = resolved.replace("${COURSE}", this.invigilator.getCourse());
      resolved = resolved.replace("${ACTIVITY}", this.invigilator.getActivity());
      resolved = resolved.replace("${NAME}", Named.canonical(this.invigilator.getName()));
      resolved = resolved.replace("${FOLDER}", ClientShared.DIR);
      resolved = resolved.replace("${DRIVE}", ClientShared.isWindowsOS() ? ClientShared.DIR_DRIVE : "");
      resolved = resolved.replace("${IP_ADDRESS}", this.invigilator.getHardwareAndSoftwareMonitor().getIPv4Address());
      String wanAddress = this.invigilator.getProperty("LOCAL_ADDRESS");
      resolved = resolved.replace("${WAN_ADDRESS}", wanAddress != null ? wanAddress : "?");
      resolved = resolved.replace("${MACHINE}", this.invigilator.getHardwareAndSoftwareMonitor().getComputerIdentifier());
      resolved = resolved.replace("${PASSWORD}", this.properties.getProperty("student.directory.PASSWORD", ""));
      return resolved;
   }

   public static void main(String[] args) {
      CommandRunner cr = new CommandRunner(new String[]{"open", "-a", "Visual Studio Code", "/Users/tonywhite/Desktop/CoMaS"});
      cr.run();
   }
}
