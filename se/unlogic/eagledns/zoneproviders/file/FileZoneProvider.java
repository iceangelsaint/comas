package se.unlogic.eagledns.zoneproviders.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import org.apache.log4j.Logger;
import org.xbill.DNS.Name;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Zone;
import se.unlogic.eagledns.SecondaryZone;
import se.unlogic.eagledns.SystemInterface;
import se.unlogic.eagledns.ZoneChangeCallback;
import se.unlogic.eagledns.ZoneProviderUpdatable;
import se.unlogic.eagledns.zoneproviders.ZoneProvider;
import se.unlogic.standardutils.numbers.NumberUtils;
import se.unlogic.standardutils.timer.RunnableTimerTask;

public class FileZoneProvider implements ZoneProvider, ZoneProviderUpdatable, Runnable {
   private final Logger log = Logger.getLogger(this.getClass());
   private String name;
   private String zoneFileDirectory;
   private boolean autoReloadZones;
   private Integer pollingInterval;
   private Map lastFileList = new HashMap();
   private ZoneChangeCallback changeCallback;
   private Timer watcher;

   public void init(String name) {
      this.name = name;
      if (this.autoReloadZones && this.pollingInterval != null) {
         this.watcher = new Timer(true);
         this.watcher.schedule(new RunnableTimerTask(this), 5000L, (long)(this.pollingInterval * 1000));
      }

   }

   public void run() {
      if (this.changeCallback != null && this.hasDirectoryChanged()) {
         this.log.info("Changes in directory " + this.zoneFileDirectory + " detected");
         this.changeCallback.zoneDataChanged();
      }

   }

   private boolean hasDirectoryChanged() {
      File folder = new File(this.zoneFileDirectory);
      File[] files = folder.listFiles();
      if (files.length != this.lastFileList.size()) {
         return true;
      } else {
         File[] var6;
         for(File f : var6 = folder.listFiles()) {
            if (!this.lastFileList.containsKey(f.getName())) {
               return true;
            }

            if (f.lastModified() > (Long)this.lastFileList.get(f.getName())) {
               return true;
            }
         }

         return false;
      }
   }

   private void updateZoneFiles(File[] files) {
      this.lastFileList = new HashMap();

      for(File f : files) {
         this.lastFileList.put(f.getName(), f.lastModified());
      }

   }

   public Collection getPrimaryZones() {
      File zoneDir = new File(this.zoneFileDirectory);
      if (zoneDir.exists() && zoneDir.isDirectory()) {
         if (!zoneDir.canRead()) {
            this.log.error("Zone file directory specified for FileZoneProvider " + this.name + " is not readable!");
            return null;
         } else {
            File[] files = zoneDir.listFiles();
            this.updateZoneFiles(files);
            if (files != null && files.length != 0) {
               ArrayList<Zone> zones = new ArrayList(files.length);

               for(File zoneFile : files) {
                  if (!zoneFile.canRead()) {
                     this.log.error("FileZoneProvider " + this.name + " unable to access zone file " + zoneFile);
                  } else {
                     try {
                        Name origin = Name.fromString(zoneFile.getName(), Name.root);
                        Zone zone = new Zone(origin, zoneFile.getPath());
                        this.log.debug("FileZoneProvider " + this.name + " successfully parsed zone file " + zoneFile.getName());
                        zones.add(zone);
                     } catch (TextParseException e) {
                        this.log.error("FileZoneProvider " + this.name + " unable to parse zone file " + zoneFile.getName(), e);
                     } catch (IOException e) {
                        this.log.error("Unable to parse zone file " + zoneFile + " in FileZoneProvider " + this.name, e);
                     }
                  }
               }

               if (!zones.isEmpty()) {
                  return zones;
               } else {
                  return null;
               }
            } else {
               this.log.info("No zone files found for FileZoneProvider " + this.name + " in directory " + zoneDir.getPath());
               return null;
            }
         }
      } else {
         this.log.error("Zone file directory specified for FileZoneProvider " + this.name + " does not exist!");
         return null;
      }
   }

   public void shutdown() {
   }

   public String getZoneFileDirectory() {
      return this.zoneFileDirectory;
   }

   public void setZoneFileDirectory(String zoneFileDirectory) {
      this.zoneFileDirectory = zoneFileDirectory;
      this.log.debug("zoneFileDirectory set to " + zoneFileDirectory);
   }

   public Collection getSecondaryZones() {
      return null;
   }

   public void zoneUpdated(SecondaryZone secondaryZone) {
   }

   public void zoneChecked(SecondaryZone secondaryZone) {
   }

   public void setChangeListener(ZoneChangeCallback ev) {
      this.changeCallback = ev;
   }

   public void setAutoReloadZones(String autoReloadZones) {
      this.autoReloadZones = Boolean.parseBoolean(autoReloadZones);
   }

   public void setPollingInterval(String pollingInterval) {
      Integer value = NumberUtils.toInt(pollingInterval);
      if (value != null && value > 0) {
         this.pollingInterval = value;
      } else {
         this.log.warn("Invalid polling interval specified: " + pollingInterval);
      }

   }

   public void setSystemInterface(SystemInterface systemInterface) {
   }
}
