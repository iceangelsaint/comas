package edu.carleton.cas.resources;

import edu.carleton.cas.background.timers.ExtendedTimer;
import edu.carleton.cas.background.timers.TimerService;
import edu.carleton.cas.constants.ClientShared;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;

public class ResourceMonitor extends AbstractResourceMonitor {
   ExtendedTimer timer;
   final boolean osIsWindows;
   final String name;
   Logger logger;
   AbstractTask task;
   final Properties properties;
   public final String activityFolder;

   public ResourceMonitor(String name, String type, Properties properties) {
      super(type);
      this.name = name;
      this.timer = TimerService.create(type);
      this.properties = properties;
      this.activityFolder = null;
      this.osIsWindows = ClientShared.isWindowsOS();
   }

   public ResourceMonitor(String name, String type, String activityDirectoryName, Properties properties) {
      super(type);
      this.properties = properties;
      this.name = name;
      this.activityFolder = activityDirectoryName;
      this.osIsWindows = ClientShared.isWindowsOS();
      this.timer = TimerService.create(type);

      try {
         this.logger = new Logger();
         this.logger.open(new File(activityDirectoryName + File.separator + "logs" + File.separator + name));
      } catch (Exception var6) {
         this.logger = null;
      }

   }

   public AbstractTask getTask() {
      return this.task;
   }

   public void open() {
      this.task = null;
      if (this.name.startsWith("network") && ClientShared.NETWORK_MONITORING) {
         if (this.osIsWindows) {
            this.task = new WindowsNetworkTask(this.logger, this);
         } else {
            this.task = new UnixNetworkTask(this.logger, this);
         }

         edu.carleton.cas.logging.Logger.log(Level.CONFIG, "Network monitoring is enabled", "");
      }

      if (this.name.startsWith("file") && ClientShared.FILE_MONITORING) {
         if (this.osIsWindows) {
            this.task = new WindowsFileTask(this.logger, this);
         } else {
            this.task = new UnixFileTask(this.logger, this);
         }

         edu.carleton.cas.logging.Logger.log(Level.CONFIG, "File monitoring is enabled", "");
      }

      if (this.task != null) {
         this.timer.scheduleAtFixedRate(this.task, 10000L, 60000L);
      }

   }

   public boolean okToClose() {
      if (this.task instanceof AbstractFileTask) {
         AbstractFileTask at = (AbstractFileTask)this.task;
         return at.processesAccessingFolderOfInterest().length == 0;
      } else {
         return true;
      }
   }

   public String[] processesAccessingFolderOfInterest() {
      if (this.task instanceof AbstractFileTask) {
         AbstractFileTask at = (AbstractFileTask)this.task;
         return at.processesAccessingFolderOfInterest();
      } else {
         return new String[0];
      }
   }

   public String[] checkProcessesAccessingFolderOfInterest() {
      if (this.task instanceof AbstractFileTask) {
         AbstractFileTask at = (AbstractFileTask)this.task;
         return at.checkProcessesAccessingFolderOfInterest();
      } else {
         return new String[0];
      }
   }

   public void close() {
      try {
         if (this.logger != null) {
            this.logger.close();
         }
      } catch (IOException var4) {
      }

      try {
         if (this.task != null) {
            this.task.cancel();
            this.task.close();
         }
      } catch (Exception var3) {
      }

      try {
         TimerService.destroy(this.timer);
      } catch (Exception var2) {
      }

   }

   public String getProperty(String name) {
      return this.properties.getProperty(name);
   }

   public String getProperty(String name, String defaultValue) {
      return this.properties.getProperty(name, defaultValue);
   }
}
