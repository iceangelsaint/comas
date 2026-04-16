package se.unlogic.eagledns.plugins;

import java.io.File;
import java.util.Timer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import se.unlogic.standardutils.numbers.NumberUtils;
import se.unlogic.standardutils.timer.RunnableTimerTask;
import se.unlogic.standardutils.xml.XMLParser;
import se.unlogic.standardutils.xml.XMLUtils;

public class QueryStatsPlugin extends BasePlugin implements Runnable {
   protected long savingInterval = 60L;
   protected String filePath;
   protected File file;
   protected long tcpOffset;
   protected long udpOffset;
   protected long tcpRejectedOffset;
   protected long udpRejectedOffset;
   protected Timer timer;

   public void init(String name) throws Exception {
      super.init(name);
      if (this.filePath == null) {
         throw new RuntimeException("No file set!");
      } else {
         this.file = new File(this.filePath);
         if (this.file.exists()) {
            this.log.info("Plugin " + name + " reading previously saved statistics from file " + this.file.getAbsolutePath());

            try {
               XMLParser settingNode = new XMLParser(this.file);
               this.tcpOffset = settingNode.getLong("/Statistics/TCPQueryCount");
               this.udpOffset = settingNode.getLong("/Statistics/UDPQueryCount");
               this.tcpRejectedOffset = settingNode.getLong("/Statistics/RejectedTCPConnections");
               this.udpRejectedOffset = settingNode.getLong("/Statistics/RejectedUDPConnections");
            } catch (Exception e) {
               this.log.error("Error reading previously saved statistics from file " + this.file.getAbsolutePath(), e);
            }
         } else {
            this.log.info("Plugin " + name + " found no previously saved statistics,, creating new file on first save");
         }

         this.timer = new Timer(true);
         this.timer.schedule(new RunnableTimerTask(this), this.savingInterval * 1000L, this.savingInterval * 1000L);
      }
   }

   public void shutdown() throws Exception {
      this.timer.cancel();
      this.run();
      super.shutdown();
   }

   public void setSavingInterval(String savingInterval) {
      Long interval = NumberUtils.toLong(savingInterval);
      if (interval != null && interval >= 1L) {
         this.savingInterval = interval;
      } else {
         this.log.error("Invalid saving interval value " + savingInterval + " specified, falling back to default value of " + this.savingInterval + " sec");
      }

   }

   public void run() {
      try {
         Document doc = XMLUtils.createDomDocument();
         Element statisticsElement = doc.createElement("Statistics");
         doc.appendChild(statisticsElement);
         XMLUtils.appendNewElement(doc, statisticsElement, "TCPQueryCount", this.tcpOffset + this.systemInterface.getCompletedTCPQueryCount());
         XMLUtils.appendNewElement(doc, statisticsElement, "UDPQueryCount", this.udpOffset + this.systemInterface.getCompletedUDPQueryCount());
         XMLUtils.appendNewElement(doc, statisticsElement, "RejectedTCPConnections", this.tcpRejectedOffset + this.systemInterface.getRejectedTCPConnections());
         XMLUtils.appendNewElement(doc, statisticsElement, "RejectedUDPConnections", this.udpRejectedOffset + this.systemInterface.getRejectedUDPConnections());
         XMLUtils.writeXMLFile(doc, this.file, true, "UTF-8");
         this.log.debug("Plugin " + this.name + " successfully saved query statistics to file " + this.file.getAbsolutePath());
      } catch (Throwable t) {
         this.log.error("Plugin " + this.name + " unable to save query statistics to file " + this.file.getAbsolutePath(), t);
      }

   }

   public void setFilePath(String filePath) {
      this.filePath = filePath;
   }
}
