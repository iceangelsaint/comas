package edu.carleton.cas.resources;

import com.cogerent.detector.DisplayDetectorPro;
import com.cogerent.detector.DisplayInfo;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.constants.ClientShared.OS;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.jetty.embedded.EmbeddedServlet;
import edu.carleton.cas.jetty.embedded.ServletProcessor;
import edu.carleton.cas.jetty.embedded.ServletRouter;
import edu.carleton.cas.jetty.embedded.WebcamServlet;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.messaging.MessageHandler;
import edu.carleton.cas.messaging.handlers.AlertMessageHandler;
import edu.carleton.cas.reporting.ReportManager;
import edu.carleton.cas.utility.DetectVM;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.Display;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.PowerSource;
import oshi.software.os.ApplicationInfo;
import oshi.software.os.FileSystem;
import oshi.software.os.NetworkParams;
import oshi.software.os.OSDesktopWindow;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

public class HardwareAndSoftwareMonitor implements Configurable {
   private static final String IP_ADDRESS_SEPARATOR = "; ";
   private static int DEFAULT_MEMORY_THRESHOLD = 6;
   private static int MEMORY_THRESHOLD;
   private static int AVAILABLE_MEMORY_THRESHOLD;
   public static long POWER_EXHAUSTION_THRESHOLD_IN_MINUTES;
   public static long THIRTY_MINUTES_IN_MSECS;
   private final SystemInfo systemInfo;
   private final OperatingSystem operatingSystem;
   private final HardwareAbstractionLayer hardwareAbstractionLayer;
   private final CentralProcessor centralProcessor;
   private final ComputerSystem computerSystem;
   private final GlobalMemory globalMemory;
   private List applicationList;
   private boolean volumesCheckPerformed;
   private final ArrayList allowedVolumes;
   private boolean processCheckPerformed;
   private boolean windowCheckPerformed;
   private boolean installedApplicationsCheckPerformed;
   private boolean browserHistoryCheckPerformed;
   private final ArrayList allowedProcesses;
   private final ArrayList deniedProcesses;
   private final ArrayList initializeProcesses;
   private final ArrayList finalizeProcesses;
   private final ArrayList installedApplications;
   private final ArrayList browserHistory;
   private final ArrayList browserHistoryAllowed;
   private final BrowserHistoryReader browserHistoryReader;
   private final ArrayList deniedWindows;
   private final ArrayList allowedWindows;
   private boolean windowVisible;
   private NetworkIFStatistics[] nifStatistics;
   private final Invigilator invigilator;

   static {
      MEMORY_THRESHOLD = DEFAULT_MEMORY_THRESHOLD;
      AVAILABLE_MEMORY_THRESHOLD = 1;
      POWER_EXHAUSTION_THRESHOLD_IN_MINUTES = 30L;
      THIRTY_MINUTES_IN_MSECS = 1800000L;
   }

   public HardwareAndSoftwareMonitor(Invigilator invigilator) {
      this.invigilator = invigilator;
      this.systemInfo = new SystemInfo();
      this.operatingSystem = this.systemInfo.getOperatingSystem();
      this.hardwareAbstractionLayer = this.systemInfo.getHardware();
      this.centralProcessor = this.hardwareAbstractionLayer.getProcessor();
      this.computerSystem = this.hardwareAbstractionLayer.getComputerSystem();
      this.globalMemory = this.hardwareAbstractionLayer.getMemory();
      this.volumesCheckPerformed = false;
      this.allowedVolumes = new ArrayList();
      this.processCheckPerformed = false;
      this.windowCheckPerformed = false;
      this.allowedProcesses = new ArrayList();
      this.deniedProcesses = new ArrayList();
      this.initializeProcesses = new ArrayList();
      this.finalizeProcesses = new ArrayList();
      this.deniedWindows = new ArrayList();
      this.allowedWindows = new ArrayList();
      this.installedApplications = new ArrayList();
      this.browserHistory = new ArrayList();
      this.browserHistoryAllowed = new ArrayList();
      this.browserHistoryReader = new BrowserHistoryReader(0L);
      HardwareAndSoftwareMonitor.OSProcessAction.setDefaultInvigilator(invigilator);
   }

   public BrowserHistoryReader getBrowserHistoryReader() {
      return this.browserHistoryReader;
   }

   public String getComputerIdentifier() {
      String vendor = this.operatingSystem.getManufacturer();
      String processorSerialNumber = this.computerSystem.getSerialNumber();
      String uuid = this.computerSystem.getHardwareUUID();
      String processorIdentifier = this.centralProcessor.getProcessorIdentifier().getIdentifier();
      int processors = this.centralProcessor.getLogicalProcessorCount();
      String delimiter = "-";
      String var10000 = String.format("%08x", vendor.hashCode());
      return var10000 + delimiter + String.format("%08x", processorSerialNumber.hashCode()) + delimiter + String.format("%08x", uuid.hashCode()) + delimiter + String.format("%08x", processorIdentifier.hashCode()) + delimiter + processors;
   }

   public String getMicroarchitecture() {
      return this.centralProcessor.getProcessorIdentifier().getMicroarchitecture();
   }

   public String getOSVersion() {
      return this.operatingSystem.getVersionInfo().getVersion();
   }

   public String getOS() {
      return this.operatingSystem.getVersionInfo().toString();
   }

   public String getVendor() {
      return this.operatingSystem.getManufacturer();
   }

   public String getProcessorSerialNumber() {
      return this.computerSystem.getSerialNumber();
   }

   public String getUUID() {
      return this.computerSystem.getHardwareUUID();
   }

   public String getProcessorIdentifier() {
      return this.centralProcessor.getProcessorIdentifier().getIdentifier();
   }

   public int getProcessorCount() {
      return this.centralProcessor.getLogicalProcessorCount();
   }

   public float getTotalMemory() {
      return (float)this.globalMemory.getTotal() / 1.0737418E9F;
   }

   public float getAvailableMemory() {
      return (float)this.globalMemory.getAvailable() / 1.0737418E9F;
   }

   public List getPowerSources() {
      return this.hardwareAbstractionLayer.getPowerSources();
   }

   public List getDisplays() {
      return this.hardwareAbstractionLayer.getDisplays();
   }

   public List getGraphicsCards() {
      return this.hardwareAbstractionLayer.getGraphicsCards();
   }

   public String displays() {
      List<Display> displays = this.invigilator.getHardwareAndSoftwareMonitor().getDisplays();
      if (displays != null && !displays.isEmpty()) {
         boolean first_d = true;
         StringBuffer d_s = new StringBuffer("Displays: ");

         for(Display d : displays) {
            if (first_d) {
               first_d = false;
            } else {
               d_s.append(", ");
            }

            DisplayInfo di = DisplayDetectorPro.parseEDID(d.getEdid());
            d_s.append(di);
         }

         return d_s.toString();
      } else {
         return "";
      }
   }

   public String graphicsCards() {
      List<GraphicsCard> gpus = this.invigilator.getHardwareAndSoftwareMonitor().getGraphicsCards();
      if (gpus != null && !gpus.isEmpty()) {
         boolean first_gpu = true;
         StringBuffer gpu_s = new StringBuffer("Graphics cards: ");

         for(GraphicsCard gpu : gpus) {
            if (first_gpu) {
               first_gpu = false;
            } else {
               gpu_s.append(", ");
            }

            gpu_s.append(gpu.getVendor());
            gpu_s.append("-");
            gpu_s.append(gpu.getName());
            gpu_s.append("-");
            gpu_s.append(gpu.getDeviceId());
         }

         return gpu_s.toString();
      } else {
         return "";
      }
   }

   public long getTimeRemainingEstimate() {
      long max = 0L;

      for(PowerSource ps : this.getPowerSources()) {
         if (ps.isPowerOnLine()) {
            return Long.MAX_VALUE;
         }

         long estimate = this.getTimeRemainingEstimate(ps);
         if (estimate > max) {
            max = estimate;
         }
      }

      return max;
   }

   public long getCapacityRemainingEstimate() {
      double max = (double)0.0F;

      for(PowerSource ps : this.getPowerSources()) {
         if (ps.isPowerOnLine()) {
            return 100L;
         }

         double estimate = ps.getRemainingCapacityPercent();
         if (estimate > max) {
            max = estimate;
         }
      }

      return Math.round(max * (double)100.0F);
   }

   public long getTimeRemainingEstimate(PowerSource ps) {
      double tre = ps.getTimeRemainingEstimated();
      return tre > (double)0.0F ? Math.round(Math.min(tre, ps.getTimeRemainingInstant()) / (double)60.0F) : Math.round(ps.getTimeRemainingInstant() / (double)60.0F);
   }

   public NetworkParams getNetworkParams() {
      return (NetworkParams)(this.getVendor().equals("GNU/Linux") ? new UbuntuNetworkParams() : this.operatingSystem.getNetworkParams());
   }

   public String getNetworkingSummary() {
      return this.buildParamsText();
   }

   private String buildParamsText() {
      NetworkParams params = this.getNetworkParams();
      StringBuilder sb = (new StringBuilder("Host Name: ")).append(params.getHostName());
      if (!params.getDomainName().isEmpty()) {
         sb.append("\nDomain Name: ").append(params.getDomainName());
      }

      sb.append("\nIPv4 Default Gateway: ").append(params.getIpv4DefaultGateway());
      if (!params.getIpv6DefaultGateway().isEmpty()) {
         sb.append("\nIPv6 Default Gateway: ").append(params.getIpv6DefaultGateway());
      }

      sb.append("\nDNS Servers: ").append(this.getIPAddressesString(params.getDnsServers()));
      return sb.toString();
   }

   public String getIPv4Address() {
      String addr = "";
      NetworkIF[] nifs = this.getInterfaceSpecs();

      for(NetworkIF nif : nifs) {
         String[] ipV4addrs = nif.getIPv4addr();

         for(String ipV4addr : ipV4addrs) {
            if (ipV4addr.length() > 0 && !ipV4addr.startsWith("127.")) {
               addr = ipV4addr;
            }
         }
      }

      return addr;
   }

   public NetworkIF[] getInterfaceSpecs() {
      List<NetworkIF> networkIfList = this.hardwareAbstractionLayer.getNetworkIFs(true);
      return (NetworkIF[])networkIfList.toArray(new NetworkIF[networkIfList.size()]);
   }

   public NetworkIF[] getInterfaceSpecsWithIpv4Address() {
      List<NetworkIF> networkIfList = this.hardwareAbstractionLayer.getNetworkIFs();
      ArrayList<NetworkIF> selectedNetworkIfList = new ArrayList();

      for(NetworkIF nif : networkIfList) {
         String[] ipV4addrs = nif.getIPv4addr();

         for(String ipV4addr : ipV4addrs) {
            if (ipV4addr.length() > 0 && !ipV4addr.startsWith("127.")) {
               selectedNetworkIfList.add(nif);
               break;
            }
         }
      }

      return (NetworkIF[])selectedNetworkIfList.toArray(new NetworkIF[selectedNetworkIfList.size()]);
   }

   public String getIPAddressesString(String[] ipAddressArr) {
      StringBuilder sb = new StringBuilder();
      boolean first = true;

      for(String ipAddress : ipAddressArr) {
         if (first) {
            first = false;
         } else {
            sb.append("; ");
         }

         sb.append(ipAddress);
      }

      return sb.toString();
   }

   public void saveInterfaceIOStatistics() {
      NetworkIF[] nif = this.getInterfaceSpecsWithIpv4Address();
      this.nifStatistics = new NetworkIFStatistics[nif.length];

      for(int i = 0; i < nif.length; ++i) {
         this.nifStatistics[i] = new NetworkIFStatistics(nif[i]);
      }

   }

   public String networkIO(String ipv4Addr, boolean quoteAsRate) {
      if (this.nifStatistics == null) {
         return "";
      } else {
         NetworkIFStatistics[] var6;
         for(NetworkIFStatistics nifs : var6 = this.nifStatistics) {
            NetworkIF nif = nifs.getNIf();
            String[] ipV4addrs = nif.getIPv4addr();

            for(String addr : ipV4addrs) {
               if (addr.equals(ipv4Addr)) {
                  nif.updateAttributes();
                  if (quoteAsRate) {
                     return nifs.dataRateString();
                  }

                  return nifs.dataString();
               }
            }
         }

         return "";
      }
   }

   public String report(String name) {
      if (name.equalsIgnoreCase("context")) {
         return this.reportContext(this.invigilator);
      } else if (name.equalsIgnoreCase("summary")) {
         return this.reportAsString(this.invigilator);
      } else if (name.equalsIgnoreCase("networking")) {
         return this.reportNetworking(this.invigilator);
      } else if (name.equalsIgnoreCase("processes")) {
         return this.reportProcesses(this.invigilator);
      } else if (name.equalsIgnoreCase("filesystem")) {
         return this.reportFileSystem(this.invigilator);
      } else if (name.equalsIgnoreCase("desktop")) {
         return this.reportDesktop(this.invigilator);
      } else if (!name.equalsIgnoreCase("apps") && !name.equalsIgnoreCase("applications")) {
         return name.equalsIgnoreCase("browser") ? this.reportBrowser(this.invigilator) : "";
      } else {
         return this.reportApplications(this.invigilator);
      }
   }

   public String reportAsString(Invigilator invigilator) {
      String var10000 = invigilator.getSessionContext();
      return "Session Context: " + var10000 + " Start: " + String.valueOf(new Date(invigilator.getActualStartTime())) + "\n" + invigilator.getSessionStartContext() + "\n\nVendor: " + this.getVendor() + " Processor: " + this.getProcessorIdentifier() + "\nIdentifier: " + this.getComputerIdentifier() + "\nOS: " + this.getOS() + String.format("\nMemory Available: %.01f GB Total: %.01f GB", this.getAvailableMemory(), this.getTotalMemory()) + "\n" + this.getNetworkingSummary();
   }

   private String reportTitle(String title, Invigilator invigilator) {
      StringBuffer sb = new StringBuffer();
      sb.append("<h1 id=\"reportTitle\">");
      sb.append(title);
      sb.append(": ");
      sb.append(invigilator.getName());
      sb.append(" (");
      sb.append(invigilator.getID());
      sb.append(") </h1>");
      sb.append(invigilator.getServletProcessor().refreshForServlet());
      return sb.toString();
   }

   private void addMeter(StringBuffer sb, long low, long high, long value) {
      sb.append("<meter value=\"");
      sb.append(value);
      sb.append("\" min=\"0\" max=\"100\"");
      sb.append(" low=\"");
      sb.append(low);
      sb.append("\"");
      sb.append(" high=\"");
      sb.append(high);
      sb.append("\">");
      sb.append(value);
      sb.append("%</meter>");
   }

   public String reportContext(Invigilator invigilator) {
      StringBuffer sb = new StringBuffer();
      sb.append(this.reportTitle("Session Context", invigilator));
      sb.append("<div class=\"w3-card-4\">");
      sb.append("<header class=\"w3-container w3-blue\"><h1>Session Parameters</h1></header>");
      sb.append("<table class=\"w3-table-all\"><tr><th>Session Variable</th><th>Session Value</th></tr>");
      sb.append("<tr><td>Activity</td><td>");
      sb.append(invigilator.getSessionContext());
      sb.append("</td></tr>");
      sb.append("<tr><td>Health</td><td>");
      if (invigilator.getReportManager() != null) {
         sb.append(invigilator.getReportManager().health());
      } else {
         sb.append("");
      }

      sb.append("</td></tr>");

      for(PowerSource ps : this.getPowerSources()) {
         if (ps.isPowerOnLine()) {
            sb.append("<tr><td>Power</td><td><span class=\"w3-tag w3-green\">Connected</span></td</tr>");
         } else if (ps.getTimeRemainingEstimated() > (double)0.0F) {
            long estimateToPowerExhaustion = Math.round(Math.min(ps.getTimeRemainingEstimated(), ps.getTimeRemainingInstant()) / (double)60.0F);
            sb.append("<tr><td>Power Remaining</td><td><span class=\"w3-tag ");
            long delta = estimateToPowerExhaustion - invigilator.estimatedTimeToEnd(TimeUnit.MINUTES);
            if (delta > POWER_EXHAUSTION_THRESHOLD_IN_MINUTES) {
               sb.append("w3-green\">");
            } else if (delta > 0L) {
               sb.append("w3-orange\">");
            } else {
               sb.append("w3-red\">");
            }

            sb.append(estimateToPowerExhaustion);
            sb.append(" mins (");
            sb.append(Math.round(ps.getRemainingCapacityPercent() * (double)100.0F));
            sb.append("%)</span>");
            sb.append("</td></tr>");
         } else if (ClientShared.isWindowsOS()) {
            WindowsPowerMeter wpm = new WindowsPowerMeter();
            int estimatedChargeRemaining = wpm.getEstimatedChargeRemaining();
            if (estimatedChargeRemaining > 0) {
               long estimateToPowerExhaustion = wpm.getEstimatedRunTime();
               sb.append("<tr><td>Power Remaining</td><td><span class=\"w3-tag ");
               long delta = estimateToPowerExhaustion - invigilator.estimatedTimeToEnd(TimeUnit.MINUTES);
               if (delta > POWER_EXHAUSTION_THRESHOLD_IN_MINUTES) {
                  sb.append("w3-green\">");
               } else if (delta > 0L) {
                  sb.append("w3-orange\">");
               } else {
                  sb.append("w3-red\">");
               }

               if (estimateToPowerExhaustion > 0L) {
                  sb.append(estimateToPowerExhaustion);
               } else {
                  sb.append("unknown");
               }

               sb.append(" mins (");
               sb.append(wpm.getEstimatedChargeRemaining());
               sb.append("%)</span>");
            }

            sb.append("</td></tr>");
         }
      }

      long mins = invigilator.getExamDurationInMinutes();
      long minsInMsecs = mins * 60L * 1000L;
      sb.append("<tr><td>Start</td><td>");
      sb.append(new Date(invigilator.getActualStartTime()));
      sb.append("</td></tr>");
      sb.append("<tr><td>Elapsed</td><td>");
      long elapsed = invigilator.elapsedTimeInMillis();
      sb.append(Utils.convertMsecsToHoursMinutesSeconds(elapsed));
      if (mins > 0L && elapsed < minsInMsecs) {
         sb.append("&nbsp;(");
         sb.append(Math.round((double)elapsed * (double)100.0F / (double)minsInMsecs));
         sb.append("%)&nbsp;");
         this.addMeter(sb, 89L, 100L, elapsed * 100L / minsInMsecs);
      }

      sb.append("</td></tr>");
      if (mins > 0L) {
         long remaining = minsInMsecs - elapsed;
         sb.append("<tr><td>Remaining</td><td>");
         if (remaining > 0L) {
            if (remaining < THIRTY_MINUTES_IN_MSECS) {
               sb.append("<span class=\"w3-tag w3-orange\">");
            }

            sb.append(Utils.convertMsecsToHoursMinutesSeconds(remaining));
            sb.append("&nbsp;(");
            sb.append(Math.round((double)remaining * (double)100.0F / (double)minsInMsecs));
            sb.append("%)");
            if (remaining < THIRTY_MINUTES_IN_MSECS) {
               sb.append("</span>");
            }

            sb.append("&nbsp;");
            this.addMeter(sb, 10L, 100L, remaining * 100L / minsInMsecs);
         } else {
            sb.append("<span class=\"w3-tag w3-red\">");
            sb.append("The allocated exam time has now expired");
            sb.append("</span>");
         }
      }

      sb.append("</td></tr>");
      sb.append("<tr><td>Duration</td><td>");
      sb.append(mins);
      sb.append(" mins</td></tr><tr><td>Initialization</td><td>");
      sb.append(invigilator.getSessionStartContext());
      sb.append("</td</tr></table>");
      sb.append("</div>");
      sb.append("<div class=\"w3-card-4\">");
      sb.append("<header class=\"w3-container w3-blue\"><h1>Machine Parameters</h1></header>");
      sb.append("<table class=\"w3-table-all w3-centre\">");
      sb.append("<thead><th>Machine Variable</th><th>Machine Value</th></thead>");
      sb.append("<tbody>");
      sb.append("<tr><td>Vendor</td><td>");
      sb.append(this.getVendor());
      sb.append("</td></tr><tr><td>Processor</td><td>");
      sb.append(this.getProcessorIdentifier());
      String architecture = this.getMicroarchitecture();
      if (architecture.length() > 0 && !architecture.equals("unknown")) {
         sb.append("</td></tr><tr><td>Architecture</td><td>");
         sb.append(architecture);
      }

      sb.append("</td></tr><tr><td>Identifier</td><td>");
      sb.append(this.getComputerIdentifier());
      sb.append("</td></tr><tr><td>OS</td><td>");
      sb.append(this.getOS());
      sb.append("</td></tr><tr><td>Memory Available</td><td>");
      if (this.isBelowAvailableMemoryThreshold()) {
         sb.append("<span class=\"w3-tag w3-red\">");
      }

      sb.append(String.format("%.01f GB", this.getAvailableMemory()));
      if (this.isBelowAvailableMemoryThreshold()) {
         sb.append("</span>");
      }

      sb.append("</td></tr><tr><td>Total Memory</td><td>");
      if (this.isBelowMemoryThreshold()) {
         sb.append("<span class=\"w3-tag w3-red\">");
      }

      sb.append(String.format("%.01f GB", this.getTotalMemory()));
      if (this.isBelowMemoryThreshold()) {
         sb.append("</span>");
      }

      NetworkParams params = this.getNetworkParams();
      sb.append("</td></tr><tr><td>Host Name</td><td>");
      sb.append(params.getHostName());
      sb.append("</td></tr><tr><td>IPv4 Address</td><td>");
      String ipv4Address = this.getIPv4Address();
      sb.append(ipv4Address);
      sb.append("</td></tr><tr><td>Remote Address</td><td>");
      sb.append(invigilator.getProperty("LOCAL_ADDRESS", ""));
      sb.append(" ");
      sb.append(invigilator.getLocation(false));
      sb.append("</td></tr><tr><td>Domain Name</td><td>");
      sb.append(params.getDomainName());
      sb.append("</td></tr><tr><td>IPv4 Default Gateway</td><td>");
      sb.append(params.getIpv4DefaultGateway());
      sb.append("</td></tr><tr><td>IPv6 Default Gateway</td><td>");
      sb.append(params.getIpv6DefaultGateway());
      sb.append("</td></tr><tr><td>DNS Servers</td><td>");
      sb.append(this.getIPAddressesString(params.getDnsServers()));
      boolean quoteAsRate = Utils.getBooleanOrDefault(invigilator.getProperties(), "report.network.quote_rate", true);
      if (quoteAsRate) {
         sb.append("</td></tr><tr><td>Network I/O Rate (bytes/sec)</td><td>");
      } else {
         sb.append("</td></tr><tr><td>Network I/O (bytes)</td><td>");
      }

      sb.append(this.networkIO(ipv4Address, quoteAsRate));
      NetworkIF vpn = invigilator.getSessionConfigurationModeMonitor().networkInterfaceRunningVPN();
      if (vpn != null) {
         sb.append("</td></tr><tr><td>VPN Server</td><td>");
         sb.append(this.getIPAddressesString(vpn.getIPv4addr()));
      }

      String vmString = DetectVM.identifyVM(false);
      if (!vmString.isEmpty()) {
         sb.append("</td></tr><tr><td>Virtual Machine</td><td>");
         sb.append(vmString);
      }

      sb.append("</td></tr><tbody></table>");
      sb.append("</div>");
      return sb.toString();
   }

   public String reportNetworking(Invigilator invigilator) {
      StringBuffer sb = new StringBuffer();
      NetworkIF[] nifs = this.getInterfaceSpecs();
      NetworkIF vpn = invigilator.getSessionConfigurationModeMonitor().networkInterfaceRunningVPN();
      sb.append(this.reportTitle("Network Interfaces", invigilator));
      sb.append("<table class=\"w3-table-all w3-centre\">");
      sb.append("<th id=\"net\">Name</th><th id=\"net\">Display Name</th><th id=\"net\">Operational/Connected/VM/VPN?</th><th id=\"net\">MAC Address</th><th id=\"net\">IPv4 Address</th><th id=\"net\">IPv6 Address</th>");
      boolean isVPNif = false;

      for(NetworkIF nif : nifs) {
         isVPNif = vpn != null && nif.getName().equals(vpn.getName());
         sb.append("<tr><td>");
         if (isVPNif) {
            sb.append("<span class=\"w3-blue\">");
         }

         sb.append(nif.getName());
         if (isVPNif) {
            sb.append("</span>");
         }

         sb.append("</td><td>");
         if (isVPNif) {
            sb.append("<span class=\"w3-blue\">");
         }

         sb.append(nif.getDisplayName());
         if (isVPNif) {
            sb.append("</span>");
         }

         sb.append("</td><td>");
         sb.append(nif.getIfOperStatus());
         sb.append("/");
         sb.append(nif.isConnectorPresent());
         sb.append("/");
         sb.append(nif.isKnownVmMacAddr());
         sb.append("/");
         sb.append(isVPNif);
         sb.append("</td><td>");
         sb.append(nif.getMacaddr());
         sb.append("</td><td>");
         sb.append(this.getIPAddressesString(nif.getIPv4addr()));
         sb.append("</td><td>");
         sb.append(this.getIPAddressesString(nif.getIPv6addr()));
         sb.append("</td></tr>");
      }

      sb.append("</table>");
      sb.append("<script>");
      sb.append(SystemWebResources.sorting("net"));
      sb.append("</script>");
      sb.append("<p></p>");
      NetworkParams params = this.getNetworkParams();
      sb.append("<table class=\"w3-table-all w3-centre\">");
      sb.append("<th id=\"net2\">Name</th><th id=\"net2\">Value</th>");
      sb.append("<tr><td>Host Name</td><td>");
      sb.append(params.getHostName());
      sb.append("</td></tr><tr><td>IPv4 Address</td><td>");
      String ipv4Address = this.getIPv4Address();
      sb.append(ipv4Address);
      sb.append("</td></tr><tr><td>Remote Address</td><td>");
      sb.append(invigilator.getProperty("LOCAL_ADDRESS", ""));
      sb.append(" ");
      sb.append(invigilator.getLocation(false));
      sb.append("</td></tr><tr><td>Domain Name</td><td>");
      sb.append(params.getDomainName());
      sb.append("</td></tr><tr><td>IPv4 Default Gateway</td><td>");
      sb.append(params.getIpv4DefaultGateway());
      sb.append("</td></tr><tr><td>IPv6 Default Gateway</td><td>");
      sb.append(params.getIpv6DefaultGateway());
      sb.append("</td></tr><tr><td>DNS Servers</td><td>");
      sb.append(this.getIPAddressesString(params.getDnsServers()));
      if (vpn != null) {
         sb.append("</td></tr><tr><td>VPN Server</td><td>");
         sb.append(this.getIPAddressesString(vpn.getIPv4addr()));
      }

      boolean quoteAsRate = Utils.getBooleanOrDefault(invigilator.getProperties(), "report.network.quote_rate", true);
      if (quoteAsRate) {
         sb.append("</td></tr><tr><td>Network I/O Rate (bytes/sec)</td><td>");
      } else {
         sb.append("</td></tr><tr><td>Network I/O (bytes)</td><td>");
      }

      sb.append(this.networkIO(ipv4Address, quoteAsRate));
      sb.append("</td></tr></table>");
      sb.append("<script>");
      sb.append(SystemWebResources.sorting("net2"));
      sb.append("</script>");
      return sb.toString();
   }

   public String reportApplications(Invigilator invigilator) {
      if (this.applicationList == null) {
         this.applicationList = this.operatingSystem.getInstalledApplications();
      }

      StringBuffer sb = new StringBuffer();
      ApplicationInfo[] deniedApplications = this.deniedApplications();
      sb.append(this.reportTitle("<button id=\"toggle\" class=\"w3-button w3-border w3-round-large\" onclick=\"toggle()\">Toggle</button>&nbsp;Applications", invigilator));
      sb.append("<table id=\"processes\" class=\"w3-table-all w3-centre\">");
      sb.append("<th id=\"app\">Name</th><th id=\"app\">Version</th><th id=\"app\">Install Date</th><th id=\"app\">Vendor</th>");
      String sq = SystemWebResources.getSearchQuery();

      for(ApplicationInfo app : this.applicationList) {
         sb.append("<tr>");
         sb.append("<td>");
         boolean isDenied = this.isApplicationOfInterest(app, deniedApplications);
         if (isDenied) {
            sb.append("<span class=\"w3-tag w3-red\">");
         } else {
            sb.append("<span id=\"process\">");
         }

         sb.append("<a style=\"text-decoration:none\" target=\"_blank\" href=\"");
         sb.append(sq);
         String[] query = app.getName().split(" ");
         sb.append(String.join("+", query));
         sb.append("\">");
         sb.append(app.getName());
         sb.append("</a></span>");
         sb.append("</td>");
         sb.append("<td>");
         sb.append(app.getVersion());
         sb.append("</td>");
         sb.append("<td>");
         sb.append(new Date(app.getTimestamp()));
         sb.append("</td>");
         sb.append("<td>");
         sb.append("<details><summary>");
         sb.append(app.getVendor());
         sb.append("</summary><textarea wrap=\"hard\" readonly rows=\"4\" cols=\"40\">");
         Map<String, String> map = app.getAdditionalInfo();
         map.forEach((k, v) -> {
            sb.append(k);
            sb.append(":");
            sb.append(v);
            sb.append("\n");
         });
         sb.append("</textarea></details>");
         sb.append("</td>");
         sb.append("</tr>");
      }

      sb.append("</table>");
      sb.append("<script>\n   var allVisible = true;\n   const rows = document.querySelector(\"tr\");\n   rows.dataset.originalDisplay = rows.style.display || \"table-row\";\n   function toggle() {\n      allVisible = !allVisible;\n      const toggleButton = document.getElementById(\"toggle\");\n      if (allVisible) {\n         toggleButton.textContent = \"Hide\";\n      } else {         toggleButton.textContent = \"Show\";\n      }\n      const table = document.getElementById(\"processes\");\n      const links = table.querySelectorAll(\"span[id='process'\");\n      links.forEach((link, index) => {\n         const row = link.closest(\"tr\");\n         if (allVisible) {\n            row.style.display = rows.dataset.originalDisplay || \"table-row\";\n         } else {\n            row.style.display = \"none\";\n         }\n      });\n   }\n");
      sb.append(SystemWebResources.sorting("app"));
      sb.append("</script>\n");
      return sb.toString();
   }

   public String reportDesktop(Invigilator invigilator) {
      StringBuffer sb = new StringBuffer();
      OSDesktopWindow[] deniedWindows = this.deniedWindows();
      OSDesktopWindow[] allowedWindows = this.allowedWindows();
      List<OSDesktopWindow> windows = this.operatingSystem.getDesktopWindows(this.windowVisible);
      sb.append(this.reportTitle("<button id=\"toggle\" class=\"w3-button w3-border w3-round-large\" onclick=\"toggle()\">Toggle</button>&nbsp;" + (this.windowVisible ? "Windows" : "All Windows"), invigilator));
      sb.append("<table id=\"processes\" class=\"w3-table-all w3-centre\">");
      sb.append("<th id=\"desktop\">Title</th><th id=\"desktop\">Path</th>");
      String sq = SystemWebResources.getSearchQuery();

      for(OSDesktopWindow window : windows) {
         sb.append("<tr>");
         sb.append("<td>");
         boolean isAllowed = this.isWindowOfInterest(window, allowedWindows);
         boolean isDenied = this.isWindowOfInterest(window, deniedWindows);
         if (isDenied && !isAllowed) {
            sb.append("<span class=\"w3-tag w3-red\">");
         } else {
            sb.append("<span id=\"process\">");
         }

         sb.append("<a style=\"text-decoration:none\" target=\"_blank\" href=\"");
         sb.append(sq);
         String[] query = window.getTitle().split(" ");
         sb.append(String.join("+", query));
         sb.append("\">");
         sb.append(window.getTitle());
         sb.append("</a></span>");
         sb.append("</td>");
         sb.append("<td>");
         sb.append(window.getCommand());
         sb.append("</td>");
         sb.append("</tr>");
      }

      sb.append("</table>");
      sb.append("<script>\n   var allVisible = true;\n   const rows = document.querySelector(\"tr\");\n   rows.dataset.originalDisplay = rows.style.display || \"table-row\";\n   function toggle() {\n      allVisible = !allVisible;\n      const toggleButton = document.getElementById(\"toggle\");\n      if (allVisible) {\n         toggleButton.textContent = \"Hide\";\n      } else {         toggleButton.textContent = \"Show\";\n      }\n      const table = document.getElementById(\"processes\");\n      const links = table.querySelectorAll(\"span[id='process'\");\n      links.forEach((link, index) => {\n         const row = link.closest(\"tr\");\n         if (allVisible) {\n            row.style.display = rows.dataset.originalDisplay || \"table-row\";\n         } else {\n            row.style.display = \"none\";\n         }\n      });\n   }\n");
      sb.append(SystemWebResources.sorting("desktop"));
      sb.append("</script>\n");
      return sb.toString();
   }

   private boolean isApplicationOfInterest(ApplicationInfo app, ApplicationInfo[] apps) {
      if (apps == null) {
         return false;
      } else {
         for(ApplicationInfo a : apps) {
            if (a.getName().equals(app.getName())) {
               return true;
            }
         }

         return false;
      }
   }

   public boolean isWindowOfInterest(OSDesktopWindow window, OSDesktopWindow[] windows) {
      if (windows == null) {
         return false;
      } else {
         for(OSDesktopWindow w : windows) {
            if (window.getTitle().equals(w.getTitle())) {
               return true;
            }
         }

         return false;
      }
   }

   public boolean hasMultipleWindowsNamed(String name) {
      return this.hasWindowNamed(name, 1);
   }

   public boolean hasWindowNamed(String regex, int threshold) {
      List<OSDesktopWindow> windows = this.operatingSystem.getDesktopWindows(this.windowVisible);

      try {
         int found = 0;
         Pattern p = Pattern.compile(regex);

         for(OSDesktopWindow window : windows) {
            Matcher m = p.matcher(window.getTitle());
            if (m.matches()) {
               ++found;
            }
         }

         if (found > threshold) {
            return true;
         } else {
            return false;
         }
      } catch (PatternSyntaxException var9) {
         return false;
      }
   }

   public boolean hasPIDrunning(int pid) {
      OSProcess process = this.getRunningProcess((long)pid);
      return process != null;
   }

   public boolean hasMultipleInstancesRunning(String regex, int threshold) {
      try {
         int found = 0;
         Pattern p = Pattern.compile(regex);

         for(OSProcess process : this.operatingSystem.getProcesses()) {
            Matcher m = p.matcher(process.getPath());
            if (m.matches()) {
               ++found;
            }
         }

         if (found > threshold) {
            return true;
         } else {
            return false;
         }
      } catch (PatternSyntaxException var9) {
         return false;
      }
   }

   public String reportProcesses(Invigilator invigilator) {
      StringBuffer sb = new StringBuffer();
      List<OSProcess> processes = this.operatingSystem.getProcesses();
      Collections.sort(processes, new ProcessComparator());
      sb.append(this.reportTitle("<button id=\"toggle\" class=\"w3-button w3-border w3-round-large\" onclick=\"toggle()\">Toggle</button>&nbsp;Processes", invigilator));
      sb.append("<table id=\"processes\" class=\"w3-table-all w3-centre\">");
      sb.append("<th id=\"proc\">Name</th><th id=\"proc\">PID</th><th id=\"proc\">User ID</th><th id=\"proc\">Path</th>");
      String sq = SystemWebResources.getSearchQuery();
      OSProcessAction[] deniedProcesses = this.deniedProcesses();

      for(OSProcess process : processes) {
         sb.append("<tr>");
         sb.append("<td>");
         if (HardwareAndSoftwareMonitor.OSProcessAction.hasBeenSuspended(process)) {
            sb.append("<span class=\"w3-tag w3-orange\">");
         } else {
            boolean found = false;

            for(OSProcessAction pa : deniedProcesses) {
               if (process.getProcessID() == pa.process.getProcessID()) {
                  sb.append("<span class=\"w3-tag w3-red\">");
                  found = true;
                  break;
               }
            }

            if (!found) {
               sb.append("<span id=\"process\">");
            }
         }

         sb.append("<a style=\"text-decoration:none\" target=\"_blank\" href=\"");
         sb.append(sq);
         String[] query = process.getName().split(" ");
         sb.append(String.join("+", query));
         sb.append("\">");
         sb.append(process.getName());
         sb.append("</a></span>");
         sb.append("</td>");
         sb.append("<td>");
         sb.append(process.getProcessID());
         sb.append("</td>");
         sb.append("<td>");
         sb.append(process.getUserID());
         sb.append("</td>");
         sb.append("<td>");
         sb.append(process.getPath());
         sb.append("</td>");
         sb.append("</tr>");
      }

      sb.append("</table>");
      sb.append("<script>\n   var allVisible = true;\n   const rows = document.querySelector(\"tr\");\n   rows.dataset.originalDisplay = rows.style.display || \"table-row\";\n   function toggle() {\n      allVisible = !allVisible;\n      const toggleButton = document.getElementById(\"toggle\");\n      if (allVisible) {\n         toggleButton.textContent = \"Hide\";\n      } else {         toggleButton.textContent = \"Show\";\n      }\n      const table = document.getElementById(\"processes\");\n      const links = table.querySelectorAll(\"span[id='process'\");\n      links.forEach((link, index) => {\n         const row = link.closest(\"tr\");\n         if (allVisible) {\n            row.style.display = rows.dataset.originalDisplay || \"table-row\";\n         } else {\n            row.style.display = \"none\";\n         }\n      });\n   }\n");
      sb.append(SystemWebResources.sorting("proc"));
      sb.append("</script>\n");
      return sb.toString();
   }

   public boolean isRunning(String processName) {
      if (processName != null) {
         for(OSProcess process : this.operatingSystem.getProcesses()) {
            if (processName.startsWith(process.getName())) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean isRunning(Pattern pattern) {
      if (pattern != null) {
         for(OSProcess process : this.operatingSystem.getProcesses()) {
            Matcher matcher = pattern.matcher(process.getName());
            if (matcher.matches()) {
               return true;
            }
         }
      }

      return false;
   }

   public OSProcess getRunningProcess(long pid) {
      if (pid > 0L) {
         for(OSProcess process : this.operatingSystem.getProcesses()) {
            if (pid == (long)process.getProcessID()) {
               return process;
            }
         }
      }

      return null;
   }

   public OSProcess getRunningProcess(String processName) {
      if (processName != null) {
         for(OSProcess process : this.operatingSystem.getProcesses()) {
            if (processName.startsWith(process.getName())) {
               return process;
            }
         }
      }

      return null;
   }

   public OSProcess getRunningProcess(Pattern pattern) {
      if (pattern != null) {
         for(OSProcess process : this.operatingSystem.getProcesses()) {
            Matcher matcher = pattern.matcher(process.getName());
            if (matcher.matches()) {
               return process;
            }
         }
      }

      return null;
   }

   public void quitRunningProcess(String processName) {
      OSProcess process = this.getRunningProcess(processName);
      if (process != null) {
         OSProcessAction ospa = new OSProcessAction(process, HardwareAndSoftwareMonitor.Action.quit);
         ospa.perform();
      }

   }

   public void terminateRunningProcess(String processName) {
      OSProcess process = this.getRunningProcess(processName);
      if (process != null) {
         OSProcessAction ospa = new OSProcessAction(process, HardwareAndSoftwareMonitor.Action.kill);
         ospa.perform();
      }

   }

   public void terminateRunningProcess(long pid) {
      OSProcess process = this.getRunningProcess(pid);
      if (process != null) {
         OSProcessAction ospa = new OSProcessAction(process, HardwareAndSoftwareMonitor.Action.kill);
         ospa.perform();
      }

   }

   public void suspendRunningProcess(String processName) {
      OSProcess process = this.getRunningProcess(processName);
      if (process != null) {
         OSProcessAction ospa = new OSProcessAction(process, HardwareAndSoftwareMonitor.Action.suspend);
         ospa.perform();
      }

   }

   public void addFinalizedProcess(String processName) {
      synchronized(this.finalizeProcesses) {
         this.finalizeProcesses.add(HardwareAndSoftwareMonitor.PatternAction.compile(processName + "," + String.valueOf(HardwareAndSoftwareMonitor.Action.quit)));
      }
   }

   public OSProcessAction[] initializedProcesses() {
      synchronized(this.initializeProcesses) {
         return this.patternProcesses(this.initializeProcesses);
      }
   }

   public OSProcessAction[] finalizedProcesses() {
      synchronized(this.finalizeProcesses) {
         return this.patternProcesses(this.finalizeProcesses);
      }
   }

   public OSProcessAction[] deniedProcesses() {
      if (!this.processCheckPerformed) {
         return null;
      } else {
         synchronized(this.deniedProcesses) {
            return this.patternProcesses(this.deniedProcesses);
         }
      }
   }

   public OSProcessAction[] allowedProcesses() {
      if (!this.processCheckPerformed) {
         return null;
      } else {
         synchronized(this.allowedProcesses) {
            return this.patternProcesses(this.allowedProcesses);
         }
      }
   }

   public ApplicationInfo[] deniedApplications() {
      if (!this.installedApplicationsCheckPerformed) {
         return null;
      } else {
         if (this.applicationList == null) {
            this.applicationList = this.operatingSystem.getInstalledApplications();
         }

         synchronized(this.installedApplications) {
            ArrayList<ApplicationInfo> match = new ArrayList();

            for(Pattern ptn : this.installedApplications) {
               for(ApplicationInfo application : this.applicationList) {
                  Matcher m = ptn.matcher(application.getName());
                  if (m.matches()) {
                     match.add(application);
                  }
               }
            }

            return (ApplicationInfo[])match.toArray(new ApplicationInfo[match.size()]);
         }
      }
   }

   public OSDesktopWindow[] deniedWindows() {
      if (!this.windowCheckPerformed) {
         return null;
      } else {
         synchronized(this.deniedWindows) {
            return this.patternWindows(this.deniedWindows);
         }
      }
   }

   public OSDesktopWindow[] allowedWindows() {
      if (!this.windowCheckPerformed) {
         return null;
      } else {
         synchronized(this.allowedWindows) {
            return this.patternWindows(this.allowedWindows);
         }
      }
   }

   public BrowserHistoryReader.HistoryEntry[] deniedPages() {
      if (!this.browserHistoryCheckPerformed) {
         return null;
      } else {
         synchronized(this.browserHistory) {
            return this.patternBrowser(this.browserHistory, this.browserHistoryAllowed);
         }
      }
   }

   public OSDesktopWindow[] patternWindows(ArrayList patterns) {
      ArrayList<OSDesktopWindow> match = new ArrayList();
      List<OSDesktopWindow> windows = this.operatingSystem.getDesktopWindows(this.windowVisible);

      for(PatternAction ptn : patterns) {
         for(OSDesktopWindow window : windows) {
            OSProcessAction pa = ptn.matcher(window, (OSProcess)null);
            if (pa != null) {
               match.add(window);
            }
         }
      }

      return (OSDesktopWindow[])match.toArray(new OSDesktopWindow[match.size()]);
   }

   public BrowserHistoryReader.HistoryEntry[] patternBrowser(ArrayList patterns, ArrayList allowedPatterns) {
      ArrayList<BrowserHistoryReader.HistoryEntry> match = new ArrayList();

      try {
         List<BrowserHistoryReader.HistoryEntry> pages = this.browserHistoryReader.getAllHistory();

         for(Pattern ptn : allowedPatterns) {
            for(BrowserHistoryReader.HistoryEntry page : pages) {
               Matcher m = ptn.matcher(page.getUrl());
               if (m.matches()) {
                  page.setAllowed(true);
               }
            }
         }

         for(Pattern ptn : patterns) {
            for(BrowserHistoryReader.HistoryEntry page : pages) {
               if (!page.isAllowed()) {
                  Matcher m = ptn.matcher(page.getUrl());
                  if (m.matches()) {
                     match.add(page);
                  }
               }
            }
         }

         return (BrowserHistoryReader.HistoryEntry[])match.toArray(new BrowserHistoryReader.HistoryEntry[match.size()]);
      } catch (Exception var10) {
         return null;
      }
   }

   public OSProcessAction[] deniedDesktopWindows() {
      if (!this.windowCheckPerformed) {
         return null;
      } else {
         synchronized(this.deniedWindows) {
            return this.patternDesktopWindows(this.deniedWindows);
         }
      }
   }

   public OSProcessAction[] patternDesktopWindows(ArrayList patterns) {
      ArrayList<OSProcessAction> match = new ArrayList();
      List<OSDesktopWindow> windows = this.operatingSystem.getDesktopWindows(this.windowVisible);

      for(PatternAction pa : patterns) {
         for(OSDesktopWindow window : windows) {
            long pid = window.getOwningProcessId();
            OSProcess process = this.invigilator.getHardwareAndSoftwareMonitor().getRunningProcess(pid);
            if (process != null) {
               OSProcessAction ospa = pa.matcher(window, process);
               if (ospa != null) {
                  match.add(ospa);
               }
            }
         }
      }

      return (OSProcessAction[])match.toArray(new OSProcessAction[match.size()]);
   }

   public OSProcessAction[] patternProcesses(ArrayList patterns) {
      ArrayList<OSProcessAction> match = new ArrayList();
      List<OSProcess> processes = this.operatingSystem.getProcesses();

      for(PatternAction pa : patterns) {
         for(OSProcess process : processes) {
            OSProcessAction ospa = pa.matcher(process);
            if (ospa != null) {
               match.add(ospa);
            }
         }
      }

      return (OSProcessAction[])match.toArray(new OSProcessAction[match.size()]);
   }

   public int findAndActOnProcessesConformingToPattern(PatternAction pa) {
      int numberOfProcessesActedOn = 0;

      for(OSProcess process : this.operatingSystem.getProcesses()) {
         OSProcessAction ospa = pa.matcher(process);
         if (ospa != null) {
            if (ospa.hasBeenSuspended()) {
               ospa.restore();
               if (ospa.action.isKill()) {
                  ospa.perform();
               }
            } else {
               ospa.perform();
            }

            ++numberOfProcessesActedOn;
         }
      }

      return numberOfProcessesActedOn;
   }

   public String reportBrowser(Invigilator invigilator) {
      if (!this.browserHistoryCheckPerformed) {
         return "";
      } else {
         StringBuffer sb = new StringBuffer();
         sb.append(this.reportTitle("Pages", invigilator));
         sb.append(invigilator.getReportManager().getReactorProcessorReport("reported_pages"));
         return sb.toString();
      }
   }

   public String reportFileSystem(Invigilator invigilator) {
      StringBuffer sb = new StringBuffer();
      FileSystem fileSystem = this.operatingSystem.getFileSystem();
      List<OSFileStore> fileStores = fileSystem.getFileStores();
      sb.append(this.reportTitle("Filesystem", invigilator));
      sb.append("<table class=\"w3-table-all w3-centre\">");
      sb.append("<th id=\"fs\">Name</th><th id=\"fs\">Mount</th><th id=\"fs\">Type</th><th id=\"fs\">Space</th><th id=\"fs\">Utilization</th>");

      for(OSFileStore fileStore : fileStores) {
         sb.append("<tr>");
         sb.append("<td>");
         sb.append(fileStore.getName());
         sb.append("</td>");
         sb.append("<td>");
         sb.append(fileStore.getMount());
         sb.append("</td>");
         sb.append("<td>");
         sb.append(fileStore.getType());
         sb.append("</td>");
         sb.append("<td>");
         float free = (float)fileStore.getFreeSpace() / 1.0737418E9F;
         sb.append(String.format("%.02f GB", free));
         sb.append("/");
         float total = (float)fileStore.getTotalSpace() / 1.0737418E9F;
         sb.append(String.format("%.02f GB", total));
         sb.append(String.format(" %.01f%s", free * 100.0F / total, "%"));
         sb.append("</td>");
         sb.append("<td><meter high=\"0.9\" low=\"0.1\" value=\"");
         sb.append(free / total);
         sb.append("\"></meter></td>");
         sb.append("</tr>");
      }

      sb.append("</table>");
      sb.append("<script>");
      sb.append(SystemWebResources.sorting("fs"));
      sb.append("</script>");
      return sb.toString();
   }

   public String[] hasRemoveableMedia() {
      FileSystem fileSystem = this.operatingSystem.getFileSystem();
      List<OSFileStore> fileStores = fileSystem.getFileStores();
      ArrayList<String> devices = new ArrayList();
      if (this.volumesCheckPerformed) {
         for(OSFileStore fileStore : fileStores) {
            if (this.getVendor().contains("Linux") && this.hasRemoveableMediaLinux(fileStore)) {
               devices.add(fileStore.getName());
            }

            if (this.getVendor().equals("Microsoft") && this.hasRemoveableMediaWindows(fileStore)) {
               devices.add(fileStore.getName());
            }

            if (this.getVendor().equals("Apple") && this.hasRemoveableMediaApple(fileStore)) {
               devices.add(fileStore.getName());
            }
         }
      }

      return (String[])devices.toArray(new String[devices.size()]);
   }

   private boolean hasAllowedMediaPattern(OSFileStore fileStore) {
      boolean allowed = false;

      for(Pattern p : this.allowedVolumes) {
         Matcher m = p.matcher(fileStore.getName());
         allowed = m.matches();
         if (allowed) {
            return true;
         }
      }

      return false;
   }

   private boolean hasRemoveableMediaLinux(OSFileStore fileStore) {
      if (this.hasAllowedMediaPattern(fileStore)) {
         return false;
      } else {
         return fileStore.getName().startsWith("/media");
      }
   }

   private boolean hasRemoveableMediaWindows(OSFileStore fileStore) {
      if (this.hasAllowedMediaPattern(fileStore)) {
         return false;
      } else {
         return fileStore.getName().startsWith("Removeable");
      }
   }

   private boolean hasRemoveableMediaApple(OSFileStore fileStore) {
      if (this.hasAllowedMediaPattern(fileStore)) {
         return false;
      } else {
         return fileStore.getMount().startsWith("/Volumes");
      }
   }

   public boolean isVM() {
      return DetectVM.identifyVM(true).isEmpty();
   }

   public void addServletHandlers() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      ContextServlet cs = new ContextServlet(this.invigilator);
      sp.addServlet(cs, "/context");
      DesktopServlet ds = new DesktopServlet(this.invigilator);
      sp.addServlet(ds, "/desktop");
      ApplicationServlet apps = new ApplicationServlet(this.invigilator);
      sp.addServlet(apps, "/applications");
      sp.addServlet(apps, "/apps");
      BrowserPagesServlet bps = new BrowserPagesServlet(this.invigilator);
      sp.addServlet(bps, "/browser");
      ProcessServlet ps = new ProcessServlet(this.invigilator);
      sp.addServlet(ps, "/processes");
      FileSystemServlet fs = new FileSystemServlet(this.invigilator);
      sp.addServlet(fs, "/filesystem");
      NetworkingServlet ns = new NetworkingServlet(this.invigilator);
      sp.addServlet(ns, "/networking");
      HomeServlet hs = new HomeServlet(this.invigilator);
      sp.addServlet(hs, "/home");
      sp.addServlet(hs, "/session");
      sp.addServlet(hs, "/exam");
      ServletRouter sr = sp.getRouter();
      sr.addRule("/home", InvigilatorState.running);
      sr.addRule("/exam", InvigilatorState.running);
      sr.addRule("/session", InvigilatorState.running);
      sr.addRedirect(InvigilatorState.running, SystemWebResources.getHomePage());
   }

   public Health getPowerHealth(Invigilator invigilator) {
      for(PowerSource ps : this.getPowerSources()) {
         if (ps.isPowerOnLine()) {
            return HardwareAndSoftwareMonitor.Health.green;
         }

         if (ps.getTimeRemainingEstimated() > (double)0.0F) {
            long estimateToPowerExhaustion = Math.round(Math.min(ps.getTimeRemainingEstimated(), ps.getTimeRemainingInstant()) / (double)60.0F);
            long delta = estimateToPowerExhaustion - invigilator.estimatedTimeToEnd(TimeUnit.MINUTES);
            if (delta > POWER_EXHAUSTION_THRESHOLD_IN_MINUTES) {
               return HardwareAndSoftwareMonitor.Health.green;
            }

            if (delta > POWER_EXHAUSTION_THRESHOLD_IN_MINUTES / 2L) {
               return HardwareAndSoftwareMonitor.Health.blue;
            }

            if (delta > 0L) {
               return HardwareAndSoftwareMonitor.Health.orange;
            }

            return HardwareAndSoftwareMonitor.Health.red;
         }
      }

      return HardwareAndSoftwareMonitor.Health.blue;
   }

   public boolean isBelowMemoryThreshold() {
      return Math.round(this.getTotalMemory()) < this.getMemoryThresholdInGB();
   }

   public int getMemoryThresholdInGB() {
      return MEMORY_THRESHOLD;
   }

   public boolean isBelowAvailableMemoryThreshold() {
      return Math.round(this.getAvailableMemory() * 10.0F) / 10 < this.getAvailableMemoryThresholdInGB();
   }

   public int getAvailableMemoryThresholdInGB() {
      return AVAILABLE_MEMORY_THRESHOLD == 0 ? Integer.MAX_VALUE : AVAILABLE_MEMORY_THRESHOLD;
   }

   public void configure() {
      this.configure(this.invigilator.getProperties());
   }

   public void configure(Properties properties) {
      String os = ClientShared.getOSString();
      int maxTime = Utils.getIntegerOrDefaultInRange(properties, "session.maxTime", 10800, 1800, 86400);
      maxTime = Utils.getIntegerOrDefaultInRange(properties, "session.max_time", maxTime, maxTime, 86400);
      MEMORY_THRESHOLD = Utils.getIntegerOrDefaultInRange(properties, "memory_threshold", DEFAULT_MEMORY_THRESHOLD, 1, 1024);
      AVAILABLE_MEMORY_THRESHOLD = Utils.getIntegerOrDefaultInRange(properties, "available_memory_threshold", 1, 0, MEMORY_THRESHOLD);
      POWER_EXHAUSTION_THRESHOLD_IN_MINUTES = (long)Utils.getIntegerOrDefaultInRange(properties, "power_exhaustion_threshold", 30, 0, maxTime);
      StringBuffer patternError = new StringBuffer();
      this.volumesCheckPerformed = Utils.getBooleanOrDefault(properties, "monitoring.volumes.required", false);
      this.allowedVolumes.clear();
      this.processPattern("volumes.allowed.pattern.", properties, this.allowedVolumes, patternError);
      this.processPattern("volumes." + os + ".allowed.pattern.", properties, this.allowedVolumes, patternError);
      this.processPattern("volumes." + this.invigilator.getID() + ".allowed.pattern.", properties, this.allowedVolumes, patternError);
      this.processPattern("volumes." + this.invigilator.getCourse() + ".allowed.pattern.", properties, this.allowedVolumes, patternError);
      this.processCheckPerformed = Utils.getBooleanOrDefault(properties, "monitoring.processes.required", false);
      this.windowCheckPerformed = Utils.getBooleanOrDefault(properties, "monitoring.windows.required", false);
      this.installedApplicationsCheckPerformed = Utils.getBooleanOrDefault(properties, "monitoring.applications.required", false);
      this.browserHistoryCheckPerformed = Utils.getBooleanOrDefault(properties, "monitoring.browsers.required", false);
      this.allowedProcesses.clear();
      this.processPattern("processes.allowed.pattern.", properties, this.allowedProcesses, false, patternError);
      this.processPattern("processes." + os + ".allowed.pattern.", properties, this.allowedProcesses, false, patternError);
      this.processPattern("processes." + this.invigilator.getID() + ".allowed.pattern.", properties, this.allowedProcesses, false, patternError);
      this.processPattern("processes." + this.invigilator.getCourse() + ".allowed.pattern.", properties, this.allowedProcesses, false, patternError);
      this.deniedProcesses.clear();
      this.processPattern("processes.denied.pattern.", properties, this.deniedProcesses, true, patternError);
      this.processPattern("processes." + os + ".denied.pattern.", properties, this.deniedProcesses, true, patternError);
      this.processPattern("processes." + this.invigilator.getID() + ".denied.pattern.", properties, this.deniedProcesses, true, patternError);
      this.processPattern("processes." + this.invigilator.getCourse() + ".denied.pattern.", properties, this.deniedProcesses, true, patternError);
      this.initializeProcesses.clear();
      this.processPattern("processes.initialize.pattern.", properties, this.initializeProcesses, true, patternError);
      this.processPattern("processes." + os + ".initialize.pattern.", properties, this.initializeProcesses, true, patternError);
      this.processPattern("processes." + this.invigilator.getID() + ".initialize.pattern.", properties, this.initializeProcesses, true, patternError);
      this.processPattern("processes." + this.invigilator.getCourse() + ".initialize.pattern.", properties, this.initializeProcesses, true, patternError);
      this.finalizeProcesses.clear();
      this.processPattern("processes.finalize.pattern.", properties, this.finalizeProcesses, true, patternError);
      this.processPattern("processes." + os + ".finalize.pattern.", properties, this.finalizeProcesses, true, patternError);
      this.processPattern("processes." + this.invigilator.getID() + ".finalize.pattern.", properties, this.finalizeProcesses, true, patternError);
      this.processPattern("processes." + this.invigilator.getCourse() + ".finalize.pattern.", properties, this.finalizeProcesses, true, patternError);
      this.windowVisible = Utils.getBooleanOrDefault(properties, "windows.visible", true);
      this.deniedWindows.clear();
      this.processPattern("windows.denied.pattern.", properties, this.deniedWindows, true, patternError);
      this.processPattern("windows." + os + ".denied.pattern.", properties, this.deniedWindows, true, patternError);
      this.processPattern("windows." + this.invigilator.getID() + ".denied.pattern.", properties, this.deniedWindows, true, patternError);
      this.processPattern("windows." + this.invigilator.getCourse() + ".denied.pattern.", properties, this.deniedWindows, true, patternError);
      this.allowedWindows.clear();
      this.processPattern("windows.allowed.pattern.", properties, this.allowedWindows, true, patternError);
      this.processPattern("windows." + os + ".allowed.pattern.", properties, this.allowedWindows, true, patternError);
      this.processPattern("windows." + this.invigilator.getID() + ".allowed.pattern.", properties, this.allowedWindows, true, patternError);
      this.processPattern("windows." + this.invigilator.getCourse() + ".allowed.pattern.", properties, this.allowedWindows, true, patternError);
      this.installedApplications.clear();
      this.processPattern("applications.denied.pattern.", properties, this.installedApplications, patternError);
      this.processPattern("applications." + os + ".denied.pattern.", properties, this.installedApplications, patternError);
      this.processPattern("applications." + this.invigilator.getID() + ".denied.pattern.", properties, this.installedApplications, patternError);
      this.processPattern("applications." + this.invigilator.getCourse() + ".denied.pattern.", properties, this.installedApplications, patternError);
      if (this.installedApplicationsCheckPerformed) {
         this.applicationList = this.operatingSystem.getInstalledApplications();
      }

      this.browserHistory.clear();
      this.processPattern("browser.denied.pattern.", properties, this.browserHistory, patternError);
      this.processPattern("browser." + os + ".denied.pattern.", properties, this.browserHistory, patternError);
      this.processPattern("browser." + this.invigilator.getID() + ".denied.pattern.", properties, this.browserHistory, patternError);
      this.processPattern("browser." + this.invigilator.getCourse() + ".denied.pattern.", properties, this.browserHistory, patternError);
      this.browserHistoryAllowed.clear();
      this.processPattern("browser.allowed.pattern.", properties, this.browserHistoryAllowed, patternError);
      this.processPattern("browser." + os + ".allowed.pattern.", properties, this.browserHistoryAllowed, patternError);
      this.processPattern("browser." + this.invigilator.getID() + ".allowed.pattern.", properties, this.browserHistoryAllowed, patternError);
      this.processPattern("browser." + this.invigilator.getCourse() + ".allowed.pattern.", properties, this.browserHistoryAllowed, patternError);
      this.browserHistoryReader.setStart(this.invigilator.getActualStartTime());
      String error = patternError.toString();
      if (error.length() > 0) {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, error, this.invigilator.createProblemSetEvent("software_configuration"));
      }

   }

   private void processPattern(String patternFormat, Properties properties, ArrayList patternArray, StringBuffer patternError) {
      int i = 1;

      for(String property = properties.getProperty(patternFormat + i); property != null; property = properties.getProperty(patternFormat + i)) {
         try {
            Pattern p = Pattern.compile(property.trim());
            patternArray.add(p);
         } catch (IllegalArgumentException var8) {
            if (patternError.toString().length() == 0) {
               patternError.append("The following directives are invalid patterns:\n");
            } else {
               patternError.append("\n");
            }

            patternError.append(property);
         }

         ++i;
      }

   }

   private void processPattern(String patternFormat, Properties properties, ArrayList patternArray, boolean check, StringBuffer patternError) {
      int i = 1;

      for(String property = properties.getProperty(patternFormat + i); property != null; property = properties.getProperty(patternFormat + i)) {
         try {
            PatternAction pa;
            if (check) {
               pa = HardwareAndSoftwareMonitor.PatternAction.compile(property);
            } else {
               Pattern p = Pattern.compile(property);
               pa = new PatternAction(p);
            }

            patternArray.add(pa);
         } catch (IllegalArgumentException var10) {
            if (patternError.toString().length() == 0) {
               patternError.append("The following directives are invalid patterns:\n");
            } else {
               patternError.append("\n");
            }

            patternError.append(property);
         }

         ++i;
      }

   }

   public class ProcessComparator implements Comparator {
      public int compare(OSProcess o1, OSProcess o2) {
         return o1.getName().compareTo(o2.getName());
      }
   }

   public class ProcessComparatorByUserID implements Comparator {
      public int compare(OSProcess o1, OSProcess o2) {
         return o1.getUserID().compareTo(o2.getUserID());
      }
   }

   public class ProcessComparatorByID implements Comparator {
      public int compare(OSProcess o1, OSProcess o2) {
         return o1.getProcessID() - o2.getProcessID();
      }
   }

   public class HomeServlet extends EmbeddedServlet {
      public HomeServlet(Invigilator invigilator) {
         super(invigilator);
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         this.invigilator.getServletProcessor().updateLastAccessTime();
         if (!this.invigilator.isInInvigilatorState(InvigilatorState.running)) {
            response.sendError(404);
         } else {
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("text/html");
            PrintWriter pw = response.getWriter();
            pw.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\"><title>");
            pw.print(this.invigilator.getTitle());
            pw.print("CoMaS ");
            String servletPath = request.getServletPath();
            if (servletPath.startsWith("/")) {
               pw.print(servletPath.substring(1, 2).toUpperCase());
               pw.print(servletPath.substring(2));
            } else {
               pw.print(servletPath.substring(0, 1).toUpperCase());
               pw.print(servletPath.substring(1));
            }

            pw.print(": ");
            pw.print(this.invigilator.getName());
            pw.print(" (");
            pw.print(this.invigilator.getID());
            pw.print(")</title>");
            pw.println(SystemWebResources.getStylesheet());
            pw.println(SystemWebResources.getIcon());
            pw.print("</head><body>");
            pw.print(this.invigilator.getServletProcessor().checkForServletCode());
            pw.print("<div class=\"w3-panel\" style=\"margin:auto;width:80%\">");
            this.homePageContent(pw, request.getRequestURI());
            pw.print(this.invigilator.getServletProcessor().footerForServlet(false, true));
            pw.print("</div>");
            pw.print("</body></html>");
            response.setStatus(200);
         }
      }

      void homePageContent(PrintWriter pw, String servletURI) {
         boolean full = servletURI.endsWith("home");
         if (full) {
            this.invigilator.setLastServlet("home");
         }

         boolean session = servletURI.endsWith("session");
         if (session) {
            this.invigilator.setLastServlet("session");
         }

         boolean exam = servletURI.endsWith("exam");
         if (exam) {
            this.invigilator.setLastServlet("exam");
         }

         String ss = this.invigilator.getServletProcessor().getService();
         pw.print("<h1 id=\"reportTitle\">CoMaS Commands</h1>");
         pw.print(this.invigilator.getServletProcessor().refreshForServlet());
         pw.print("<table class=\"w3-table-all\">");
         pw.print("<thead><th>Command</th><th>Description</th></thead>");
         pw.print("<tbody>");
         if (full || session) {
            pw.print("<tr><td><h1>Session: ");
            pw.print(this.invigilator.getName());
            pw.print(" (");
            pw.print(this.invigilator.getID());
            pw.print(")</h1></td><td></td></tr>");
            pw.print("<tr><td><button accesskey=\"d\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + "desktop\", \"Desktop\");'>Desktop</button>");
            pw.print("</td><td>Display list of desktop windows</td></tr>");
            pw.print("<tr><td><button accesskey=\"p\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + "processes\", \"Processes\");'>Processes</button>");
            pw.print("</td><td>Display list of running processes</td></tr>");
            pw.print("<tr><td><button accesskey=\"f\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + "filesystem\", \"Filesystem\");'>Filesystem</button>");
            pw.print("</td><td>Display list of available filesystem volumes</td></tr>");
            pw.print("<tr><td><button accesskey=\"n\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + "networking\", \"Networking\");'>Networking</button>");
            pw.print("</td><td>Display networking configuration</td></tr>");
         }

         String examMapping = this.invigilator.getSessionContext();
         if (full || exam) {
            pw.print("<tr><td><h1>Activity: ");
            pw.print(examMapping);
            pw.print("</h1></td><td></td></tr>");
            pw.print("<tr><td><button accesskey=\"c\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + "context\", \"Context\");'>Context</button>");
            pw.print("</td><td>Display session context</td></tr>");
            pw.print("<tr><td><button accesskey=\"r\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + "report\", \"Report\");'>Report</button>");
            pw.print("</td><td>Display session mode report</td></tr>");
            pw.print("<tr><td><button accesskey=\"s\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + "summary\", \"Summary\");'>Summary</button>");
            pw.print("</td><td>Display session summary report</td></tr>");
            pw.print("<tr><td><button accesskey=\"l\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + "log\", \"Logs\");'>Logs</button>");
            pw.print("</td><td>Display session logs</td></tr>");
            if (this.invigilator.getServletProcessor().getServletMapping("/" + examMapping) != null) {
               pw.print("<tr><td><button accesskey=\"e\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + examMapping + "\", \"Exam\");'>Exam</button>");
               pw.print("</td><td>Display exam questions</td></tr>");
            }

            if (this.invigilator.getServletProcessor().hasTools()) {
               pw.print("<tr><td><button accesskey=\"t\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + "tools/\", \"Tools\");'>Tools</button>");
               pw.print("</td><td>Display available session tools</td></tr>");
            }

            if (this.invigilator.getServletProcessor().hasResources()) {
               pw.print("<tr><td><button accesskey=\"o\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + "resources/\", \"Resources\");'>Resources</button>");
               pw.print("</td><td>Display available session resources</td></tr>");
            }

            int numberOfCheckedEvents = WebcamServlet.getSingleton().getNumberOfCheckedEvents();
            if (WebcamServlet.getSingleton().isIncludingWebcam()) {
               pw.print("<tr><td><button accesskey=\"w\" class=\"w3-button w3-round-large w3-blue\" onclick='openWindow(\"" + ss + "webcam\", \"Webcam\");'>Webcam");
               if (numberOfCheckedEvents > 0) {
                  pw.print("&nbsp;<span class=\"w3-badge\">");
                  pw.print(numberOfCheckedEvents);
                  pw.print("</span>");
               }

               pw.print("</button></td><td>Display webcam</td></tr>");
            } else {
               pw.print("<tr><td><button accesskey=\"v\" class=\"w3-button w3-round-large w3-blue\" onclick='openWindow(\"" + ss + "webcam\", \"Events\");'>Events");
               if (numberOfCheckedEvents > 0) {
                  pw.print("&nbsp;<span class=\"w3-badge\">");
                  pw.print(numberOfCheckedEvents);
                  pw.print("</span>");
               }

               pw.print("</button></td><td>Display events</td></tr>");
            }

            MessageHandler mh = this.invigilator.getWebsocketEndpointManager().getHandler("alert");
            pw.print("<tr><td><button accesskey=\"a\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + "alerts\", \"Alerts\");'>Alerts");
            if (mh instanceof AlertMessageHandler) {
               AlertMessageHandler amh = (AlertMessageHandler)mh;
               int numberOfUnacknowledgedMessages = amh.numberOfUnacknowledgedAlerts();
               if (numberOfUnacknowledgedMessages > 0) {
                  pw.print("&nbsp;<span class=\"w3-badge\">");
                  pw.print(numberOfUnacknowledgedMessages);
                  pw.print("</span>");
               }
            }

            pw.print("</button>");
            pw.print("</td><td>Display session administrator alerts</td></tr>");
            String mailButton = this.invigilator.getServletProcessor().getMailButton();
            if (mailButton.length() > 0) {
               pw.print("<tr><td>");
               pw.print(mailButton);
               pw.print("</td><td>");
               pw.print("Send email to session administrator");
               pw.print("</td></tr>");
            }
         }

         if (this.invigilator.getServletProcessor().getServletMapping("/quit") != null) {
            pw.print("<tr><td><h1>Control: ");
            pw.print(examMapping);
            pw.print("</h1></td><td></td></tr>");
            pw.print("<tr><td><button accesskey=\"q\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + ss + "quit\", \"Quit\");'>Quit</button>");
            pw.print("</td><td>Quit the current session</td></tr>");
         }

         pw.print("</tbody></table>");
      }
   }

   public class ContextServlet extends EmbeddedServlet {
      public ContextServlet(Invigilator invigilator) {
         super(invigilator);
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         this.invigilator.getServletProcessor().updateLastAccessTime();
         if (!this.invigilator.isInInvigilatorState(InvigilatorState.running)) {
            response.sendError(404);
         } else {
            this.invigilator.setLastServlet("context");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("text/html");
            PrintWriter pw = response.getWriter();
            pw.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\"><title>");
            pw.print(this.invigilator.getTitle());
            pw.print("CoMaS Context: ");
            pw.print(this.invigilator.getName());
            pw.print(" (");
            pw.print(this.invigilator.getID());
            pw.print(")</title>");
            pw.println(SystemWebResources.getStylesheet());
            pw.println(SystemWebResources.getIcon());
            pw.print("</head><body>");
            pw.print(this.invigilator.getServletProcessor().checkForServletCode());
            pw.print("<div class=\"w3-container\">");
            pw.println(HardwareAndSoftwareMonitor.this.reportContext(this.invigilator));
            pw.println("<br/>");
            pw.print(this.invigilator.getServletProcessor().footerForServlet(true, true, SystemWebResources.getHomeButton()));
            pw.print("</div>");
            pw.print("</body></html>");
            response.setStatus(200);
         }
      }
   }

   public class NetworkingServlet extends EmbeddedServlet {
      public NetworkingServlet(Invigilator invigilator) {
         super(invigilator);
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         this.invigilator.getServletProcessor().updateLastAccessTime();
         if (!this.invigilator.isInInvigilatorState(InvigilatorState.running)) {
            response.sendError(404);
         } else {
            this.invigilator.setLastServlet("networking");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("text/html");
            PrintWriter pw = response.getWriter();
            pw.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\"><title>");
            pw.print(this.invigilator.getTitle());
            pw.print("CoMaS Networking: ");
            pw.print(this.invigilator.getName());
            pw.print(" (");
            pw.print(this.invigilator.getID());
            pw.print(")</title>");
            pw.println(SystemWebResources.getStylesheet());
            pw.println(SystemWebResources.getIcon());
            pw.print("</head><body>");
            pw.print(this.invigilator.getServletProcessor().checkForServletCode());
            pw.print("<div class=\"w3-container\">");
            pw.println(HardwareAndSoftwareMonitor.this.reportNetworking(this.invigilator));
            pw.print("<br/>");
            pw.print(this.invigilator.getServletProcessor().footerForServlet(true, true, SystemWebResources.getHomeButton()));
            pw.print("</div>");
            pw.print("</body></html>");
            response.setStatus(200);
         }
      }
   }

   public class BrowserPagesServlet extends EmbeddedServlet {
      public BrowserPagesServlet(Invigilator invigilator) {
         super(invigilator);
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         this.invigilator.getServletProcessor().updateLastAccessTime();
         if (this.invigilator.isInInvigilatorState(InvigilatorState.running) && HardwareAndSoftwareMonitor.this.browserHistoryCheckPerformed) {
            this.invigilator.setLastServlet("browser");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("text/html");
            PrintWriter pw = response.getWriter();
            pw.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\">");
            pw.print("<title>");
            pw.print(this.invigilator.getTitle());
            pw.print("CoMaS Browser: ");
            pw.print(this.invigilator.getName());
            pw.print(" (");
            pw.print(this.invigilator.getID());
            pw.print(")</title>");
            pw.println(SystemWebResources.getStylesheet());
            pw.println(SystemWebResources.getIcon());
            pw.print("</head><body>");
            pw.print(this.invigilator.getServletProcessor().checkForServletCode());
            pw.print("<div class=\"w3-container\">");
            pw.println(HardwareAndSoftwareMonitor.this.reportBrowser(this.invigilator));
            pw.print("<br/>");
            pw.print(this.invigilator.getServletProcessor().footerForServlet(true, true, SystemWebResources.getHomeButton()));
            pw.print("</div>");
            pw.println("</body></html>");
            response.setStatus(200);
         } else {
            response.sendError(404);
         }
      }
   }

   public class ApplicationServlet extends EmbeddedServlet {
      public ApplicationServlet(Invigilator invigilator) {
         super(invigilator);
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         this.invigilator.getServletProcessor().updateLastAccessTime();
         if (this.invigilator.isInInvigilatorState(InvigilatorState.running) && HardwareAndSoftwareMonitor.this.installedApplicationsCheckPerformed) {
            this.invigilator.setLastServlet("apps");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("text/html");
            PrintWriter pw = response.getWriter();
            pw.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\">");
            pw.print("<title>");
            pw.print(this.invigilator.getTitle());
            pw.print("CoMaS Applications: ");
            pw.print(this.invigilator.getName());
            pw.print(" (");
            pw.print(this.invigilator.getID());
            pw.print(")</title>");
            pw.println(SystemWebResources.getStylesheet());
            pw.println(SystemWebResources.getIcon());
            pw.print("</head><body>");
            pw.print(this.invigilator.getServletProcessor().checkForServletCode());
            pw.print("<div class=\"w3-container\">");
            pw.println(HardwareAndSoftwareMonitor.this.reportApplications(this.invigilator));
            pw.print("<br/>");
            pw.print(this.invigilator.getServletProcessor().footerForServlet(true, true, SystemWebResources.getHomeButton()));
            pw.print("</div>");
            pw.println("</body></html>");
            response.setStatus(200);
         } else {
            response.sendError(404);
         }
      }
   }

   public class DesktopServlet extends EmbeddedServlet {
      public DesktopServlet(Invigilator invigilator) {
         super(invigilator);
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         this.invigilator.getServletProcessor().updateLastAccessTime();
         if (!this.invigilator.isInInvigilatorState(InvigilatorState.running)) {
            response.sendError(404);
         } else {
            this.invigilator.setLastServlet("desktop");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("text/html");
            PrintWriter pw = response.getWriter();
            pw.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\">");
            pw.print("<title>");
            pw.print(this.invigilator.getTitle());
            pw.print("CoMaS Desktop: ");
            pw.print(this.invigilator.getName());
            pw.print(" (");
            pw.print(this.invigilator.getID());
            pw.print(")</title>");
            pw.println(SystemWebResources.getStylesheet());
            pw.println(SystemWebResources.getIcon());
            pw.print("</head><body>");
            pw.print(this.invigilator.getServletProcessor().checkForServletCode());
            pw.print("<div class=\"w3-container\">");
            pw.println(HardwareAndSoftwareMonitor.this.reportDesktop(this.invigilator));
            pw.print("<br/>");
            pw.print(this.invigilator.getServletProcessor().footerForServlet(true, true, SystemWebResources.getHomeButton()));
            pw.print("</div>");
            pw.println("</body></html>");
            response.setStatus(200);
         }
      }
   }

   public class ProcessServlet extends EmbeddedServlet {
      public ProcessServlet(Invigilator invigilator) {
         super(invigilator);
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         this.invigilator.getServletProcessor().updateLastAccessTime();
         if (!this.invigilator.isInInvigilatorState(InvigilatorState.running)) {
            response.sendError(404);
         } else {
            this.invigilator.setLastServlet("processes");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("text/html");
            PrintWriter pw = response.getWriter();
            pw.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\"><title>");
            pw.print(this.invigilator.getTitle());
            pw.print("CoMaS Processes: ");
            pw.print(this.invigilator.getName());
            pw.print(" (");
            pw.print(this.invigilator.getID());
            pw.print(")</title>");
            pw.println(SystemWebResources.getStylesheet());
            pw.println(SystemWebResources.getIcon());
            pw.print("</head><body>");
            pw.print(this.invigilator.getServletProcessor().checkForServletCode());
            pw.print("<div class=\"w3-container\">");
            pw.println(HardwareAndSoftwareMonitor.this.reportProcesses(this.invigilator));
            pw.print("<br/>");
            pw.print(this.invigilator.getServletProcessor().footerForServlet(true, true, SystemWebResources.getHomeButton()));
            pw.print("</div>");
            pw.println("</body></html>");
            response.setStatus(200);
         }
      }
   }

   public class FileSystemServlet extends EmbeddedServlet {
      public FileSystemServlet(Invigilator invigilator) {
         super(invigilator);
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         this.invigilator.getServletProcessor().updateLastAccessTime();
         if (!this.invigilator.isInInvigilatorState(InvigilatorState.running)) {
            response.sendError(404);
         } else {
            this.invigilator.setLastServlet("filesystem");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("text/html");
            PrintWriter pw = response.getWriter();
            pw.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\"><title>");
            pw.print(this.invigilator.getTitle());
            pw.print("CoMaS File System: ");
            pw.print(this.invigilator.getName());
            pw.print(" (");
            pw.print(this.invigilator.getID());
            pw.print(")</title>");
            pw.println(SystemWebResources.getStylesheet());
            pw.println(SystemWebResources.getIcon());
            pw.print("</head><body>");
            pw.print(this.invigilator.getServletProcessor().checkForServletCode());
            pw.print("<div class=\"w3-container\">");
            pw.println(HardwareAndSoftwareMonitor.this.reportFileSystem(this.invigilator));
            pw.print("<br/>");
            pw.print(this.invigilator.getServletProcessor().footerForServlet(true, true, SystemWebResources.getHomeButton()));
            pw.print("</div>");
            pw.println("</body></html>");
            response.setStatus(200);
         }
      }
   }

   public static enum Action {
      kill,
      suspend,
      resume,
      quit,
      report,
      unspecified;

      public boolean isKill() {
         return this == kill;
      }

      public boolean isSuspend() {
         return this == suspend;
      }

      public boolean isResume() {
         return this == resume;
      }

      public boolean isQuit() {
         return this == quit;
      }

      public boolean isReport() {
         return this == report;
      }

      public boolean isUnspecified() {
         return this == unspecified;
      }

      public static Action parse(String actionString) {
         actionString = actionString.toLowerCase().trim();
         if (!actionString.equals("kill") && !actionString.equals("stop") && !actionString.equals("close")) {
            if (!actionString.equals("suspend") && !actionString.equals("freeze")) {
               if (!actionString.equals("resume") && !actionString.equals("unfreeze")) {
                  if (actionString.equals("quit")) {
                     return quit;
                  } else {
                     return actionString.equals("report") ? report : unspecified;
                  }
               } else {
                  return resume;
               }
            } else {
               return suspend;
            }
         } else {
            return kill;
         }
      }
   }

   public static class PatternAction {
      public final Action action;
      public final Pattern pattern;
      public final ClientShared.OS os;
      public boolean type;

      public PatternAction(Pattern pattern) {
         this(pattern, HardwareAndSoftwareMonitor.Action.unspecified);
      }

      public PatternAction(Pattern pattern, Action action) {
         this(pattern, action, ClientShared.getOS(), true);
      }

      public PatternAction(Pattern pattern, Action action, ClientShared.OS os, boolean type) {
         this.pattern = pattern;
         this.action = action;
         this.os = os;
         this.type = type;
      }

      public static PatternAction compile(String specification) throws PatternSyntaxException, IllegalArgumentException {
         specification = specification.trim();
         if (specification.length() == 0) {
            throw new IllegalArgumentException();
         } else {
            String[] tokens = specification.split("[:,]");
            if (tokens != null && tokens.length == 3) {
               return new PatternAction(Pattern.compile(fixRegex(tokens[0].trim())), HardwareAndSoftwareMonitor.Action.parse(tokens[1]), OS.parse(tokens[2].trim()), true);
            } else {
               return tokens != null && tokens.length == 2 ? new PatternAction(Pattern.compile(fixRegex(tokens[0].trim())), HardwareAndSoftwareMonitor.Action.parse(tokens[1])) : new PatternAction(Pattern.compile(fixRegex(specification)));
            }
         }
      }

      public void usePath() {
         this.setType(false);
      }

      public void useName() {
         this.setType(true);
      }

      public void setType(boolean type) {
         this.type = type;
      }

      public OSProcessAction matcher(OSProcess process) {
         return this.matcher(process, this.type);
      }

      public OSProcessAction matcher(OSDesktopWindow window, OSProcess process) {
         return this.matcher(window, process, this.type);
      }

      public OSProcessAction matcher(OSProcess process, boolean onName) {
         if (OS.isSameOS(this.os)) {
            Matcher m = this.pattern.matcher(onName ? process.getName() : process.getPath());
            return m.matches() ? new OSProcessAction(process, this.action) : null;
         } else {
            return null;
         }
      }

      public OSProcessAction matcher(OSDesktopWindow window, OSProcess process, boolean onName) {
         if (OS.isSameOS(this.os)) {
            Matcher m = this.pattern.matcher(onName ? window.getTitle() : window.getCommand());
            return m.matches() ? new OSProcessAction(process, window, this.action) : null;
         } else {
            return null;
         }
      }

      private static String fixRegex(String regex) {
         if (!regex.startsWith("^")) {
            regex = "^" + regex;
         }

         if (!regex.endsWith("$")) {
            regex = regex + "$";
         }

         return regex;
      }

      public String toString() {
         if (this.os.isUnknown()) {
            String var1 = this.pattern.pattern();
            return var1 + ":" + String.valueOf(this.action);
         } else {
            String var10000 = this.pattern.pattern();
            return var10000 + ":" + String.valueOf(this.action) + ":" + String.valueOf(this.os);
         }
      }
   }

   public static class OSProcessAction {
      private static Invigilator invigilator;
      public static final ArrayList suspendedProcesses = new ArrayList();
      private static final OutputProcessorObserver observer = new OutputProcessorObserver() {
         public void resultAvailable(OutputProcessor p) {
            edu.carleton.cas.logging.Logger.debug(Level.DIAGNOSTIC, p.result());
         }

         public void exceptionOccurred(Exception e) {
            HardwareAndSoftwareMonitor.OSProcessAction.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Hardware and software monitor could not run a command: " + String.valueOf(e), HardwareAndSoftwareMonitor.OSProcessAction.invigilator.createProblemSetEvent("software_configuration"));
         }
      };
      private static final NullOutputProcessor nullOutputProcessor;
      public final OSDesktopWindow window;
      public final OSProcess process;
      public final Action action;
      public final ClientShared.OS os;

      static {
         nullOutputProcessor = new NullOutputProcessor(observer);
      }

      public OSProcessAction(OSProcess process) {
         this(process, HardwareAndSoftwareMonitor.Action.unspecified);
      }

      public OSProcessAction(OSProcess process, Action action) {
         this(process, action, ClientShared.getOS());
      }

      public OSProcessAction(OSProcess process, Action action, ClientShared.OS os) {
         this.action = action;
         this.process = process;
         this.window = null;
         this.os = os;
      }

      public OSProcessAction(OSProcess process, OSDesktopWindow window) {
         this(process, window, HardwareAndSoftwareMonitor.Action.unspecified);
      }

      public OSProcessAction(OSProcess process, OSDesktopWindow window, Action action) {
         this(process, window, action, ClientShared.getOS());
      }

      public OSProcessAction(OSProcess process, OSDesktopWindow window, Action action, ClientShared.OS os) {
         this.action = action;
         this.process = process;
         this.window = window;
         this.os = os;
      }

      public static void setDefaultInvigilator(Invigilator _invigilator) {
         invigilator = _invigilator;
      }

      public boolean isReport() {
         return this.action.isReport();
      }

      public boolean isSuspend() {
         return this.action.isSuspend();
      }

      public boolean isKill() {
         return this.action.isKill();
      }

      public boolean isUnspecified() {
         return this.action.isUnspecified();
      }

      public boolean isAction(Action _action) {
         return this.action == _action;
      }

      public boolean hasBeenSuspended() {
         synchronized(suspendedProcesses) {
            for(PID spid : suspendedProcesses) {
               if (spid.equals(this.process.getProcessID())) {
                  return true;
               }
            }

            return false;
         }
      }

      public static boolean hasBeenSuspended(OSProcess process) {
         synchronized(suspendedProcesses) {
            for(PID spid : suspendedProcesses) {
               if (spid.equals(process.getProcessID())) {
                  return true;
               }
            }

            return false;
         }
      }

      private boolean removeFromSuspended() {
         synchronized(suspendedProcesses) {
            return suspendedProcesses.remove(new PID(this.process.getProcessID()));
         }
      }

      private void addToSuspended() {
         synchronized(suspendedProcesses) {
            suspendedProcesses.add(new PID(this.process.getProcessID()));
         }
      }

      public String toDisplay() {
         if (this.action.isKill()) {
            return this.process.getName() + "(T)";
         } else if (this.action.isSuspend()) {
            return this.process.getName() + "(S)";
         } else if (this.action.isQuit()) {
            return this.process.getName() + "(Q)";
         } else {
            return this.action.isReport() ? this.process.getName() + "(R)" : this.process.getName();
         }
      }

      public String toString() {
         if (this.os.isUnknown()) {
            String var1 = this.process.getName();
            return var1 + ":" + String.valueOf(this.action);
         } else {
            String var10000 = this.process.getName();
            return var10000 + ":" + String.valueOf(this.action) + ":" + String.valueOf(this.os);
         }
      }

      public boolean perform() {
         String pidAsString = String.format("%d", this.process.getProcessID());
         String[] cmd = null;
         if (this.action.isSuspend() && !this.hasBeenSuspended()) {
            if (!ClientShared.isMacOS() && !ClientShared.isLinuxOS()) {
               if (ClientShared.isWindowsOS()) {
                  cmd = new String[]{ClientShared.DOWNLOADS_DIR + "pssuspend.exe", "-accepteula", pidAsString};
                  File cmdFile = new File(ClientShared.DOWNLOADS_DIR + "pssuspend.exe");
                  if (!cmdFile.exists()) {
                     try {
                        invigilator.createOStoolFileFromResourceOrDownload("pssuspend.exe");
                     } catch (IOException var6) {
                        invigilator.logArchiver.put(Level.DIAGNOSTIC, "Could not access pssuspend.exe");
                     }
                  }
               }
            } else {
               cmd = new String[]{"kill", "-STOP", pidAsString};
            }

            if (cmd != null) {
               CommandRunner cr = new CommandRunner(cmd, nullOutputProcessor);
               cr.run();
               this.addToSuspended();
            }
         } else if (this.action.isKill()) {
            if (!ClientShared.isMacOS() && !ClientShared.isLinuxOS()) {
               if (ClientShared.isWindowsOS()) {
                  cmd = new String[]{ClientShared.DOWNLOADS_DIR + "pskill.exe", "-accepteula", pidAsString};
                  File cmdFile = new File(ClientShared.DOWNLOADS_DIR + "pskill.exe");
                  if (!cmdFile.exists()) {
                     try {
                        invigilator.createOStoolFileFromResourceOrDownload("pskill.exe");
                     } catch (IOException var5) {
                        invigilator.logArchiver.put(Level.DIAGNOSTIC, "Could not access pskill.exe");
                     }
                  }
               }
            } else {
               cmd = new String[]{"kill", "-9", pidAsString};
            }

            if (cmd != null) {
               CommandRunner cr = new CommandRunner(cmd, nullOutputProcessor);
               cr.run();
               return this.removeFromSuspended();
            }
         } else if (this.action.isReport()) {
            ReportManager rm = invigilator.getReportManager();
            rm.annotateReport(this.process);
            return rm.reportProcessorIsChanged("reported_processes");
         }

         return true;
      }

      public boolean restore() {
         String pidAsString = String.format("%d", this.process.getProcessID());
         String[] cmd = null;
         if ((this.action.isSuspend() || this.action.isResume()) && this.hasBeenSuspended()) {
            if (!ClientShared.isMacOS() && !ClientShared.isLinuxOS()) {
               if (ClientShared.isWindowsOS()) {
                  cmd = new String[]{ClientShared.DOWNLOADS_DIR + "pssuspend.exe", "-accepteula", "-r", pidAsString};
                  File cmdFile = new File(ClientShared.DOWNLOADS_DIR + "pssuspend.exe");
                  if (!cmdFile.exists()) {
                     try {
                        invigilator.createOStoolFileFromResourceOrDownload("pssuspend.exe");
                     } catch (IOException var5) {
                        invigilator.logArchiver.put(Level.DIAGNOSTIC, "Could not access pssuspend.exe");
                     }
                  }
               }
            } else {
               cmd = new String[]{"kill", "-CONT", pidAsString};
            }

            if (cmd != null) {
               CommandRunner cr = new CommandRunner(cmd, nullOutputProcessor);
               cr.run();
               return this.removeFromSuspended();
            }
         }

         return true;
      }

      public static void restoreAll() {
         String[] cmd = null;
         ArrayList<PID> copyOfSuspendedProcesses;
         synchronized(suspendedProcesses) {
            copyOfSuspendedProcesses = new ArrayList(suspendedProcesses);
         }

         for(PID spid : copyOfSuspendedProcesses) {
            if (!ClientShared.isMacOS() && !ClientShared.isLinuxOS()) {
               if (ClientShared.isWindowsOS()) {
                  cmd = new String[]{ClientShared.DOWNLOADS_DIR + "pssuspend.exe", "-accepteula", "-r", spid.toString()};
                  File cmdFile = new File(ClientShared.DOWNLOADS_DIR + "pssuspend.exe");
                  if (!cmdFile.exists()) {
                     try {
                        invigilator.createOStoolFileFromResourceOrDownload("pssuspend.exe");
                     } catch (IOException var7) {
                        invigilator.logArchiver.put(Level.DIAGNOSTIC, "Could not access pssuspend.exe");
                     }
                  }
               }
            } else {
               cmd = new String[]{"kill", "-CONT", spid.toString()};
            }

            if (cmd != null) {
               CommandRunner cr = new CommandRunner(cmd, nullOutputProcessor);
               cr.run();
            }
         }

         synchronized(suspendedProcesses) {
            suspendedProcesses.clear();
         }
      }
   }

   public static class PID {
      public final int pid;

      public PID(int pid) {
         this.pid = pid;
      }

      public boolean equals(int pid) {
         return this.pid == pid;
      }

      public boolean equals(Object id) {
         if (id instanceof PID) {
            return this.pid == ((PID)id).pid;
         } else {
            return super.equals(id);
         }
      }

      public String toString() {
         return String.valueOf(this.pid);
      }
   }

   public static enum Health {
      red,
      orange,
      yellow,
      blue,
      green,
      indigo,
      violet;

      public static Health parse(String value) {
         String trimmedValue = value.trim().toLowerCase();
         if (trimmedValue.equals("red")) {
            return red;
         } else if (trimmedValue.equals("orange")) {
            return orange;
         } else if (trimmedValue.equals("yellow")) {
            return yellow;
         } else if (trimmedValue.equals("blue")) {
            return blue;
         } else if (trimmedValue.equals("green")) {
            return green;
         } else if (trimmedValue.equals("indigo")) {
            return indigo;
         } else if (trimmedValue.equals("violet")) {
            return violet;
         } else {
            throw new IllegalArgumentException();
         }
      }

      public boolean isRed() {
         return this == red;
      }

      public boolean isOrange() {
         return this == orange;
      }

      public boolean isYellow() {
         return this == yellow;
      }

      public boolean isBlue() {
         return this == blue;
      }

      public boolean isGreen() {
         return this == green;
      }

      public boolean isIndigo() {
         return this == indigo;
      }

      public boolean isViolet() {
         return this == violet;
      }
   }

   public class NetworkIFStatistics {
      private long start;
      private long sent;
      private long recv;
      private final NetworkIF nif;

      public NetworkIFStatistics(NetworkIF nif) {
         this.nif = nif;
         this.reset();
      }

      public void reset() {
         this.sent = this.nif.getBytesSent();
         this.recv = this.nif.getBytesRecv();
         this.start = System.currentTimeMillis();
      }

      public double getSentRate() {
         long now = System.currentTimeMillis();
         return now == this.start ? (double)0.0F : (double)this.getSent() * (double)1000.0F / (double)Math.max(now - this.start, 1L);
      }

      public double getRecvRate() {
         long now = System.currentTimeMillis();
         return now == this.start ? (double)0.0F : (double)this.getRecv() * (double)1000.0F / (double)Math.max(now - this.start, 1L);
      }

      public long getSent() {
         long checkSent = this.nif.getBytesSent();
         if (checkSent < this.sent) {
            this.reset();
         }

         return checkSent - this.sent;
      }

      public long getRecv() {
         long checkRecv = this.nif.getBytesRecv();
         if (checkRecv < this.recv) {
            this.reset();
         }

         return checkRecv - this.recv;
      }

      public String toString() {
         return this.dataString();
      }

      public String dataString() {
         long var10000 = this.getRecv();
         return var10000 + "/" + this.getSent() + " bytes";
      }

      public String dataRateString() {
         return String.format("%.01f/%.01f bytes/sec", this.getRecvRate(), this.getSentRate());
      }

      public boolean updateAttributes() {
         return this.nif.updateAttributes();
      }

      public NetworkIF getNIf() {
         return this.nif;
      }
   }
}
