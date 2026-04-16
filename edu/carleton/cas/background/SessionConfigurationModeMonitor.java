package edu.carleton.cas.background;

import com.cogerent.detector.DisplayDetectorPro;
import com.cogerent.detector.DisplayInfo;
import com.cogerent.dns.DNS;
import com.cogerent.dns.DNSObserver;
import com.cogerent.utility.PropertyValue;
import edu.carleton.cas.background.timers.ExtendedTimer;
import edu.carleton.cas.background.timers.TimerService;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.jetty.embedded.ProgressServlet;
import edu.carleton.cas.jetty.embedded.SessionEndTask;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.reporting.ReportManager;
import edu.carleton.cas.reporting.ReportManager.ProblemStatus;
import edu.carleton.cas.resources.BrowserHistoryReader;
import edu.carleton.cas.resources.HardwareAndSoftwareMonitor;
import edu.carleton.cas.resources.NetworkIFManagerFactory;
import edu.carleton.cas.resources.NetworkIFManagerInterface;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.resources.HardwareAndSoftwareMonitor.OSProcessAction;
import edu.carleton.cas.ui.DisappearingAlert;
import edu.carleton.cas.ui.PasswordDialog;
import edu.carleton.cas.ui.WebAppDialogCoordinator;
import edu.carleton.cas.utility.Displays;
import edu.carleton.cas.utility.IconLoader;
import edu.carleton.cas.utility.Sleeper;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import oshi.hardware.Display;
import oshi.hardware.NetworkIF;
import oshi.software.os.NetworkParams;
import oshi.software.os.OSDesktopWindow;
import oshi.software.os.OSProcess;

public class SessionConfigurationModeMonitor implements Closeable, DNSObserver {
   private static final String MAXIMUM_MONITORS_PROPERTY_NAME = "session.maximum_monitors";
   private static final String SESSION_PASSWORD_PROPERTY_NAME = "session.password_prompt";
   private static final String ALLOWED_IPV4_ADDRESS_PATTERN_PROPERTY_NAME = "session.ipv4.pattern";
   private static final String ALLOWED_IPV4_GATEWAY_PROPERTY_NAME = "session.ipv4.gateway";
   private static final String ALLOWED_NETWORK_PROPERTY_NAME = "session.network";
   private static final String TERMINATE_ON_MISCONFIGURATION_PROPERTY_NAME = "session.terminate_on_misconfiguration";
   private static final String RESTRICTED_MODE_SERVER_PROPERTY_NAME = "session.required_server";
   private static final String MAXIMUM_NUMBER_OF_ALERTS_PROPERTY_NAME = "session.max_alerts";
   private static final String DISPLAY_ALERTS_PROPERTY_NAME = "session.display_alerts";
   private static final String CHECK_VPN_PROPERTY_NAME = "session.check_vpn";
   private static final String ALERT_TIMEOUT_IN_SECONDS = "session.alert.timeout";
   private static final String DISPLAY_POWER_ALERT_PROPERTY_NAME = "session.display_power_alert";
   private static final String VIRTUAL_MACHINE_INTERFACE_CHECK_FREQUENCY_IN_SECONDS_PROPERTY_NAME = "session.vm_check_interval";
   private static final String TERMINATE_ON_VPN_PROPERTY_NAME = "session.terminate_on_vpn";
   private static final String TERMINATE_ON_MONITORS_PROPERTY_NAME = "session.terminate_on_excessive_monitors";
   private static final String TERMINATE_ON_REMOVEABLE_VOLUMES_PROPERTY_NAME = "session.terminate_on_removeable_volumes";
   private static final String TERMINATE_ON_VIRTUAL_DISPLAYS_PROPERTY_NAME = "session.terminate_on_virtual_displays";
   private static final String TERMINATE_ON_AUTHENTICATION = "session.terminate_on_authentication";
   private static final String MAXIMUM_PASSWORD_ATTEMPTS = "session.maximum_password_attempts";
   private static final String LOCALHOST = "localhost";
   private static final String LOCAL_IPV4_ADDRESS = "127.0.0.1";
   private static final String DEFAULT_WIFI_SSID = "comas0001";
   private static final String DEFAULT_LOCAL_DNS_SERVER = "192.168.87.1";
   private static final String DEFAULT_LOCAL_IPV4_GATEWAY = "192.168.87.1";
   private static final String DEFAULT_IPV4_ADDRESS_PATTERN = "^192.168.87.[\\d]{1,3}$";
   private static final int ALLOWED_MAXIMUM_MONITORS = 1000;
   private static int MAXIMUM_ALERTS = Integer.MAX_VALUE;
   private static int MAXIMUM_TIMEOUT_IN_SECONDS = 3600;
   private static long TIME_BETWEEN_VIRTUAL_MACHINE_INTERFACE_CHECKS = 300000L;
   private static long TIME_BETWEEN_DYNAMIC_DNS_SERVER_CHECKS = 60000L;
   private static String RESTRICTED_MODE_SERVER = "comas-local.cogerent.com";
   private Mode mode;
   private IPv4ConfigurationMode allocationMode;
   public String allowed_dns_server;
   public String allowed_dns_domain_server;
   private Pattern ipv4_allowed;
   private String ipv4Gateway;
   public final Invigilator invigilator;
   public NetworkParams networkParams;
   public String[] initialDnsServers;
   private String networkName;
   private boolean initializeDNSCacheRequired;
   private final AtomicBoolean running;
   private int removeableDiskDetected;
   private boolean networkProblemDetected;
   private final ExtendedTimer timer;
   private SessionCheckTask sessionCheckTask;
   private StringBuffer report;
   private boolean power_alert_is_displayed;
   private boolean use_terminal_to_flush_dns;
   private boolean administratorPrivilegeIsRequired;
   private int maximum_monitors;
   private int number_of_alerts;
   private int numberOfMonitors;
   private AtomicBoolean alertIsActive;
   private int timeout;
   private int numberOfChecksPerformed;
   private NetworkIFManagerInterface networkIFManager;
   private boolean displayAlert;
   private NetworkIF vpn;
   private boolean check_vpn;
   private boolean terminate_on_vpn;
   private boolean terminate_on_excessive_monitors;
   private boolean terminate_on_authentication;
   private int maximum_password_attempts;
   private DNS dns;
   private final AtomicBoolean stopped;
   private final ArrayList vpnPattern;
   private PasswordDialog passwordDialog;
   StringBuffer finalReport;

   public SessionConfigurationModeMonitor(Invigilator invigilator) {
      this.passwordDialog = PasswordDialog.create(invigilator);
      this.stopped = new AtomicBoolean(false);
      this.dns = null;
      this.mode = SessionConfigurationModeMonitor.Mode.unspecified;
      Properties p = invigilator.getProperties();
      this.check_vpn = Utils.getBooleanOrDefault(p, "session.check_vpn", true);
      this.terminate_on_vpn = Utils.getBooleanOrDefault(p, "session.terminate_on_vpn", false);
      TIME_BETWEEN_VIRTUAL_MACHINE_INTERFACE_CHECKS = (long)(Utils.getIntegerOrDefaultInRange(p, "session.vm_check_interval", 300, 0, 2147483) * 1000);
      this.power_alert_is_displayed = Utils.getBooleanOrDefault(p, "session.display_power_alert", false);
      this.timeout = Utils.getIntegerOrDefaultInRange(p, "session.alert.timeout", 10, 0, MAXIMUM_TIMEOUT_IN_SECONDS) * 1000;
      this.maximum_monitors = Utils.getIntegerOrDefaultInRange(p, "session.maximum_monitors", 0, 1, 1000);
      this.terminate_on_excessive_monitors = Utils.getBooleanOrDefault(p, "session.terminate_on_excessive_monitors", false);
      MAXIMUM_ALERTS = Utils.getIntegerOrDefaultInRange(p, "session.max_alerts", MAXIMUM_ALERTS, 1, Integer.MAX_VALUE);
      this.number_of_alerts = 0;
      this.use_terminal_to_flush_dns = Utils.getBooleanOrDefault(p, "session.password_prompt", false);
      RESTRICTED_MODE_SERVER = Utils.getStringOrDefault(p, "session.required_server", RESTRICTED_MODE_SERVER);
      this.displayAlert = Utils.getBooleanOrDefault(p, "session.display_alerts", true);
      this.terminate_on_authentication = Utils.getBooleanOrDefault(p, "session.terminate_on_authentication", false);
      this.maximum_password_attempts = Utils.getIntegerOrDefaultInRange(p, "session.maximum_password_attempts", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
      String modeString = PropertyValue.getValue(invigilator, "session", "mode", new String[]{"local", "global", "restricted", "hybrid"}, SessionConfigurationModeMonitor.Mode.unspecified.toString());
      this.setMode(modeString);
      String dhcpString = PropertyValue.getValue(invigilator, "session", "ipv4.configure", new String[]{"dhcp", "static"}, SessionConfigurationModeMonitor.IPv4ConfigurationMode.dhcp.toString());
      this.setAllocationMode(dhcpString);
      if (this.allocationMode.isUnspecified()) {
         this.allocationMode = SessionConfigurationModeMonitor.IPv4ConfigurationMode.dhcp;
      }

      this.networkName = p.getProperty("session.network", "comas0001").trim();
      String dns_server = PropertyValue.getValue(p, "session", "dns", invigilator.getCourse(), invigilator.getActivity(), invigilator.getID());
      String dns_domain_server = PropertyValue.getValue(p, "session", "dns.domain", invigilator.getCourse(), invigilator.getActivity(), invigilator.getID());
      if (dns_domain_server != null) {
         this.allowed_dns_domain_server = dns_domain_server.trim();
         if (this.allowed_dns_domain_server.length() == 0) {
            this.allowed_dns_domain_server = null;
         }
      } else {
         this.allowed_dns_domain_server = null;
      }

      this.invigilator = invigilator;
      this.vpnPattern = new ArrayList();
      this.setupVPNPatterns();
      if (dns_server != null) {
         this.allowed_dns_server = dns_server.trim();

         try {
            InetAddress.getByName(this.allowed_dns_server);
         } catch (UnknownHostException e) {
            this.fallbackToAnotherMode("DNS server exception " + String.valueOf(e));
         }
      } else if (this.allowed_dns_domain_server != null && this.allowed_dns_domain_server.length() > 0) {
         try {
            this.allowed_dns_server = InetAddress.getByName(this.allowed_dns_domain_server).getHostAddress();
         } catch (UnknownHostException var11) {
         }
      }

      if (this.mode.isHybrid() && this.allowed_dns_server == null) {
         this.allowed_dns_server = "127.0.0.1";
      }

      String pattern = p.getProperty("session.ipv4.pattern", "^192.168.87.[\\d]{1,3}$").trim();

      try {
         this.ipv4_allowed = Pattern.compile(pattern);
      } catch (PatternSyntaxException e) {
         invigilator.logArchiver.put(Level.DIAGNOSTIC, "Session mode monitor could not compile an IPv4 pattern: " + String.valueOf(e), invigilator.createProblemSetEvent("software_configuration"));
         this.ipv4_allowed = Pattern.compile("^192.168.87.[\\d]{1,3}$");
      }

      this.ipv4Gateway = p.getProperty("session.ipv4.gateway", "192.168.87.1").trim();
      this.initializeDNSCacheRequired = false;
      this.timer = TimerService.create("SessionConfigurationModeMonitor");
      this.running = new AtomicBoolean(false);
      this.removeableDiskDetected = 0;
      this.networkProblemDetected = false;
      this.numberOfMonitors = 0;
      this.vpn = null;
      this.initializeNetworkParameters();
      this.administratorPrivilegeIsRequired = this.isHybrid() && ClientShared.isWindowsOS();
      this.initialDnsServers = this.networkParams.getDnsServers();
      this.alertIsActive = new AtomicBoolean(false);
      this.numberOfChecksPerformed = 0;

      try {
         if (TIME_BETWEEN_VIRTUAL_MACHINE_INTERFACE_CHECKS > 0L) {
            this.timer.scheduleAtFixedRate(new VirtualMachineCheckTimeoutTask(), TIME_BETWEEN_VIRTUAL_MACHINE_INTERFACE_CHECKS, TIME_BETWEEN_VIRTUAL_MACHINE_INTERFACE_CHECKS);
         }
      } catch (IllegalStateException e) {
         invigilator.logArchiver.put(Level.DIAGNOSTIC, "Session mode monitor could not schedule a check: " + String.valueOf(e), invigilator.createProblemSetEvent("software_configuration"));
      }

      this.networkIFManager = NetworkIFManagerFactory.create(this);
      this.sessionCheckTask = null;
   }

   private void setupVPNPatterns() {
      int i = 1;
      String base = "vpn.name.";
      String vpnString = this.invigilator.getProperty(base + i);

      while(vpnString != null) {
         boolean var8 = false;

         label49: {
            try {
               var8 = true;
               Pattern p = Pattern.compile(vpnString.trim());
               this.vpnPattern.add(p);
               var8 = false;
               break label49;
            } catch (PatternSyntaxException e) {
               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Session mode monitor could not compile a VPN pattern: " + String.valueOf(e), this.invigilator.createProblemSetEvent("software_configuration"));
               var8 = false;
            } finally {
               if (var8) {
                  ++i;
                  vpnString = this.invigilator.getProperty(base + i);
               }
            }

            ++i;
            vpnString = this.invigilator.getProperty(base + i);
            continue;
         }

         ++i;
         vpnString = this.invigilator.getProperty(base + i);
      }

   }

   public PasswordDialog getPasswordDialog() {
      return this.passwordDialog;
   }

   public boolean DNScheck() throws TextParseException, UnknownHostException {
      return this.allowed_dns_server == null ? true : this.DNScheck(this.allowed_dns_server);
   }

   private boolean DNScheck(String server) throws TextParseException, UnknownHostException {
      Name name = Name.fromString(ClientShared.COMPANY_DOMAIN);
      Lookup lookup = new Lookup(name, 1);
      lookup.setResolver(new SimpleResolver(server));
      boolean lookup_successful = false;
      Record[] records = lookup.run();
      if (records != null) {
         for(Record record : records) {
            if (record.getType() == 1) {
               lookup_successful = true;
            }
         }
      }

      return lookup_successful;
   }

   public void processDNSServerConfiguration() {
      if (this.allowed_dns_server != null && !this.allowed_dns_server.equals("127.0.0.1") && !this.allowed_dns_server.equals("localhost")) {
         try {
            this.DNScheck(this.allowed_dns_server);
         } catch (Exception e) {
            this.fallbackToAnotherMode("DNS server exception " + String.valueOf(e));
         }
      }

      boolean usingEmbeddedDNSServer = false;
      if (this.mode.isHybrid() && this.allowed_dns_server != null && (this.allowed_dns_server.equals("127.0.0.1") || this.allowed_dns_server.equals("localhost"))) {
         this.allowed_dns_server = this.invigilator.getHardwareAndSoftwareMonitor().getIPv4Address();
         String dns_server_configuration = PropertyValue.getValue(this.invigilator.getProperties(), "session", "dns.configuration", this.invigilator.getCourse(), this.invigilator.getActivity(), this.invigilator.getID());
         if (dns_server_configuration != null) {
            String resolvedConfiguration = dns_server_configuration.trim();

            try {
               resolvedConfiguration = this.invigilator.resolveFolderVariables(resolvedConfiguration);
               if (!resolvedConfiguration.startsWith("http")) {
                  resolvedConfiguration = ClientShared.CMS_URL + resolvedConfiguration;
               }

               this.dns = new DNS(resolvedConfiguration, "Cookie", "token=" + this.invigilator.getToken(), this.timer, this.initialDnsServers);
               if (this.dns.isViable()) {
                  this.dns.start();
                  if (!this.dns.isStarted()) {
                     this.dns = null;
                     String var10001 = ClientShared.getOSDisplayString();
                     this.fallbackToAnotherMode("A local DNS server could not be started on " + var10001 + " " + this.invigilator.getHardwareAndSoftwareMonitor().getOS());
                  } else {
                     this.allowed_dns_domain_server = null;
                     usingEmbeddedDNSServer = true;
                  }
               } else {
                  this.dns = null;
                  this.fallbackToAnotherMode("Configuration of a local DNS server has no cache or resolvers for " + resolvedConfiguration);
               }
            } catch (IOException e) {
               this.fallbackToAnotherMode("Configuration of a local DNS server unavailable for " + resolvedConfiguration + ". Exception was " + String.valueOf(e));
            }
         } else {
            this.fallbackToAnotherMode("Configuration of a local DNS server not found");
         }
      }

      if (this.allowed_dns_server == null && this.mode.isUnspecified()) {
         this.setMode(SessionConfigurationModeMonitor.Mode.global);
      }

      if (this.mode.isHybrid() && this.allowed_dns_domain_server != null && this.allowed_dns_server != null && !usingEmbeddedDNSServer) {
         try {
            this.timer.scheduleAtFixedRate(new DNSDomainServerCheckTask(), TIME_BETWEEN_DYNAMIC_DNS_SERVER_CHECKS, TIME_BETWEEN_DYNAMIC_DNS_SERVER_CHECKS);
         } catch (IllegalStateException e) {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Session mode monitor could not schedule a check: " + String.valueOf(e), this.invigilator.createProblemSetEvent("software_configuration"));
         }
      }

      this.administratorPrivilegeIsRequired = this.isHybrid() && ClientShared.isWindowsOS();
   }

   public void fallbackToAnotherMode(String message) {
      String backup = PropertyValue.getValue(this.invigilator.getProperties(), "session", "dns.backup", this.invigilator.getCourse(), this.invigilator.getActivity(), this.invigilator.getID());
      String backup_domain = PropertyValue.getValue(this.invigilator.getProperties(), "session", "dns.backup.domain", this.invigilator.getCourse(), this.invigilator.getActivity(), this.invigilator.getID());
      Mode desired_mode = this.mode;
      if (backup == null && backup_domain == null) {
         this.setMode(SessionConfigurationModeMonitor.Mode.global);
      }

      if (backup != null) {
         backup = backup.trim();
         if (!backup.equals("localhost") && !backup.equals("127.0.0.1")) {
            try {
               InetAddress.getByName(backup);
               this.allowed_dns_server = backup;
            } catch (UnknownHostException var8) {
               this.setMode(SessionConfigurationModeMonitor.Mode.global);
            }
         } else {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Cannot use localhost for backup.");
            this.setMode(SessionConfigurationModeMonitor.Mode.global);
         }
      }

      if (backup_domain != null) {
         backup_domain = backup_domain.trim();
         if (!backup_domain.equals("localhost") && !backup_domain.equals("127.0.0.1")) {
            try {
               InetAddress backup_address = InetAddress.getByName(backup_domain);
               this.allowed_dns_domain_server = backup_domain;
               if (backup == null) {
                  this.allowed_dns_server = backup_address.getHostAddress();
               }
            } catch (UnknownHostException var7) {
               this.setMode(SessionConfigurationModeMonitor.Mode.global);
            }
         } else {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Cannot use localhost for backup.");
            this.setMode(SessionConfigurationModeMonitor.Mode.global);
         }
      }

      try {
         this.DNScheck();
      } catch (Exception var6) {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, message + ". Backup DNS is unreachable", this.invigilator.createProblemSetEvent("network_configuration"));
         this.setMode(SessionConfigurationModeMonitor.Mode.global);
      }

      if (desired_mode != this.mode) {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, message + ". Mode is now " + String.valueOf(this.mode), this.invigilator.createProblemSetEvent("network_configuration"));
      }

   }

   public StringBuffer getReport() {
      return this.report;
   }

   public void setReport(StringBuffer report) {
      this.report = report;
   }

   public Mode getMode() {
      return this.mode;
   }

   public void setMode(String modeString) {
      this.mode = SessionConfigurationModeMonitor.Mode.parse(modeString);
   }

   public IPv4ConfigurationMode getAllocationMode() {
      return this.allocationMode;
   }

   public void setAllocationMode(String modeString) {
      this.allocationMode = SessionConfigurationModeMonitor.IPv4ConfigurationMode.parse(modeString);
   }

   public void setMode(Mode mode) {
      this.mode = mode;
   }

   public void initializeNetworkParameters() {
      this.networkParams = this.invigilator.getHardwareAndSoftwareMonitor().getNetworkParams();
   }

   public void schedule() {
      try {
         if (this.sessionCheckTask != null) {
            this.sessionCheckTask.ignore();
            this.sessionCheckTask.cancel();
         }

         this.sessionCheckTask = new SessionCheckTask(this);
         this.timer.scheduleRandomRepeating(this.sessionCheckTask, (long)(ClientShared.MIN_INTERVAL * 1000), (long)(ClientShared.MAX_INTERVAL * 1000));
      } catch (IllegalArgumentException | NullPointerException | IllegalStateException e) {
         Logger.log(Level.WARNING, "Timer could not schedule: ", e);
      }

   }

   public SessionCheckTask getSessionCheckTask() {
      return this.sessionCheckTask;
   }

   public void finalCheck() {
      if (this.running.compareAndSet(false, true)) {
         try {
            this.finalReport = new StringBuffer();
            this.windowCheck(this.finalReport);
            this.browserCheck(this.finalReport);
         } finally {
            this.running.set(false);
         }
      }

   }

   public void check() {
      if (this.running.compareAndSet(false, true)) {
         try {
            this.initializeNetworkParameters();
            this.report = new StringBuffer();
            this.filesystemCheck(this.report);
            this.monitorCheck(this.report);
            this.windowCheck(this.report);
            this.processCheck(this.report);
            this.browserCheck(this.report);
            this.powerCheck(this.report);
            this.vpnCheck(this.report);
            if (this.isLocal()) {
               this.doLocalStart(this.report);
            } else if (this.isRestricted()) {
               this.doRestrictedStart(this.report);
            } else if (this.isHybrid()) {
               this.doHybridStart(this.report);
            }

            this.process(this.report);
            this.passwordCheck();
         } finally {
            this.running.set(false);
         }
      }

   }

   private void passwordCheck() {
      int passwordAttempts = this.passwordDialog.getCount();
      if (passwordAttempts > this.maximum_password_attempts && this.terminate_on_authentication) {
         DisappearingAlert da = new DisappearingAlert((long)ClientShared.DISAPPEARING_ALERT_TIMEOUT, 1, 0);
         String msg = "Too many incorrect passwords entered.\n\nYour session will now end";
         this.invigilator.setInvigilatorState(InvigilatorState.ended);
         Runnable action = new SessionEndTask(this.invigilator, ProgressServlet.getSingleton(), "Session ended because " + this.invigilator.getNameAndID() + " did not provide a correct password");
         da.setRunOnCloseRegardless(action);
         da.show(msg);
      }

   }

   private void process(StringBuffer sb) {
      if (sb.length() > 0) {
         this.alert(sb.toString());
      }

   }

   private void vpnCheck(StringBuffer sb) {
      if (this.check_vpn) {
         NetworkIF old_vpn = this.vpn;
         this.vpn = this.networkInterfaceRunningVPN();
         if (this.vpn != null && old_vpn == null) {
            String msg = "\nYou appear to be running a VPN on " + this.invigilator.getHardwareAndSoftwareMonitor().getIPAddressesString(this.vpn.getIPv4addr()) + "\nACTION: You should disconnect the VPN before proceeding";
            sb.append(msg);
            sb.append("\n");
            this.invigilator.logArchiver.put(Level.LOGGED, msg, this.invigilator.createProblemSetEvent("vpn_detected"));
         } else if (old_vpn != null && this.vpn == null) {
            String msg = "VPN disconnected";
            this.invigilator.logArchiver.put(Level.LOGGED, msg, this.invigilator.createProblemClearEvent("vpn_detected"));
         }

      }
   }

   public boolean displayCheckCommon(StringBuffer sb, List displays) {
      boolean first = true;
      StringBuffer sb_msg = new StringBuffer();

      for(DisplayInfo di : displays) {
         if (DisplayDetectorPro.isSuspicious(di)) {
            if (first) {
               first = false;
               sb_msg.append("\nA virtual display may have been detected:\n");
            } else {
               sb_msg.append(",\n");
            }

            Set<String> reasons = DisplayDetectorPro.reasonsFor(di);
            sb_msg.append(di);
            if (!reasons.isEmpty()) {
               sb_msg.append(" ");
               sb_msg.append(String.join("; ", reasons));
            }
         }
      }

      if (!first) {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, sb_msg.toString(), this.invigilator.createProblemSetEvent("vm_display"));
         sb.append(sb_msg.toString());
         sb.append("\nACTION: Please disable the display before continuing");
      }

      return first;
   }

   public boolean displayCheck(StringBuffer sb) {
      List<Display> displays = this.invigilator.getHardwareAndSoftwareMonitor().getDisplays();
      if (displays != null && !displays.isEmpty()) {
         List<DisplayInfo> _displays = new ArrayList();

         for(Display display : displays) {
            byte[] edid = display.getEdid();
            DisplayInfo di = DisplayDetectorPro.parseEDID(edid);
            _displays.add(di);
         }

         return this.displayCheckCommon(sb, _displays);
      } else {
         return true;
      }
   }

   public boolean displayCheckPro(StringBuffer sb) {
      List<DisplayInfo> displays;
      try {
         displays = DisplayDetectorPro.getDisplays();
      } catch (Exception var4) {
         displays = null;
      }

      return displays != null && !displays.isEmpty() ? this.displayCheckCommon(sb, displays) : true;
   }

   private void filesystemCheck(StringBuffer sb) {
      String[] media = this.invigilator.getHardwareAndSoftwareMonitor().hasRemoveableMedia();
      if (media != null) {
         if (media.length > 0) {
            sb.append("\nOne or more removeable disk(s) have been detected: ");
            String allDisks = "";
            boolean first = true;

            for(String m : media) {
               if (first) {
                  first = false;
                  if (media.length > 1) {
                     sb.append("\n");
                  }
               } else {
                  sb.append(", ");
                  allDisks = allDisks + ", ";
               }

               sb.append(m);
               allDisks = allDisks + m;
            }

            sb.append("\nACTION: Eject these device(s) before continuing.\n\n");
            if (this.removeableDiskDetected != media.length) {
               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Removeable disk(s) detected: " + allDisks, this.invigilator.createProblemSetEvent("removeable_disk"));
               if (this.invigilator.isInitialized()) {
                  this.removeableDiskDetected = media.length;
               }
            }
         } else if (this.removeableDiskDetected != 0) {
            this.removeableDiskDetected = 0;
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "No Removeable disks detected", this.invigilator.createProblemClearEvent("removeable_disk"));
         }
      }

   }

   private void monitorCheck(StringBuffer sb) {
      if (this.maximum_monitors != 0) {
         int numberOfDisplays = Displays.getNumberOfDisplays();
         if (numberOfDisplays > this.maximum_monitors) {
            sb.append("\nThere are too many displays attached: ");
            sb.append(numberOfDisplays);
            sb.append(". Only ");
            sb.append(this.maximum_monitors);
            if (this.maximum_monitors == 1) {
               sb.append(" is allowed.\n");
            } else {
               sb.append(" are allowed.\n");
            }

            sb.append("ACTION: Disconnect ");
            int numberToDisconnect = numberOfDisplays - this.maximum_monitors;
            sb.append(numberToDisconnect);
            if (numberToDisconnect > 1) {
               sb.append(" displays\n\n");
            } else {
               sb.append(" display\n\n");
            }

            if (this.numberOfMonitors != numberOfDisplays) {
               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "There are too many displays attached:" + numberOfDisplays, this.invigilator.createProblemSetEvent("excessive_monitors"));
               if (this.invigilator.isInitialized()) {
                  this.numberOfMonitors = numberOfDisplays;
               }
            }
         } else if (this.numberOfMonitors != 0 && this.numberOfMonitors != numberOfDisplays) {
            this.numberOfMonitors = numberOfDisplays;
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Number of displays okay: " + this.numberOfMonitors, this.invigilator.createProblemClearEvent("excessive_monitors"));
         }

      }
   }

   private void powerCheck(StringBuffer sb) {
      if (this.power_alert_is_displayed) {
         if (this.invigilator.getHardwareAndSoftwareMonitor().getPowerHealth(this.invigilator).isRed()) {
            this.power_alert_is_displayed = false;
            String powerMsg = String.format("You have less than %d mins of power", this.invigilator.getHardwareAndSoftwareMonitor().getTimeRemainingEstimate());
            (new DisappearingAlert((long)ClientShared.DISAPPEARING_ALERT_TIMEOUT, 1, 2)).show(powerMsg, "CoMaS Power Information Alert");
         }

      }
   }

   private void windowCheck(StringBuffer sb) {
      HardwareAndSoftwareMonitor.OSProcessAction[] deniedWindows = this.invigilator.getHardwareAndSoftwareMonitor().deniedDesktopWindows();
      if (deniedWindows != null && deniedWindows.length > 0) {
         OSDesktopWindow[] allowedWindows = this.invigilator.getHardwareAndSoftwareMonitor().allowedWindows();
         ReportManager rm = this.invigilator.getReportManager();
         boolean first = true;
         StringBuffer wbuf = new StringBuffer();

         for(HardwareAndSoftwareMonitor.OSProcessAction pa : deniedWindows) {
            OSDesktopWindow dw = pa.window;
            OSProcess dp = pa.process;
            if (!this.invigilator.getHardwareAndSoftwareMonitor().isWindowOfInterest(dw, allowedWindows)) {
               if (first) {
                  first = false;
                  sb.append("\nThere are windows open that should not be:\n");
               } else {
                  wbuf.append(",\n");
               }

               wbuf.append(dw.getTitle());
               rm.annotateReport(dw);
               if (dp != null && rm.reportWindowAsProcess()) {
                  rm.annotateReport(dp);
               }
            }
         }

         sb.append(wbuf.toString());
         if (deniedWindows.length > 1) {
            sb.append("\nACTION: Close these windows while running CoMaS\n");
         } else {
            sb.append("\nACTION: Close " + deniedWindows[0].window.getTitle() + " while running CoMaS\n");
         }

         boolean windowsHaveChanged = rm.reportProcessorIsChanged("reported_windows");
         rm.resetReportProcessorChanged("reported_windows");
         if (this.invigilator.isInInvigilatorState(InvigilatorState.running)) {
            if (!rm.hasProblemWithSetStatus("denied_windows")) {
               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Application windows (" + deniedWindows.length + ") detected:\n" + wbuf.toString(), this.invigilator.createProblemSetEvent("denied_windows"));
            } else if (windowsHaveChanged) {
               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Application windows (" + deniedWindows.length + ") detected:\n" + wbuf.toString(), this.invigilator.createProblemUpdateEvent("denied_windows"));
            }
         }
      } else if (this.invigilator.getReportManager().hasProblemWithSetStatus("denied_windows")) {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Application windows okay", this.invigilator.createProblemClearEvent("denied_windows"));
      }

   }

   private void browserCheck(StringBuffer sb) {
      BrowserHistoryReader.HistoryEntry[] deniedPages = this.invigilator.getHardwareAndSoftwareMonitor().deniedPages();
      if (deniedPages != null && deniedPages.length > 0) {
         ReportManager rm = this.invigilator.getReportManager();
         boolean first = true;
         StringBuffer wbuf = new StringBuffer();
         URI uri = null;

         for(BrowserHistoryReader.HistoryEntry dp : deniedPages) {
            if (first) {
               first = false;
               if (deniedPages.length > 1) {
                  sb.append("\nThe following ");
                  sb.append(deniedPages.length);
                  sb.append(" pages have been accessed:\n");
               } else {
                  sb.append("\nThe following page has been accessed:\n");
               }
            } else {
               wbuf.append(",\n");
            }

            try {
               uri = new URI(dp.getUrl());
               wbuf.append(uri.getScheme());
               wbuf.append(":");
               wbuf.append("//");
               wbuf.append(uri.getAuthority());
               wbuf.append(uri.getPath());
            } catch (URISyntaxException var12) {
               wbuf.append(dp.getUrl());
            }

            rm.annotateReport(dp);
         }

         sb.append(wbuf.toString());
         if (deniedPages.length > 1) {
            sb.append("\nACTION: Do not access these pages while running CoMaS\n");
         } else {
            sb.append("\nACTION: Do not access ");
            if (uri != null) {
               sb.append(uri.getScheme());
               sb.append(":");
               sb.append("//");
               sb.append(uri.getAuthority());
               sb.append(uri.getPath());
            } else {
               sb.append(deniedPages[0].getUrl());
            }

            sb.append(" while running CoMaS\n");
         }

         boolean pagesHaveChanged = rm.reportProcessorIsChanged("reported_pages");
         rm.resetReportProcessorChanged("reported_pages");
         if (!rm.hasProblemWithSetStatus("denied_pages")) {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Browser pages (" + deniedPages.length + ") detected:\n" + wbuf.toString(), rm.createProblemEvent("denied_pages", ProblemStatus.set(), deniedPages));
         } else if (pagesHaveChanged) {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Browser pages (" + deniedPages.length + ") detected:\n" + wbuf.toString(), rm.createProblemEvent("denied_pages", ProblemStatus.update(), deniedPages));
         }
      } else if (this.invigilator.getReportManager().hasProblemWithSetStatus("denied_pages")) {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Application windows okay", this.invigilator.createProblemClearEvent("denied_pages"));
      }

   }

   private void processCheck(StringBuffer sb) {
      HardwareAndSoftwareMonitor.OSProcessAction[] deniedProcesses = this.invigilator.getHardwareAndSoftwareMonitor().deniedProcesses();
      ArrayList<HardwareAndSoftwareMonitor.OSProcessAction> filteredDeniedProcesses = new ArrayList();
      int countOfSuspended = 0;
      boolean firstAll = true;
      StringBuffer allProcesses = new StringBuffer();
      ReportManager rm = this.invigilator.getReportManager();
      if (deniedProcesses != null) {
         for(HardwareAndSoftwareMonitor.OSProcessAction pa : deniedProcesses) {
            if (!pa.hasBeenSuspended()) {
               filteredDeniedProcesses.add(pa);
            } else if (pa.isSuspend()) {
               ++countOfSuspended;
               if (firstAll) {
                  firstAll = false;
               } else {
                  allProcesses.append(", ");
               }

               allProcesses.append(pa.toDisplay());
            }
         }
      }

      int workToDo = filteredDeniedProcesses.size();
      if (workToDo > 0) {
         HashSet<String> displayed = new HashSet();
         boolean first = true;
         boolean action = false;

         for(HardwareAndSoftwareMonitor.OSProcessAction pa : filteredDeniedProcesses) {
            if (!pa.hasBeenSuspended()) {
               if (first) {
                  first = false;
                  sb.append("\nThere are processes running that should not be:\n");
               } else if (!displayed.contains(pa.process.getName())) {
                  sb.append(",\n");
               }

               if (firstAll) {
                  firstAll = false;
               } else {
                  allProcesses.append(", ");
               }

               allProcesses.append(pa.toDisplay());
               if (!displayed.contains(pa.process.getName())) {
                  sb.append(pa.process.getName());
               }

               displayed.add(pa.process.getName());
            }

            if (pa.perform()) {
               action = true;
            }
         }

         if (workToDo > 1) {
            sb.append("\nACTION: Quit these processes while running CoMaS\n\n");
         } else if (workToDo == 1) {
            sb.append("\nACTION: Quit " + ((HardwareAndSoftwareMonitor.OSProcessAction)filteredDeniedProcesses.get(0)).process.getName() + " while running CoMaS\n\n");
         }

         rm.resetReportProcessorChanged("reported_processes");
         if (this.invigilator.isInInvigilatorState(InvigilatorState.running)) {
            if (!rm.hasProblemWithSetStatus("denied_processes")) {
               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Problem processes (" + filteredDeniedProcesses.size() + ") detected: " + String.valueOf(allProcesses), this.invigilator.createProblemSetEvent("denied_processes"));
            } else if (action) {
               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Problem processes (" + filteredDeniedProcesses.size() + ") detected: " + String.valueOf(allProcesses), this.invigilator.createProblemUpdateEvent("denied_processes"));
            }
         }
      } else if (rm.hasProblemWithSetStatus("denied_processes")) {
         String msg;
         if (countOfSuspended > 1) {
            msg = "No problem processes detected; however, " + countOfSuspended + " processes are suspended";
         } else if (countOfSuspended == 1) {
            msg = "No problem processes detected; however, " + countOfSuspended + " process is suspended";
         } else {
            msg = "No problem processes detected";
         }

         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, msg, this.invigilator.createProblemClearEvent("denied_processes"));
      } else if (this.invigilator.isInInvigilatorState(InvigilatorState.running) && countOfSuspended > 0 && !rm.hasProblemOccurred("denied_processes", 1)) {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Suspended problem processes (" + countOfSuspended + ") detected: " + String.valueOf(allProcesses), this.invigilator.createProblemSetEvent("denied_processes"));
      }

   }

   public void initializeProcesses(boolean start, int type) {
      HashSet<String> processNames = new HashSet();
      HashSet<String> allProcessNames = new HashSet();
      boolean studentHasProcessesToQuit = true;
      boolean hasProcessesToInitialize = true;
      boolean first = true;

      while(studentHasProcessesToQuit || hasProcessesToInitialize) {
         studentHasProcessesToQuit = false;
         processNames.clear();
         allProcessNames.clear();
         HardwareAndSoftwareMonitor.OSProcessAction[] initializedProcesses;
         if (start) {
            initializedProcesses = this.invigilator.getHardwareAndSoftwareMonitor().initializedProcesses();
            hasProcessesToInitialize = initializedProcesses.length > 0;
         } else {
            hasProcessesToInitialize = false;
            initializedProcesses = this.invigilator.getHardwareAndSoftwareMonitor().finalizedProcesses();
         }

         if (initializedProcesses != null && initializedProcesses.length > 0) {
            for(HardwareAndSoftwareMonitor.OSProcessAction pa : initializedProcesses) {
               if (pa.action.isQuit()) {
                  studentHasProcessesToQuit = true;
                  processNames.add(pa.process.getName());
               } else {
                  pa.perform();
               }

               allProcessNames.add(pa.process.getName());
            }

            if (processNames.size() > 0) {
               final CountDownLatch cdl = new CountDownLatch(1);
               Runnable cdlr = new Runnable() {
                  public void run() {
                     cdl.countDown();
                  }
               };
               String phrase = processNames.size() == 1 ? "application" : "applications";
               StringBuffer sb = new StringBuffer("You must quit the following ");
               sb.append(phrase);
               sb.append(" before proceeding:\n\n");

               for(String processName : processNames) {
                  sb.append(processName);
                  sb.append("\n");
                  phrase = processName;
               }

               if (processNames.size() > 1) {
                  phrase = "applications";
               }

               String quitCharacterString;
               if (ClientShared.isMacOS()) {
                  quitCharacterString = "(⌘Q)";
               } else if (ClientShared.isWindowsOS()) {
                  quitCharacterString = "(Alt+F4)";
               } else {
                  quitCharacterString = "(Alt+F4)";
               }

               if (start) {
                  sb.append(String.format("\nACTION: Quit %s %s then press OK. Press Cancel to end session", quitCharacterString, phrase));
               } else {
                  sb.append(String.format("\nACTION: Quit %s %s then press OK", quitCharacterString, phrase));
               }

               DisappearingAlert da = new DisappearingAlert((long)this.timeout);
               da.setRunOnCloseRegardless(cdlr);
               da.setOptionType(type);
               da.show(sb.toString());

               try {
                  cdl.await((long)this.timeout, TimeUnit.MILLISECONDS);
               } catch (InterruptedException var16) {
               }

               if (da.getChoice() == 2) {
                  this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Refused to quit applications");
                  this.invigilator.setState("Terminated");
                  this.invigilator.endTheSession();
                  System.exit(0);
               }
            }

            if (first) {
               String verbPhrase = "was %d application";
               if (allProcessNames.size() > 1) {
                  verbPhrase = "were %d applications";
               }

               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, String.format("There " + verbPhrase + " initialized: %s", allProcessNames.size(), String.join(",", allProcessNames)));
               first = false;
            }
         }
      }

   }

   public void finalizeProcesses() {
      this.initializeProcesses(false, -1);
   }

   public void initializeProcesses() {
      this.initializeProcesses(true, 2);
   }

   public String report() {
      return this.report != null && this.report.length() != 0 ? this.report.toString() : "Things appear to be fine. Nothing to report here.";
   }

   private StringBuffer doLocalStart(StringBuffer sb) {
      boolean networkProblem = false;
      String[] dnsServers = this.networkParams.getDnsServers();
      if (dnsServers.length > 1) {
         sb.append("\nToo many DNS servers configured\n");
         networkProblem = true;
      } else if (dnsServers.length == 1 && !dnsServers[0].equals(this.allowed_dns_server)) {
         sb.append("\nYou have an incorrectly configured DNS server: " + dnsServers[0] + "\n");
         networkProblem = true;
      }

      if (!this.networkParams.getIpv4DefaultGateway().equals(this.ipv4Gateway)) {
         sb.append("You have an incorrectly configured IPv4 gateway: " + this.networkParams.getIpv4DefaultGateway() + "\n");
         networkProblem = true;
      }

      NetworkIF[] networkIF = this.invigilator.getHardwareAndSoftwareMonitor().getInterfaceSpecs();

      for(NetworkIF netIF : networkIF) {
         String[] var12;
         for(String ipAddress : var12 = netIF.getIPv4addr()) {
            if (!ipAddress.equals("127.0.0.1")) {
               Matcher m = this.ipv4_allowed.matcher(ipAddress);
               if (!m.matches()) {
                  sb.append("Misconfigured IPV4 address detected: " + ipAddress + "\n");
                  networkProblem = true;
               }
            }
         }
      }

      if (!this.dnsResolutionCheck(sb)) {
         networkProblem = true;
      }

      if (networkProblem) {
         sb.append("\n\nDIAGNOSIS: You are not connected to the required network (" + this.networkName + ")");
         sb.append("\nACTION: You should connect to the " + this.networkName + " network\n");
      }

      if (networkProblem) {
         if (!this.networkProblemDetected) {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, sb.toString(), this.invigilator.createProblemSetEvent("network_configuration"));
         }

         if (this.invigilator.isInitialized()) {
            this.networkProblemDetected = true;
         }
      } else if (this.networkProblemDetected) {
         this.networkProblemDetected = false;
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Network correctly configured", this.invigilator.createProblemClearEvent("network_configuration"));
      }

      this.initializeDNSCache();
      return sb;
   }

   private void doHybridStart(StringBuffer sb) {
      this.dnsResolutionCheck(sb);
      this.initializeDNSCache();
      boolean networkProblem = false;
      this.setupDns(sb);
      this.initializeNetworkParameters();
      String[] dnsServers = this.networkParams.getDnsServers();
      if (dnsServers.length > 1) {
         sb.append("\nToo many DNS servers configured for hybrid mode\n");
         networkProblem = true;
      } else if (dnsServers.length == 1 && !dnsServers[0].equals(this.allowed_dns_server)) {
         sb.append("\nYour DNS server is incorrect for hybrid mode: " + dnsServers[0] + "\n");
         networkProblem = true;
      }

      if (networkProblem) {
         sb.append("\nDIAGNOSIS: DNS servers need to be reconfigured");
         sb.append("\nACTION: CoMaS will set your DNS server to ");
         sb.append(this.allowed_dns_server);
         sb.append("\n");
         if (!this.networkProblemDetected) {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, sb.toString(), this.invigilator.createProblemSetEvent("network_configuration"));
         }

         if (this.invigilator.isInitialized()) {
            this.networkProblemDetected = true;
         }
      } else if (this.networkProblemDetected) {
         this.networkProblemDetected = false;
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Network correctly configured", this.invigilator.createProblemClearEvent("network_configuration"));
      }

   }

   public void checkAndLogWiFiChange() {
      if (this.isHybrid()) {
         NetworkIF[] nif = this.invigilator.getHardwareAndSoftwareMonitor().getInterfaceSpecsWithIpv4Address();
         if (nif != null && nif.length > 0) {
            String ssidOnReset = this.networkIFManager.getWiFiSSID(nif[0]);
            String ssid = this.networkIFManager.getSSID();
            if (ssid != null && ssid.length() > 0 && !ssidOnReset.equals(ssid)) {
               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "WiFi network changed from " + ssid + " to " + ssidOnReset);
            }
         }
      }

   }

   private void resetDns() {
      if (this.isHybrid()) {
         NetworkIF[] nif = this.invigilator.getHardwareAndSoftwareMonitor().getInterfaceSpecsWithIpv4Address();
         if (nif != null && nif.length > 0) {
            this.networkIFManager.resetDnsServers(nif[0], this.initialDnsServers, this.allocationMode.isDHCP());
         }
      }

   }

   private void setupDns(StringBuffer sb) {
      if (this.isHybrid()) {
         String[] dnsServers = this.networkParams.getDnsServers();
         NetworkIF[] nif = this.invigilator.getHardwareAndSoftwareMonitor().getInterfaceSpecsWithIpv4Address();
         if (this.vpn != null) {
            this.protectedSetDnsServers(nif);
         }

         if (dnsServers.length == 1 && dnsServers[0].equals(this.allowed_dns_server)) {
            if (nif.length > 0 && this.networkIFManager.getIpV6State(nif[0]).equals("on")) {
               sb.append("\nThis session has IPv6 access. This will be disabled");
               this.networkIFManager.setIpV6State(nif[0], "off");
            }
         } else if (nif.length == 0) {
            sb.append("\nCannot find a valid IPv4 address for this device. Cannot run in hybrid mode");
         } else {
            if (nif.length > 1) {
               sb.append("\nThere are multiple IPv4 addresses for this device:\n");

               for(NetworkIF n_if : nif) {
                  sb.append(this.invigilator.getHardwareAndSoftwareMonitor().getIPAddressesString(n_if.getIPv4addr()));
                  sb.append(" ");
               }

               sb.append("\nUsing ");
               sb.append(this.invigilator.getHardwareAndSoftwareMonitor().getIPAddressesString(nif[0].getIPv4addr()));
            }

            this.protectedSetDnsServers(nif);
            String ssid = this.networkIFManager.getSSID();
            if (ssid.length() > 0) {
               String msg = "WiFi network is " + ssid;
               if (this.vpn != null) {
                  msg = msg + ", VPN is active";
               }

               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, msg);
            } else if (this.vpn != null) {
               this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "VPN is active");
            }
         }
      }

   }

   private void protectedSetDnsServers(NetworkIF[] nif) {
      try {
         this.invigilator.examArchiver.lock();
         this.invigilator.screenShotArchiver.lock();
         this.invigilator.logArchiver.lock();
         this.invigilator.stateDistributor.lock();
         this.networkIFManager.setDnsServers(nif[0], this.allowed_dns_server);
      } finally {
         this.invigilator.stateDistributor.unlock();
         this.invigilator.logArchiver.unlock();
         this.invigilator.screenShotArchiver.unlock();
         this.invigilator.examArchiver.unlock();
      }

   }

   private void changeVirtualMachineNetworkInterfaces(boolean enable) {
      try {
         String prompt = "CoMaS: A virtual machine network adapter needs to be " + (enable ? "enabled" : "disabled");
         NetworkIF[] nifs = this.invigilator.getHardwareAndSoftwareMonitor().getInterfaceSpecs();

         for(NetworkIF nif : nifs) {
            if (nif.isKnownVmMacAddr()) {
               this.networkIFManager.changeNetworkInterfaceState(nif, enable, prompt);
            }
         }
      } catch (Exception var8) {
      }

   }

   public boolean isRunningVPN() {
      return this.vpn != null;
   }

   public NetworkIF getVPN() {
      return this.vpn;
   }

   public boolean terminateIfVirtualDisplays() {
      return Utils.getBooleanOrDefault(this.invigilator.getProperties(), "session.terminate_on_virtual_displays", false);
   }

   public boolean terminateIfRemoveableVolumes() {
      return Utils.getBooleanOrDefault(this.invigilator.getProperties(), "session.terminate_on_removeable_volumes", false);
   }

   public boolean terminateIfVPNRunning() {
      return this.terminate_on_vpn;
   }

   public boolean terminateIfExcessiveMonitors() {
      return this.terminate_on_excessive_monitors;
   }

   public boolean terminateIfNoAuthentication() {
      return this.terminate_on_authentication;
   }

   public boolean isAdministratorPrivilegeRequired() {
      return this.administratorPrivilegeIsRequired;
   }

   public int excessMonitors() {
      return this.maximum_monitors == 0 ? 0 : Displays.getNumberOfDisplays() - this.maximum_monitors;
   }

   public boolean shouldTerminateBecauseOfVPN() {
      return this.isRunningVPN() && this.terminateIfVPNRunning();
   }

   public NetworkIF networkInterfaceRunningVPN() {
      NetworkIF[] nifs = this.invigilator.getHardwareAndSoftwareMonitor().getInterfaceSpecs();

      for(NetworkIF nif : nifs) {
         if (nif.getIPv4addr().length != 0) {
            if (this.networkIFManager.isVPN(nif)) {
               return nif;
            }

            for(Pattern p : this.vpnPattern) {
               Matcher m = p.matcher(nif.getDisplayName());
               if (m.matches()) {
                  return nif;
               }
            }
         }
      }

      return null;
   }

   public void logReportContent() {
      if (this.report != null && this.report.length() > 0) {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, this.report.toString());
      }

   }

   private boolean dnsResolutionCheck(StringBuffer sb) {
      int i = 1;
      boolean rtn = true;
      boolean first = true;

      for(String hostToCheck = this.invigilator.getProperty("session.network.host." + i); hostToCheck != null; hostToCheck = this.invigilator.getProperty("session.network.host." + i)) {
         if (this.canResolveHost(hostToCheck)) {
            if (first) {
               first = false;
               sb.append("You seem to know about locations you should not know about:\n");
            }

            sb.append(" - Host " + hostToCheck + " could be resolved\n");
            this.initializeDNSCacheRequired = true;
            rtn = false;
         }

         ++i;
      }

      return rtn;
   }

   public boolean isUsingTerminal() {
      return this.use_terminal_to_flush_dns;
   }

   public void setUsingTerminal() {
      this.use_terminal_to_flush_dns = true;
   }

   public void initializeDNSCache() {
      if (this.initializeDNSCacheRequired) {
         this.networkIFManager.initializeDnsCache();
      }

   }

   public boolean isDNSInitializationRequired() {
      return this.initializeDNSCacheRequired;
   }

   private boolean canResolveHost(String host) {
      if (host == null) {
         return false;
      } else {
         try {
            InetAddress.getByName(host);
            return true;
         } catch (UnknownHostException var3) {
            return false;
         }
      }
   }

   private StringBuffer doRestrictedStart(StringBuffer sb) {
      if (!ClientShared.DIRECTORY_HOST.equals(RESTRICTED_MODE_SERVER)) {
         sb.append("Restricted mode requires that ");
         sb.append(RESTRICTED_MODE_SERVER);
         sb.append(" be used as CoMaS server.\nYou have selected ");
         sb.append(ClientShared.DIRECTORY_HOST);
         sb.append("\n\nDIAGNOSIS: You have selected the wrong CoMaS server");
         sb.append("\nACTION: Restart and select ");
         sb.append(RESTRICTED_MODE_SERVER);
         sb.append(" as your CoMaS server\n\n");
      }

      return this.doLocalStart(sb);
   }

   private void doLocalClose() {
   }

   private void doRestrictedClose() {
   }

   private void doHybridClose() {
      this.resetDns();
   }

   public boolean isHybrid() {
      return this.mode.isHybrid();
   }

   public boolean isLocal() {
      return this.mode.isLocal();
   }

   public boolean isRestricted() {
      return this.mode.isRestricted();
   }

   public boolean isGlobal() {
      return this.mode.isGlobal();
   }

   public boolean isUnspecified() {
      return this.mode.isUnspecified();
   }

   public boolean isStopped() {
      return this.stopped.get();
   }

   public boolean isRunning() {
      return !this.stopped.get();
   }

   public void stop() {
      this.stopped.set(true);

      try {
         this.close();
         this.setMode(SessionConfigurationModeMonitor.Mode.global);
      } catch (IOException e) {
         Logger.log(Level.WARNING, "SessionConfigurationModeMonitor close: ", e);
      }

   }

   private void restoreProcesses() {
      HardwareAndSoftwareMonitor.OSProcessAction[] deniedProcesses = this.invigilator.getHardwareAndSoftwareMonitor().deniedProcesses();
      if (deniedProcesses != null) {
         for(HardwareAndSoftwareMonitor.OSProcessAction pa : deniedProcesses) {
            try {
               pa.restore();
            } catch (Exception e) {
               Logger.log(Level.WARNING, "SessionConfigurationModeMonitor process restore: ", e);
            }
         }
      }

      OSProcessAction.restoreAll();
   }

   public void close() throws IOException {
      try {
         TimerService.destroy(this.timer);
      } catch (Exception var2) {
      }

      this.restoreProcesses();
      if (this.isLocal()) {
         this.doLocalClose();
      } else if (this.isRestricted()) {
         this.doRestrictedClose();
      } else if (this.isHybrid()) {
         this.doHybridClose();
      }

      this.changeVirtualMachineNetworkInterfaces(true);
      this.networkIFManager.close();
   }

   public void stopDNS() {
      if (this.dns != null) {
         this.dns.stop();
         this.dns = null;
      }

   }

   public void addServletHandler() {
      SessionServlet ss = new SessionServlet(this);
      this.invigilator.getServletProcessor().addServlet(ss, "/report");
   }

   public boolean endOnMisconfiguration() {
      return Utils.getBooleanOrDefault(this.invigilator.getProperties(), "session.terminate_on_misconfiguration", true);
   }

   private void alert(String msg) {
      if (this.displayAlert) {
         if (this.alertIsActive.compareAndSet(false, true)) {
            WebAppDialogCoordinator.coordinate(ClientShared.MAX_TIME_TO_WAIT_TO_COORDINATE_UI);
            SwingUtilities.invokeLater(new Alert(msg));
         }

      }
   }

   public void onDNSLookup(InetAddress addr, Name name, Record[] records) {
      if (!this.invigilator.logArchiver.isStopped()) {
         if (records == null) {
            String msg = "UNRESOLVED: " + String.valueOf(name);
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, msg);
         } else {
            StringBuffer buff = new StringBuffer(name.toString());

            for(Record r : records) {
               buff.append("\n");
               buff.append(r.toString());
            }

            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, buff.toString());
         }

      }
   }

   public void onDNSError(Throwable e) {
      this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "DNS: " + String.valueOf(e));
   }

   public void onDNSMessage(Message message) {
      this.invigilator.logArchiver.put(Level.DIAGNOSTIC, message.toString());
   }

   public void onDNSLog(String log) {
   }

   private class Alert implements Runnable {
      private String msg;
      private JFrame frame;

      Alert(String msg) {
         this.msg = msg;
         this.frame = new JFrame();
         this.frame.setAlwaysOnTop(true);
      }

      public void run() {
         boolean sessionToEnd = SessionConfigurationModeMonitor.this.endOnMisconfiguration();
         String modifier;
         String title;
         int option;
         int type;
         if (sessionToEnd) {
            modifier = "\n\nYour session will now end";
            title = "CoMaS session will end";
            type = 0;
            option = 0;
         } else {
            modifier = "\n\nCoMaS may change configuration. Continue session?";
            title = "CoMaS configuration change required";
            option = 0;
            type = 2;
         }

         if (SessionConfigurationModeMonitor.this.number_of_alerts >= SessionConfigurationModeMonitor.MAXIMUM_ALERTS && !sessionToEnd) {
            SessionConfigurationModeMonitor.this.alertIsActive.set(false);
         } else {
            AtomicBoolean alertTimeout = new AtomicBoolean(false);
            if (SessionConfigurationModeMonitor.this.timeout > 0) {
               try {
                  SessionConfigurationModeMonitor.this.timer.schedule(SessionConfigurationModeMonitor.this.new AlertTimeoutTask(this.frame, alertTimeout), (long)SessionConfigurationModeMonitor.this.timeout);
               } catch (IllegalStateException var9) {
                  SessionConfigurationModeMonitor.this.timeout = 0;
               }
            }

            int res = JOptionPane.showConfirmDialog(this.frame, this.msg + modifier, title, option, type, IconLoader.getIcon(type));
            SessionConfigurationModeMonitor.this.alertIsActive.set(false);
            ++SessionConfigurationModeMonitor.this.number_of_alerts;
            if (res != 0 && !alertTimeout.get() || sessionToEnd) {
               Thread thread = new Thread(new Runnable() {
                  public void run() {
                     SessionConfigurationModeMonitor.this.invigilator.removeShutdownHook();

                     try {
                        SessionConfigurationModeMonitor.this.invigilator.endTheSession();
                     } finally {
                        SessionConfigurationModeMonitor.this.invigilator.exitAfterSessionEnd(-1);
                     }

                  }
               });
               thread.start();
            }
         }

      }
   }

   public static enum IPv4ConfigurationMode {
      manual,
      dhcp,
      unspecified;

      public static IPv4ConfigurationMode parse(String modeString) {
         if (modeString == null) {
            return unspecified;
         } else {
            modeString = modeString.trim();
            if (modeString.equalsIgnoreCase("static")) {
               return manual;
            } else {
               return modeString.equalsIgnoreCase("dhcp") ? dhcp : unspecified;
            }
         }
      }

      public boolean isDHCP() {
         return this == dhcp;
      }

      public boolean isStatic() {
         return this == manual;
      }

      public boolean isUnspecified() {
         return this == unspecified;
      }
   }

   public static enum Mode {
      local,
      restricted,
      hybrid,
      global,
      unspecified;

      public static Mode parse(String modeString) {
         if (modeString == null) {
            return unspecified;
         } else {
            modeString = modeString.trim();
            if (modeString.equalsIgnoreCase("local")) {
               return local;
            } else if (modeString.equalsIgnoreCase("restricted")) {
               return restricted;
            } else if (modeString.equalsIgnoreCase("hybrid")) {
               return hybrid;
            } else {
               return modeString.equalsIgnoreCase("global") ? global : unspecified;
            }
         }
      }

      public boolean isHybrid() {
         return this == hybrid;
      }

      public boolean isRestricted() {
         return this == restricted;
      }

      public boolean isLocal() {
         return this == local;
      }

      public boolean isGlobal() {
         return this == global;
      }

      public boolean isUnspecified() {
         return this == unspecified;
      }
   }

   public static class SessionServlet extends HttpServlet {
      private final SessionConfigurationModeMonitor snmm;

      public SessionServlet(SessionConfigurationModeMonitor snmm) {
         this.snmm = snmm;
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         this.snmm.invigilator.getServletProcessor().updateLastAccessTime();
         this.snmm.check();
         this.snmm.invigilator.setLastServlet("report");
         response.addHeader("Access-Control-Allow-Origin", "*");
         response.setContentType("text/html");
         PrintWriter pw = response.getWriter();
         pw.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\"><title>");
         pw.print(this.snmm.invigilator.getTitle());
         pw.print("CoMaS Report: ");
         pw.print(this.snmm.invigilator.getName());
         pw.print(" (");
         pw.print(this.snmm.invigilator.getID());
         pw.print(")</title>");
         pw.print(SystemWebResources.getStylesheet());
         pw.print(SystemWebResources.getIcon());
         pw.print("</head><body>");
         pw.print(this.snmm.invigilator.getServletProcessor().checkForServletCode());
         pw.print("<div style=\"margin:16px;font-family: Arial, Helvetica, sans-serif;\">");
         pw.print("<h1 id=\"reportTitle\">Report: ");
         pw.print(this.snmm.invigilator.getCourse());
         pw.print("/");
         pw.print(this.snmm.invigilator.getActivity());
         pw.print(" for ");
         pw.print(this.snmm.invigilator.getName());
         pw.print(" (");
         pw.print(this.snmm.invigilator.getID());
         pw.print(")");
         pw.print(" Mode: ");
         pw.print(this.snmm.mode);
         pw.print("</h1>");
         pw.print(this.snmm.invigilator.getServletProcessor().refreshForServlet());
         pw.print("<pre>");
         pw.println(this.snmm.report());
         pw.println("</pre>");
         pw.print(this.snmm.invigilator.getServletProcessor().footerForServlet(true, false, SystemWebResources.getHomeButton()));
         pw.print("</div>");
         pw.print("</body></html>");
         response.setStatus(200);
      }
   }

   public class SessionCheckTask extends TimerTask {
      private final SessionConfigurationModeMonitor snmm;
      private Runnable beforeCheck;
      private Runnable afterCheck;
      private boolean ignore;
      private final ReentrantLock lock;

      public SessionCheckTask(SessionConfigurationModeMonitor snmm) {
         this.snmm = snmm;
         this.lock = new ReentrantLock(true);
      }

      public void setRunBeforeCheck(Runnable check) {
         this.lock.lock();
         this.beforeCheck = check;
         this.lock.unlock();
      }

      public void setRunAfterCheck(Runnable check) {
         this.lock.lock();
         this.afterCheck = check;
         this.lock.unlock();
      }

      public void ignore() {
         this.lock.lock();
         this.ignore = true;
         this.lock.unlock();
      }

      public void reset() {
         this.lock.lock();
         this.beforeCheck = null;
         this.afterCheck = null;
         this.ignore = false;
         this.lock.unlock();
      }

      public void run() {
         this.lock.lock();

         try {
            if (!this.ignore) {
               if (SessionConfigurationModeMonitor.this.invigilator.isInEndingState()) {
                  return;
               }

               while(!SessionConfigurationModeMonitor.this.invigilator.isInitialized()) {
                  Sleeper.sleep(1000);
               }

               if (this.beforeCheck != null) {
                  this.beforeCheck.run();
               }

               this.snmm.check();
               if (this.afterCheck != null) {
                  this.afterCheck.run();
               }

               ++SessionConfigurationModeMonitor.this.numberOfChecksPerformed;
               this.oneTimeChecksToBePerformed();
               return;
            }

            this.ignore = false;
         } catch (Exception e) {
            SessionConfigurationModeMonitor.this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Session mode monitor could not run a command: " + String.valueOf(e), SessionConfigurationModeMonitor.this.invigilator.createProblemSetEvent("software_configuration"));
            return;
         } finally {
            this.lock.unlock();
         }

      }

      private void oneTimeChecksToBePerformed() {
         if (SessionConfigurationModeMonitor.this.numberOfChecksPerformed == 1) {
            SessionConfigurationModeMonitor.this.changeVirtualMachineNetworkInterfaces(false);
         }

      }
   }

   public class AlertTimeoutTask extends TimerTask {
      private final JFrame frame;
      private final AtomicBoolean timedOut;

      public AlertTimeoutTask(JFrame frame, AtomicBoolean timedOut) {
         this.frame = frame;
         this.timedOut = timedOut;
      }

      public void run() {
         this.timedOut.set(true);
         this.frame.dispose();
      }

      public boolean cancel() {
         boolean rtn = super.cancel();
         this.frame.dispose();
         return rtn;
      }
   }

   public class VirtualMachineCheckTimeoutTask extends TimerTask {
      public void run() {
         SessionConfigurationModeMonitor.this.changeVirtualMachineNetworkInterfaces(false);
      }
   }

   public class DNSDomainServerCheckTask extends TimerTask {
      private String DEFAULT_DNS_SERVER = "8.8.8.8";
      Lookup lookup;

      DNSDomainServerCheckTask() {
         if (SessionConfigurationModeMonitor.this.allowed_dns_domain_server != null && SessionConfigurationModeMonitor.this.dns == null) {
            try {
               Name name = Name.fromString(SessionConfigurationModeMonitor.this.allowed_dns_domain_server);
               this.lookup = new Lookup(name, 1);
            } catch (TextParseException var3) {
               this.lookup = null;
            }
         } else {
            this.lookup = null;
         }

      }

      public void run() {
         if (SessionConfigurationModeMonitor.this.mode.isHybrid() && SessionConfigurationModeMonitor.this.allowed_dns_domain_server != null && SessionConfigurationModeMonitor.this.allowed_dns_server != null && this.lookup != null) {
            try {
               if (SessionConfigurationModeMonitor.this.initialDnsServers != null && SessionConfigurationModeMonitor.this.initialDnsServers.length > 0) {
                  this.lookup.setResolver(new SimpleResolver(SessionConfigurationModeMonitor.this.initialDnsServers[0]));
               } else {
                  this.lookup.setResolver(new SimpleResolver(this.DEFAULT_DNS_SERVER));
               }
            } catch (Exception var9) {
               return;
            }

            InetAddress address = null;

            try {
               Record[] records = this.lookup.run();
               if (records != null) {
                  for(Record record : records) {
                     if (record.getType() == 1) {
                        ARecord arecord = (ARecord)record;
                        address = arecord.getAddress();
                     }
                  }
               }
            } catch (Exception var8) {
            }

            if (address != null) {
               String dns_ip_address = address.getHostAddress();
               if (SessionConfigurationModeMonitor.this.allowed_dns_server.equals(dns_ip_address)) {
                  return;
               }

               SessionConfigurationModeMonitor.this.allowed_dns_server = dns_ip_address;
               SessionConfigurationModeMonitor.this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Dynamic DNS server change: " + SessionConfigurationModeMonitor.this.allowed_dns_server);
            }

         }
      }
   }
}
