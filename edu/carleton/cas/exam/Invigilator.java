package edu.carleton.cas.exam;

import com.cogerent.utility.CircularBuffer;
import com.cogerent.utility.PropertyValue;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import edu.carleton.cas.background.AnnotatedObject;
import edu.carleton.cas.background.ApplicationLoginState;
import edu.carleton.cas.background.ArchiveSentinel;
import edu.carleton.cas.background.Authenticator;
import edu.carleton.cas.background.ConfigurationBridge;
import edu.carleton.cas.background.Harvester;
import edu.carleton.cas.background.KeepAliveSentinel;
import edu.carleton.cas.background.LogArchiver;
import edu.carleton.cas.background.LoggerModuleBridge;
import edu.carleton.cas.background.ScreenShotSentinel;
import edu.carleton.cas.background.SessionConfigurationModeMonitor;
import edu.carleton.cas.background.StateDistributor;
import edu.carleton.cas.background.UploadArchiver;
import edu.carleton.cas.background.timers.TimerService;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.dao.Session;
import edu.carleton.cas.dao.Student;
import edu.carleton.cas.dao.StudentSession;
import edu.carleton.cas.file.DirectoryUtils;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.file.Zip;
import edu.carleton.cas.jetty.embedded.ExamServlet;
import edu.carleton.cas.jetty.embedded.IDVerificationServlet;
import edu.carleton.cas.jetty.embedded.ProgressIndicator;
import edu.carleton.cas.jetty.embedded.ProgressServlet;
import edu.carleton.cas.jetty.embedded.QuitServlet;
import edu.carleton.cas.jetty.embedded.ServletProcessor;
import edu.carleton.cas.jetty.embedded.SessionEndTask;
import edu.carleton.cas.jetty.embedded.UploadCheckServlet;
import edu.carleton.cas.jetty.embedded.UploadServlet;
import edu.carleton.cas.jetty.embedded.WebcamServlet;
import edu.carleton.cas.jetty.embedded.processors.ExamQuestionProperties;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.modules.foundation.ModuleManager;
import edu.carleton.cas.reporting.ReportManager;
import edu.carleton.cas.resources.AbstractFileTask;
import edu.carleton.cas.resources.AbstractNetworkTask;
import edu.carleton.cas.resources.ApplicationConfigurationLauncher;
import edu.carleton.cas.resources.BrowserTabsFromServer;
import edu.carleton.cas.resources.FileExplorer;
import edu.carleton.cas.resources.FileSystemMonitor;
import edu.carleton.cas.resources.HardwareAndSoftwareMonitor;
import edu.carleton.cas.resources.Resource;
import edu.carleton.cas.resources.ResourceListener;
import edu.carleton.cas.resources.ResourceMonitor;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.security.Checksum;
import edu.carleton.cas.security.StreamCryptoUtils;
import edu.carleton.cas.ui.DisappearingAlert;
import edu.carleton.cas.ui.WebAlert;
import edu.carleton.cas.utility.ClientConfiguration;
import edu.carleton.cas.utility.ClientHelper;
import edu.carleton.cas.utility.ClipboardManager;
import edu.carleton.cas.utility.CopilotControl;
import edu.carleton.cas.utility.CountDownLatchNotifier;
import edu.carleton.cas.utility.DetectVM;
import edu.carleton.cas.utility.Displays;
import edu.carleton.cas.utility.HTMLFileViewGenerator;
import edu.carleton.cas.utility.IPAddressChecker;
import edu.carleton.cas.utility.MacAddress;
import edu.carleton.cas.utility.Named;
import edu.carleton.cas.utility.Observable;
import edu.carleton.cas.utility.Password;
import edu.carleton.cas.utility.PatternConstants;
import edu.carleton.cas.utility.ScreenShotPermissionTest;
import edu.carleton.cas.utility.Sleeper;
import edu.carleton.cas.utility.VMDetector;
import edu.carleton.cas.utility.WindowsAdmin;
import edu.carleton.cas.websocket.WebsocketClientEndpoint;
import edu.carleton.cas.websocket.WebsocketEndpointManager;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.Thread.State;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.text.DateFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Properties;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.ImageIO;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import org.xbill.DNS.TextParseException;
import oshi.hardware.NetworkIF;

public class Invigilator extends Observable implements ApplicationLoginState, ResourceListener, Thread.UncaughtExceptionHandler {
   private static final DateFormatSymbols dfs = new DateFormatSymbols();
   private static final SimpleDateFormat sdf_h_mm_ss_a_z;
   private static final FileFilter EXAM_FOLDER_FILTER;
   private String name;
   private String canonicalStudentName;
   private String id;
   private String email;
   private String passcode;
   private String course;
   private String activity;
   private StudentSession studentSession;
   private String title;
   private String lastServlet;
   private AtomicReference iState;
   private String state;
   private String alert;
   private boolean done;
   private Thread thread;
   private boolean isAllowedToUpload;
   private boolean isRestartedSession;
   private String sessionStartContext;
   public final UploadArchiver screenShotArchiver;
   public final LogArchiver logArchiver;
   public final LogArchiver highPriorityLogArchiver;
   public final UploadArchiver examArchiver;
   public final StateDistributor stateDistributor;
   private final ScreenShotSentinel screenShotSentinel;
   private final ArchiveSentinel archiveSentinel;
   private final KeepAliveSentinel sentinel;
   private ModuleManager moduleManager;
   private FileSystemMonitor fileSystemMonitor;
   private int failures;
   private long failureStartTime;
   private long actualStartTime;
   private long estimatedEndTime;
   private long restartedArchiveSessionTime;
   private ResourceMonitor fileResource;
   private ResourceMonitor networkResource;
   private WebsocketEndpointManager websocketEndpointManager;
   private ServletProcessor servletProcessor;
   private HardwareAndSoftwareMonitor hardwareAndSoftwareMonitor;
   private Properties properties;
   private ExamQuestionProperties examQuestionProperties;
   private ReportManager reportManager;
   private SessionConfigurationModeMonitor sessionConfigurationModeMonitor;
   private ClientConfiguration cc;
   private Thread shutdownHook;
   private CircularBuffer circularImageBuffer;
   private X509Certificate launcherCertificate;
   private X509Certificate clientCertificate;
   AtomicBoolean authenticationInProgress;
   private AtomicBoolean endedSession;
   private CountDownLatchNotifier latch;
   long actualEndTime;
   AtomicBoolean finalAuthentication;
   CountDownLatch finalAuthenticationLatch;
   AtomicBoolean finalizeShutdown;
   private String[] location;
   private boolean initHasRun;
   long authenticationStartProblemTime;
   long successfulAuthenticationTime;
   Throwable authenticationFailureCause;
   long wakeup;
   private boolean screenShotProblem;
   private BufferedImage qrCode;
   private BufferedImage annotationCode;
   private String qrLabel;
   private long lastSnapshotTime;
   private ReentrantLock screenShotLock;
   private boolean archiveProblem;
   private ReentrantLock archiveLock;
   private String reportedState;
   // $FF: synthetic field
   private static volatile int[] $SWITCH_TABLE$java$util$concurrent$TimeUnit;

   static {
      dfs.setAmPmStrings(new String[]{"AM", "PM"});
      sdf_h_mm_ss_a_z = new SimpleDateFormat("h:mm:ss a z", dfs);
      EXAM_FOLDER_FILTER = new FileFilter() {
         public boolean accept(File f) {
            return !f.isHidden() && f.canRead() && !f.getName().equals("activity.idx");
         }
      };
   }

   public Invigilator(ClientConfiguration cc) {
      this("", "", "default", "default", cc.getConfiguration());
      if (cc.getID() == null) {
         cc.setID("");
      }

      if (cc.getFirst() == null) {
         cc.setFirst("");
      }

      if (cc.getLast() == null) {
         cc.setLast("");
      }

      if (cc.getCourse() == null) {
         cc.setCourse("default");
      }

      if (cc.getActivity() == null) {
         cc.setActivity("default");
      }

      if (cc.getEmail() == null) {
         cc.setEmail("");
      }

      this.setID(cc.getID());
      this.setName(cc.getFirst(), cc.getLast());
      this.setEmail(cc.getEmail());
      this.setCourse(cc.getCourse());
      this.setActivity(cc.getActivity());
      this.studentSession = new StudentSession(new Session(cc), new Student(cc));
      this.cc = cc;
   }

   private Invigilator(String id, String name, String course, String activity, Properties properties) {
      this.failures = 0;
      this.failureStartTime = 0L;
      this.actualStartTime = 0L;
      this.estimatedEndTime = 0L;
      this.restartedArchiveSessionTime = 0L;
      this.authenticationInProgress = new AtomicBoolean(false);
      this.endedSession = new AtomicBoolean(false);
      this.latch = null;
      this.actualEndTime = 0L;
      this.finalAuthentication = new AtomicBoolean(false);
      this.finalAuthenticationLatch = new CountDownLatch(1);
      this.finalizeShutdown = new AtomicBoolean(false);
      this.initHasRun = false;
      this.authenticationStartProblemTime = 0L;
      this.successfulAuthenticationTime = 0L;
      this.authenticationFailureCause = null;
      this.wakeup = 9223372036854655807L;
      this.screenShotProblem = false;
      this.qrCode = null;
      this.annotationCode = null;
      this.qrLabel = null;
      this.lastSnapshotTime = 0L;
      this.screenShotLock = new ReentrantLock();
      this.archiveProblem = false;
      this.archiveLock = new ReentrantLock();
      this.title = "";
      this.lastServlet = ProgressServlet.getMapping();
      this.name = name;
      this.canonicalStudentName = Named.canonical(name);
      this.id = id;
      this.course = course;
      this.activity = activity;
      this.passcode = null;
      this.state = "Unknown";
      this.done = false;
      this.properties = properties;
      this.isAllowedToUpload = true;
      this.reportedState = "Logged out";
      this.sessionStartContext = "";
      this.iState = new AtomicReference(InvigilatorState.unknown);
      this.shutdownHook = null;
      this.sentinel = new KeepAliveSentinel(ClientShared.MSECS_BETWEEN_KEEP_ALIVE_CHECK, this);
      this.screenShotArchiver = new UploadArchiver(this, ClientShared.BASE_VIDEO, "video", ClientShared.PASSKEY_VIDEO, "Screen upload", (long)ClientShared.UPLOAD_THRESHOLD_IN_MSECS);
      this.screenShotSentinel = new ScreenShotSentinel(this, (long)ClientShared.UPLOAD_THRESHOLD_IN_MSECS);
      this.logArchiver = new LogArchiver(this, ClientShared.LOG_URL, "SEVERE", "Logging");
      this.highPriorityLogArchiver = new LogArchiver(this, ClientShared.LOG_URL, "SEVERE", "High Priority Logging");
      this.stateDistributor = new StateDistributor(this, ClientShared.BASE_LOGIN, "state", "State");
      this.examArchiver = new UploadArchiver(this, ClientShared.BASE_UPLOAD, "upload", ClientShared.PASSKEY_FILE_UPLOAD, "Archive upload", (long)ClientShared.ARCHIVE_UPLOAD_THRESHOLD_IN_MSECS);
      this.archiveSentinel = new ArchiveSentinel(this, (long)ClientShared.ARCHIVE_UPLOAD_THRESHOLD_IN_MSECS);
      this.sentinel.register(this.screenShotSentinel);
      this.sentinel.register(this.screenShotArchiver);
      this.sentinel.register(this.logArchiver);
      this.sentinel.register(this.highPriorityLogArchiver);
      this.sentinel.register(this.examArchiver);
      this.sentinel.register(this.archiveSentinel);
      this.sentinel.register(this.stateDistributor);
      this.screenShotArchiver.start();
      this.logArchiver.start();
      this.highPriorityLogArchiver.start();
      this.screenShotSentinel.start();
      this.examArchiver.start();
      this.archiveSentinel.start();
      this.stateDistributor.start();
      this.moduleManager = ModuleManager.create();
      this.websocketEndpointManager = new WebsocketEndpointManager(this);
      this.moduleManager.addSharedProperty("websocket", this.websocketEndpointManager);
      this.updateActualStartAndEstimatedEndTimes();
      this.sentinel.start();
      this.alert = "";
      this.servletProcessor = ServletProcessor.create(this);
      this.hardwareAndSoftwareMonitor = new HardwareAndSoftwareMonitor(this);
      this.hardwareAndSoftwareMonitor.getBrowserHistoryReader().setStart(this.actualStartTime);
      this.reportManager = new ReportManager(this);
      this.reportManager.start();
   }

   public ReportManager getReportManager() {
      return this.reportManager;
   }

   public ModuleManager getModuleManager() {
      return this.moduleManager;
   }

   public HardwareAndSoftwareMonitor getHardwareAndSoftwareMonitor() {
      return this.hardwareAndSoftwareMonitor;
   }

   public ServletProcessor getServletProcessor() {
      return this.servletProcessor;
   }

   public long getActualStartTime() {
      return this.actualStartTime;
   }

   public long getEstimatedEndTime() {
      return this.estimatedEndTime;
   }

   public long getRestartedArchiveSessionTime() {
      return this.restartedArchiveSessionTime;
   }

   public String getNameAndID() {
      return this.name + " (" + this.id + ")";
   }

   public String getSessionContext() {
      return this.course + "/" + this.activity;
   }

   public String getName() {
      return this.name;
   }

   public String getEmail() {
      return this.email;
   }

   public void setName(String first, String last) {
      if (first != null && last != null) {
         String var10001 = first.trim().toLowerCase();
         this.name = var10001 + " " + last.trim().toLowerCase();
         this.canonicalStudentName = Named.canonical(this.name);
      }

   }

   public void setFullName(String full) {
      if (full != null) {
         this.name = full.toLowerCase();
         this.canonicalStudentName = Named.canonical(this.name);
      }

   }

   public void setID(String id) {
      if (id != null) {
         this.id = id.trim();
      }

   }

   public void setEmail(String email) {
      if (email != null) {
         this.email = email;
      }

   }

   public void setCourse(String course) {
      if (course != null) {
         this.course = course.trim();
      }

   }

   public void setActivity(String activity) {
      if (activity != null) {
         this.activity = activity.trim();
      }

   }

   public X509Certificate getLauncherCertificate() {
      return this.launcherCertificate;
   }

   public void setLauncherCertificate(X509Certificate launcherCertificate) {
      this.launcherCertificate = launcherCertificate;
   }

   public X509Certificate getClientCertificate() {
      return this.clientCertificate;
   }

   public void setClientCertificate(X509Certificate clientCertificate) {
      this.clientCertificate = clientCertificate;
   }

   public void setProperty(String property, String value) {
      this.properties.setProperty(property, value);
   }

   public Properties getProperties() {
      return this.properties;
   }

   public void mergeProperties(Properties properties) {
      properties.forEach((k, v) -> this.properties.put(k, v));
   }

   public ClientConfiguration getClientConfiguration() {
      return this.cc;
   }

   public String getCourse() {
      return this.course;
   }

   public String getActivity() {
      return this.activity;
   }

   public String getID() {
      return this.id;
   }

   public String getCanonicalStudentName() {
      return this.canonicalStudentName;
   }

   public InvigilatorState getInvigilatorState() {
      return (InvigilatorState)this.iState.get();
   }

   public void setInvigilatorState(InvigilatorState _iState) {
      this.iState.set(_iState);
   }

   public ExamQuestionProperties getExamQuestionProperties() {
      return this.examQuestionProperties;
   }

   public void setExamQuestionProperties(ExamQuestionProperties examQuestionProperties) {
      this.examQuestionProperties = examQuestionProperties;
   }

   public String getLastServlet() {
      return this.lastServlet;
   }

   public void setLastServlet(String lastServlet) {
      if (lastServlet != null && !lastServlet.equals(QuitServlet.getMapping())) {
         this.lastServlet = lastServlet;
      }

   }

   public boolean isInInvigilatorState(InvigilatorState state) {
      return this.iState.get() == state;
   }

   public String getSessionStartContext() {
      return this.sessionStartContext;
   }

   public WebsocketEndpointManager getWebsocketEndpointManager() {
      return this.websocketEndpointManager;
   }

   public SessionConfigurationModeMonitor getSessionConfigurationModeMonitor() {
      return this.sessionConfigurationModeMonitor;
   }

   public String getAlert() {
      return this.alert;
   }

   public void resetAlert() {
      this.alert = null;
   }

   public boolean removeShutdownHook() {
      try {
         if (this.shutdownHook != null) {
            return Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
         }
      } catch (Exception var2) {
      }

      return false;
   }

   public String getStatisticsReport() {
      StringBuffer sb = new StringBuffer();
      sb.append("<table class=\"w3-table-all\">");
      sb.append("<thead>");
      sb.append("<th>Name</th>");
      sb.append("<th>Starts</th>");
      sb.append("<th>Failures</th>");
      sb.append("<th>Processed</th>");
      sb.append("<th>Rate (msecs/upload)</th>");
      sb.append("<th>Exceptions</th>");
      sb.append("<th>Total Time (secs)</th>");
      sb.append("<th>Last Processed</th>");
      sb.append("<th>Last Exception</th>");
      sb.append("</thead><tbody>");
      this.screenShotArchiver.getStatistics().reportAsTableRow(this.screenShotArchiver.getName(), sb, sdf_h_mm_ss_a_z);
      this.examArchiver.getStatistics().reportAsTableRow(this.examArchiver.getName(), sb, sdf_h_mm_ss_a_z);
      this.logArchiver.getStatistics().reportAsTableRow(this.logArchiver.getName(), sb, sdf_h_mm_ss_a_z);
      this.highPriorityLogArchiver.getStatistics().reportAsTableRow(this.highPriorityLogArchiver.getName(), sb, sdf_h_mm_ss_a_z);
      this.stateDistributor.getStatistics().reportAsTableRow(this.stateDistributor.getName(), sb, sdf_h_mm_ss_a_z);
      sb.append("</tbody></table>");
      String ipv4Address = this.hardwareAndSoftwareMonitor.getIPv4Address();
      boolean quoteAsRate = Utils.getBooleanOrDefault(this.getProperties(), "report.network.quote_rate", true);
      if (quoteAsRate) {
         sb.append("<p style=\"text-align:center\">Network I/O Rate (bytes/sec) ");
      } else {
         sb.append("<p style=\"text-align:center\">Network I/O (bytes) ");
      }

      sb.append(this.hardwareAndSoftwareMonitor.networkIO(ipv4Address, quoteAsRate));
      sb.append(" for ");
      sb.append(ipv4Address);
      sb.append("</p>");
      return sb.toString();
   }

   private void updateActualStartAndEstimatedEndTimes() {
      this.actualStartTime = System.currentTimeMillis();
      this.estimatedEndTime = this.actualStartTime + this.getExamDurationInMinutes() * 60L * 1000L;
      if (this.hardwareAndSoftwareMonitor != null) {
         this.hardwareAndSoftwareMonitor.getBrowserHistoryReader().setStart(this.actualStartTime);
      }

   }

   public long getExamDurationInMinutes() {
      int durationInMinutes = Utils.getIntegerOrDefaultInRange(this.properties, "student.directory.ALLOWED_DURATION", 0, 0, 1440);
      return durationInMinutes == 0 ? (long)Utils.getIntegerOrDefaultInRange(this.properties, "DURATION", 180, 0, 1440) : (long)durationInMinutes;
   }

   public boolean changeServer(String server, String reason) {
      if (server == null) {
         return false;
      } else {
         if (this.sessionConfigurationModeMonitor != null && this.sessionConfigurationModeMonitor.isHybrid()) {
            try {
               this.sessionConfigurationModeMonitor.DNScheck();
            } catch (UnknownHostException | TextParseException e) {
               this.sessionConfigurationModeMonitor.fallbackToAnotherMode("DNS server exception on server change. Exception was " + String.valueOf(e));
            }
         }

         String[] tokens = server.split(":");
         String hostToUse;
         String portToUse;
         if (tokens != null && tokens.length > 1) {
            portToUse = tokens[1].trim();
            hostToUse = tokens[0].trim();
         } else {
            portToUse = ClientShared.PROTOCOL.equals("https") ? "8443" : "8080";
            hostToUse = server.trim();
         }

         int portAsInt;
         try {
            portAsInt = Integer.parseInt(portToUse);
         } catch (NumberFormatException var13) {
            portAsInt = 8443;
         }

         if (hostToUse.equals(ClientShared.DIRECTORY_HOST)) {
            return false;
         } else if (!Utils.isReachable(hostToUse, portAsInt, ClientShared.CONNECTION_TIMEOUT_IN_MSECS)) {
            this.logArchiver.put(Level.DIAGNOSTIC, "Backup server is unreachable: " + hostToUse + ":" + portAsInt);
            return false;
         } else {
            if (!this.setStateAndAuthenticate("Logged out")) {
               String eMsg = String.format("Could not log out from %s. Change was %s", ClientShared.DIRECTORY_HOST, reason);
               this.logArchiver.put(Level.DIAGNOSTIC, eMsg);
            }

            String msg = String.format("CoMaS server now: %s was %s. Change was %s", server, ClientShared.DIRECTORY_HOST, reason);
            this.logArchiver.put(Level.LOGGED, msg);
            ClientShared.CONFIGS.setProperty(this.course + ".port", portToUse);
            ClientShared.CONFIGS.setProperty(this.course + ".hostname", hostToUse);
            ClientShared.setupURLs(this.course);
            this.setProperty("student.directory.host", ClientShared.WEBSOCKET_HOST);
            this.setProperty("student.directory.port", ClientShared.PORT);
            SystemWebResources.configure(this.properties);
            this.updateSharedModulePropertiesThatAreHostRelated();
            this.servletProcessor.configure();
            this.websocketEndpointManager.close();
            this.examArchiver.setTarget(ClientShared.BASE_UPLOAD);
            this.screenShotArchiver.setTarget(ClientShared.BASE_VIDEO);

            try {
               this.successfulAuthenticationTime = 0L;
               (new Thread(new Authenticator(this))).start();
            } finally {
               this.failures = 1;
               this.failureStartTime = System.currentTimeMillis();
            }

            return true;
         }
      }
   }

   public String toStudentDirectoryContext() {
      StringBuffer buff = new StringBuffer();
      buff.append("{ ");
      boolean first = true;

      for(String key : this.properties.stringPropertyNames()) {
         if (key.startsWith("student.directory.")) {
            if (first) {
               first = false;
            } else {
               buff.append(",");
            }

            buff.append("\"");
            buff.append(key.substring("student.directory.".length()).toLowerCase());
            buff.append("\":\"");
            buff.append(this.properties.get(key));
            buff.append("\"");
         }
      }

      buff.append(" }");
      return buff.toString();
   }

   public String toStudentDirectoryAsJavascript() {
      StringBuffer buff = new StringBuffer();
      buff.append("const env = { ");
      boolean first = true;

      for(String key : this.properties.stringPropertyNames()) {
         if (key.startsWith("student.directory.")) {
            String keyUsed = key.substring("student.directory.".length()).toLowerCase();
            if (PatternConstants.variablePattern.matcher(keyUsed).matches()) {
               if (first) {
                  first = false;
               } else {
                  buff.append(",");
               }

               buff.append(keyUsed);
               buff.append(":\"");
               buff.append(this.properties.get(key));
               buff.append("\"");
            }
         }
      }

      buff.append(" };\n");
      return buff.toString();
   }

   public String getProperty(String key) {
      return this.properties.getProperty(key);
   }

   public String getProperty(String key, String defaultValue) {
      return this.properties.getProperty(key, defaultValue);
   }

   public long getSessionEndTime() {
      String maxTime = this.properties.getProperty("session.maxTime");
      if (maxTime == null) {
         maxTime = this.properties.getProperty("session.max_time", "0");
      }

      long maxSessionTime = 0L;
      long maxTimeInSeconds = 0L;

      try {
         maxTimeInSeconds = (long)Integer.parseInt(maxTime.trim());
         maxSessionTime = maxTimeInSeconds * 1000L + System.currentTimeMillis();
      } catch (NumberFormatException var11) {
         maxSessionTime = 0L;
         maxTimeInSeconds = 0L;
      }

      long latestEndTime = 0L;
      String latestEndTimeInMsecs = this.properties.getProperty("END_MSECS", "0");

      try {
         latestEndTime = Long.parseLong(latestEndTimeInMsecs.trim());
         if (latestEndTime == Long.MAX_VALUE) {
            latestEndTime = 0L;
         }
      } catch (NumberFormatException var10) {
         latestEndTime = 0L;
      }

      return maxSessionTime > latestEndTime ? maxSessionTime : latestEndTime;
   }

   public long estimatedTimeToEnd(TimeUnit unit) {
      long estimate = this.estimatedEndTime - System.currentTimeMillis();
      if (estimate > 0L) {
         switch (unit) {
            case MILLISECONDS:
               return estimate;
            case SECONDS:
               return estimate / 1000L;
            case MINUTES:
               return estimate / 60000L;
            default:
               throw new IllegalArgumentException();
         }
      } else {
         return 0L;
      }
   }

   public long elapsedTimeInMillis() {
      return this.actualStartTime > 0L ? Math.max(System.currentTimeMillis() - this.actualStartTime, 0L) : 0L;
   }

   public String getToken() {
      return this.properties.getProperty("TOKEN");
   }

   public void setToken(String tokenValue) {
      if (tokenValue == null) {
         this.properties.remove("TOKEN");
      } else {
         this.properties.setProperty("TOKEN", tokenValue);
      }

      this.moduleManager.addSharedProperty("token", tokenValue);
      this.moduleManager.setToken(tokenValue);
   }

   public boolean setStateAndAuthenticateCommon(String state) {
      Response response = null;
      if (!this.authenticationInProgress.compareAndSet(false, true)) {
         return false;
      } else {
         try {
            this.state = state;
            response = this.authenticate();
            if (response != null) {
               String rtn = (String)response.readEntity(String.class);
               if (!rtn.equalsIgnoreCase("{\"STOPPED\"}") && !rtn.equalsIgnoreCase("{\"STOPPING\"}")) {
                  JsonReader reader = Json.createReader(new StringReader(rtn));
                  JsonObject meAsJson = reader.readObject();
                  JsonString tokenAsJsonString = meAsJson.getJsonString("TOKEN");
                  if (tokenAsJsonString != null) {
                     String tokenAsString = tokenAsJsonString.getString();
                     if (tokenAsString != null) {
                        this.setToken(tokenAsString);
                     }
                  }
               } else if (!this.isInEndingState()) {
                  DisappearingAlert da = new DisappearingAlert((long)ClientShared.DISAPPEARING_ALERT_TIMEOUT, 1, 2);
                  this.setInvigilatorState(InvigilatorState.ending);
                  Runnable action = new SessionEndTask(this, ProgressServlet.getSingleton(), "Session ended by administrator");
                  da.setRunOnCloseRegardless(action);
                  da.show("Session ended by administrator", "CoMaS Configuration Alert");
               }
            }
         } catch (Exception var11) {
         } finally {
            this.authenticationInProgress.set(false);
         }

         return response != null && response.getStatus() < 204;
      }
   }

   public boolean isLoggingIn() {
      return this.state.equals("Logging in");
   }

   public boolean authenticationInProgress() {
      return this.authenticationInProgress.get();
   }

   public void setStateWaitAuthenticationThenExit(java.util.logging.Level level, String msg, String state, int millis, int code) {
      if (msg != null) {
         this.logArchiver.put(level, msg);
      }

      this.setInvigilatorState(InvigilatorState.ending);
      this.updateProgressServlet(100, SystemWebResources.getLocalResource("endedLandingPage", "/ended"));
      Sleeper.sleep(millis);
      this.setStateAndAuthenticateCommon(state);
      System.exit(code);
   }

   public boolean setStateAndAuthenticate(String state) {
      boolean rtn = this.setStateAndAuthenticateCommon(state);
      this.logArchiver.put(Level.DIAGNOSTIC, state + " " + String.valueOf(new Date()));
      return rtn;
   }

   public boolean setStateAndAuthenticate(String state, Object[] args) {
      boolean rtn = this.setStateAndAuthenticateCommon(state);
      this.logArchiver.put(Level.DIAGNOSTIC, state + " " + String.valueOf(new Date()), args);
      return rtn;
   }

   public void exitAfterSessionEnd(int code) {
      int count = 1;

      try {
         while(this.latch != null && this.latch.getCount() > 0L) {
            ProgressServlet.getSingleton().setProgressMessage("Waiting (" + count + ") ...");
            ++count;
            this.latch.await(5000L, TimeUnit.MILLISECONDS);
         }
      } catch (InterruptedException var4) {
      }

      System.exit(code);
   }

   public boolean isOkToClose() {
      boolean ok = true;
      if (this.fileResource != null) {
         ok = this.fileResource.okToClose();
      }

      return ok;
   }

   public String[] checkProcessesAccessingFolderOfInterest() {
      return this.fileResource != null ? this.fileResource.checkProcessesAccessingFolderOfInterest() : new String[0];
   }

   public String[] processesAccessingFolderOfInterest() {
      return this.fileResource != null ? this.fileResource.processesAccessingFolderOfInterest() : new String[0];
   }

   public boolean isEndedSession() {
      return this.endedSession.get();
   }

   public void endTheSession() {
      if (this.endedSession.compareAndSet(false, true)) {
         if (this.initHasRun) {
            this.sessionConfigurationModeMonitor.finalizeProcesses();
            this.sessionConfigurationModeMonitor.checkAndLogWiFiChange();
            this.sessionConfigurationModeMonitor.finalCheck();
            if (ClientShared.isWindowsOS() && PropertyValue.getValue(this, "ai", "copilot", false)) {
               CopilotControl cc = new CopilotControl(this);
               cc.enable();
            }

            this.properties.remove("session.initialized");
            this.properties.setProperty("session.ended", "true");
         }

         this.actualEndTime = System.currentTimeMillis();
         this.latch = new CountDownLatchNotifier(this.getLatchSize(), ProgressServlet.getSingleton());
         this.setTitleStatus(0);
         this.updateProgressServlet(5, "Finishing up, please wait");
         Logger.log(java.util.logging.Level.FINE, "Ending session ", "");
         this.preShutdownAuthentication();
         if (this.initHasRun) {
            this.takeScreenShot();
         }

         if (this.initHasRun && ClientShared.AUTO_ARCHIVE) {
            this.updateProgressServlet(10, "Archiving");
            if (this.createAndUploadArchive(true)) {
               File af = this.archiveFile();
               if (af.exists()) {
                  String ct = sdf_h_mm_ss_a_z.format(new Date(af.lastModified()));
                  String key = this.properties.getProperty("session.key");
                  if (key != null) {
                     key = key.trim();
                     String prefix = Utils.getStringOrDefault(this.properties, "session.prefix", "CoMaS");
                     prefix = this.resolveFolderVariables(prefix);
                     String encryptedArchive;
                     if (Utils.getBooleanOrDefault(this.properties, "session.use_comas_folder_for_archive", true)) {
                        encryptedArchive = String.format("%s%s%s-%s", ClientShared.COMAS_DIRECTORY, File.separator, prefix, this.encryptedArchiveCore());
                     } else {
                        encryptedArchive = String.format("%s%s%s-%s", ClientShared.HOME, File.separator, prefix, this.encryptedArchiveCore());
                     }

                     try {
                        StreamCryptoUtils.streamEncrypt(key, af.getAbsolutePath(), encryptedArchive);
                        String checksum = Checksum.getSHA256Checksum(encryptedArchive);
                        String msg = String.format("Checksum for local encrypted archive is: %s\nFinal archive (%d bytes) created at %s", checksum, af.length(), ct);
                        this.logArchiver.put(Level.DIAGNOSTIC, msg);
                     } catch (Exception e) {
                        this.logArchiver.put(Level.DIAGNOSTIC, "Failed to create local encrypted archive: " + String.valueOf(e));
                     }
                  } else {
                     LogArchiver var10000 = this.logArchiver;
                     java.util.logging.Level var10001 = Level.LOGGED;
                     long var10002 = af.length();
                     var10000.put(var10001, "Final archive (" + var10002 + " bytes) created at " + ct);
                  }
               } else {
                  this.logArchiver.put(Level.LOGGED, "Final archive has not been created");
               }
            }

            this.updateProgressServlet(15, "Archive created");
         }

         if (this.initHasRun) {
            this.finalizeReport();
         }

         this.done = true;
         this.kickstartThread(this.thread);
         this.stop();
         this.updateProgressServlet(90, "Finishing up, please wait");
         Logger.log(java.util.logging.Level.FINE, "Ended session ", "");
         Date var13 = new Date(System.currentTimeMillis());
         Logger.output("Exam end " + String.valueOf(var13));
         if (this.initHasRun && this.actualStartTime > 0L) {
            NumberFormat nf = NumberFormat.getInstance();
            String var14 = nf.format((double)this.elapsedTimeInMillis() / (double)1000.0F);
            Logger.output("Session duration: " + var14 + " seconds");
         }
      }

      this.finalizeShutdown();
      this.finalAuthentication();
      if (this.sessionConfigurationModeMonitor != null) {
         if (this.sessionConfigurationModeMonitor.isRunning()) {
            this.sessionConfigurationModeMonitor.stop();
         }

         this.sessionConfigurationModeMonitor.stopDNS();
      }

      this.updateProgressServlet(100, SystemWebResources.getLocalResource("endLandingPage", "/end"));
   }

   private int getLatchSize() {
      return (this.examArchiver != null ? 1 : 0) + (this.screenShotArchiver != null ? 1 : 0) + (this.logArchiver != null ? 1 : 0) + (this.highPriorityLogArchiver != null ? 1 : 0) + (this.stateDistributor != null ? 1 : 0) + (this.fileSystemMonitor != null ? 1 : 0);
   }

   private void kickstartThread(Thread _thread) {
      if (_thread != null && _thread.getState() == State.TIMED_WAITING) {
         _thread.interrupt();
      }

   }

   private void preShutdownAuthentication() {
      int failureToAuthenticate = 0;

      while(!this.isConnected() && failureToAuthenticate < 10) {
         try {
            if (this.setStateAndAuthenticateCommon("Logging in")) {
               this.failures = 0;
            } else {
               ++failureToAuthenticate;
               Sleeper.sleep(1000);
            }
         } catch (Exception var3) {
            ++failureToAuthenticate;
            Sleeper.sleep(1000);
         }
      }

   }

   private void finalAuthentication() {
      if (this.finalAuthentication.compareAndSet(false, true)) {
         int maxFailures = 10;
         int failureToAuthenticate = 0;

         while(this.reportedState != null && failureToAuthenticate < maxFailures) {
            this.state = this.reportedState;

            try {
               if (this.setStateAndAuthenticateCommon(this.state)) {
                  this.reportedState = null;
               }
            } catch (Exception var9) {
            } finally {
               ++failureToAuthenticate;
               if (this.reportedState != null && failureToAuthenticate < maxFailures) {
                  Sleeper.sleep(1000);
               } else {
                  this.finalAuthenticationLatch.countDown();
               }

            }
         }
      }

      try {
         this.finalAuthenticationLatch.await();
      } catch (InterruptedException var8) {
      }

   }

   private void finalizeReport() {
      String endSessionLog = String.format("Session ended after %.03f seconds", (double)this.elapsedTimeInMillis() / (double)1000.0F);
      String screensDirString = ClientShared.getScreensDirectory(this.course, this.activity);
      File screensDir = new File(screensDirString);
      File[] screenShots = screensDir.listFiles();
      String numberOfScreenShots;
      String latestScreenShotTime;
      String screenShotBacklog;
      if (screenShots != null && screenShots.length != 0) {
         Arrays.sort(screenShots, (a, b) -> b.lastModified() > a.lastModified() ? 1 : -1);
         numberOfScreenShots = "screen:" + screenShots.length;
         latestScreenShotTime = "screenShotTime:" + screenShots[0].lastModified();
         int var10000 = this.screenShotArchiver != null ? this.screenShotArchiver.backlog() : 0;
         screenShotBacklog = "screenShotBacklog:" + var10000;
      } else {
         numberOfScreenShots = "";
         latestScreenShotTime = "";
         screenShotBacklog = "";
      }

      String archivesDirString = ClientShared.getArchivesDirectory(this.course, this.activity);
      File archivesDir = new File(archivesDirString);
      File[] archives = archivesDir.listFiles();
      String numberOfArchives;
      String latestArchiveTime;
      String archiveBacklog;
      if (archives != null && archives.length != 0) {
         Arrays.sort(archives, (a, b) -> b.lastModified() > a.lastModified() ? 1 : -1);
         numberOfArchives = "archive:" + archives.length;
         latestArchiveTime = "archiveTime:" + archives[0].lastModified();
         int var18 = this.examArchiver != null ? this.examArchiver.backlog() : 0;
         archiveBacklog = "archiveBacklog:" + var18;
      } else {
         numberOfArchives = "";
         latestArchiveTime = "";
         archiveBacklog = "";
      }

      long ect = this.expectedCompletionTime();
      String warningMessage = String.format("Long completion time expected: (%.03f seconds).", (double)ect / (double)1000.0F);
      if (ect > (long)ClientShared.MAX_MSECS_TO_WAIT_TO_END) {
         endSessionLog = String.format("%s.\n%s", endSessionLog, warningMessage);
      }

      this.logArchiver.put(Level.LOGGED, endSessionLog);
      if (ect > (long)ClientShared.MAX_MSECS_TO_WAIT_TO_END) {
         this.reportManager.annotateReport(warningMessage);
      }

      StringBuilder sb = new StringBuilder();
      this.logArchiver.logStatisticsIfRestarted(sb);
      this.highPriorityLogArchiver.logStatisticsIfRestarted(sb);
      this.screenShotArchiver.logStatisticsIfRestarted(sb);
      this.examArchiver.logStatisticsIfRestarted(sb);
      this.stateDistributor.logStatisticsIfRestarted(sb);
      if (sb.length() > 0) {
         this.reportManager.annotateReport(sb.toString());
      }

      this.reportManager.instantReport(new Object[]{"session", numberOfScreenShots, latestScreenShotTime, numberOfArchives, latestArchiveTime, screenShotBacklog, archiveBacklog, "stop"});
      this.updateProgressServlet(20, "Report generated");
   }

   private long expectedCompletionTime() {
      long expectedCompletionTime = Math.max((long)ClientShared.MAX_MSECS_TO_WAIT_TO_END, this.screenShotArchiver != null ? this.screenShotArchiver.expectedCompletionTime() : 0L);
      expectedCompletionTime = Math.max(expectedCompletionTime, this.examArchiver != null ? this.examArchiver.expectedCompletionTime() : 0L);
      expectedCompletionTime = Math.max(expectedCompletionTime, this.logArchiver != null ? this.logArchiver.expectedCompletionTime() : 0L);
      expectedCompletionTime = Math.max(expectedCompletionTime, this.highPriorityLogArchiver != null ? this.highPriorityLogArchiver.expectedCompletionTime() : 0L);
      return Math.min(expectedCompletionTime, (long)(5 * ClientShared.MAX_MSECS_TO_WAIT_TO_END));
   }

   private void finalizeShutdown() {
      if (this.finalizeShutdown.compareAndSet(false, true)) {
         try {
            if (this.latch != null) {
               long expectedCompletionTime = this.expectedCompletionTime();
               if (expectedCompletionTime > (long)ClientShared.MAX_MSECS_TO_WAIT_TO_END) {
                  float maxTime = (float)expectedCompletionTime;
                  long timeToWaitInMillis = 1000L;

                  while(expectedCompletionTime > 0L) {
                     try {
                        float progress = 5.0F + 5.0F * (maxTime - (float)expectedCompletionTime) / maxTime;
                        this.updateProgressServlet(Math.round(progress), "Expected completion time: " + expectedCompletionTime / 1000L + " seconds");
                        expectedCompletionTime -= 1000L;
                        this.latch.await(1000L, TimeUnit.MILLISECONDS);
                     } catch (InterruptedException var11) {
                     }
                  }
               } else {
                  this.latch.await(expectedCompletionTime, TimeUnit.MILLISECONDS);
               }
            }
         } catch (InterruptedException var12) {
         } finally {
            this.updateProgressServlet(95, "Cleaning up");
            this.manageClipboard();
            this.manageResourceFiles();
            this.manageExamFiles();
            this.manageScreenFiles();
            this.manageArchiveFiles();
            this.manageToolFiles();
            this.manageSessionFiles();
            this.manageCourseFiles();
            this.manageOSToolFiles();
            this.manageLogFiles();
            if (this.sessionConfigurationModeMonitor != null && this.sessionConfigurationModeMonitor.isRunning()) {
               this.sessionConfigurationModeMonitor.stop();
            }

            this.updateProgressServlet(100, SystemWebResources.getLocalResource("endLandingPage", "/end"));
            Sleeper.sleep(1000);
            this.servletProcessor.stop();

            while(!this.servletProcessor.isStopped()) {
               Sleeper.sleep(1000);
            }

            this.halt(ClientShared.THIRTY_SECONDS_IN_MSECS);
         }
      }

   }

   public void halt(final int msecs) {
      String os = ClientShared.getOSString();
      if (Utils.getBooleanOrDefault(this.properties, "session." + os + ".halt", false)) {
         Thread terminator = new Thread(new Runnable() {
            public void run() {
               Sleeper.sleep(msecs);
               Runtime.getRuntime().halt(0);
            }
         });
         terminator.setName("halt");
         terminator.setUncaughtExceptionHandler(this);
         terminator.start();
      }

   }

   private Thread addShutdownHook() {
      Logger.log(java.util.logging.Level.FINE, "Creating session end hook", "");
      Thread hook = new Thread() {
         public void run() {
            if (Utils.getBooleanOrDefault(Invigilator.this.properties, "session.shutdown_hook", true)) {
               Invigilator.this.endTheSession();
            }

         }
      };
      hook.setName("session end");
      hook.setUncaughtExceptionHandler(this);
      Runtime.getRuntime().addShutdownHook(hook);
      Logger.log(java.util.logging.Level.FINE, "", "Session hook established");
      return hook;
   }

   public void setEndOfSessionStateFlags() {
      this.done = true;
      this.isAllowedToUpload = false;
   }

   public boolean canRunAtThisIPAddress() {
      IPAddressChecker checker;
      try {
         checker = new IPAddressChecker(this.properties);
      } catch (UnknownHostException var3) {
         return Utils.getBooleanOrDefault(this.properties, IPAddressChecker.getDefault(), true);
      }

      return checker.deny() ? false : checker.allow();
   }

   public String getLocation(boolean countryNameWanted) {
      if (this.location == null) {
         try {
            String ipAddress = this.getProperty("LOCAL_ADDRESS");
            if (AbstractNetworkTask.isPrivateIPv4(ipAddress)) {
               this.location = new String[]{"private"};
               return this.location[0];
            }

            Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
            WebTarget webTarget = client.target(ClientShared.BASE_LOGIN).path("lookup");
            Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
            invocationBuilder.accept(new String[]{"application/json"});
            String token = this.getToken();
            if (token != null) {
               invocationBuilder.cookie("token", token);
            }

            Form form = new Form();
            form.param("name", ipAddress);
            form.param("country", "true");
            form.param("live", this.properties.getProperty("location.database.city.live", "true"));
            Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
            this.location = ((String)response.readEntity(String.class)).split(":");
         } catch (Exception var9) {
            this.location = null;
            return "unknown";
         }
      }

      if (this.location.length == 1) {
         return this.location[0];
      } else if (!countryNameWanted && this.location.length >= 3) {
         String var10 = this.location[0];
         return var10 + " " + this.location[2];
      } else {
         String var10000 = this.location[0];
         return var10000 + ", " + this.location[1];
      }
   }

   public boolean isDone() {
      return this.done;
   }

   public boolean isRestartedSession() {
      return this.isRestartedSession;
   }

   public boolean isAllowedToUpload() {
      return this.isAllowedToUpload;
   }

   private void init() {
      if (!this.initHasRun) {
         this.isAllowedToUpload = true;
         this.examArchiver.setTarget(ClientShared.BASE_UPLOAD);
         this.screenShotArchiver.setTarget(ClientShared.BASE_VIDEO);
         this.servletProcessor.configure();
         this.servletProcessor.setupExtensions();
         SystemWebResources.configure(this.properties);
         this.hardwareAndSoftwareMonitor.configure();
         this.hardwareAndSoftwareMonitor.saveInterfaceIOStatistics();
         this.updateProgressServlet(10, "Hardware configured");
         this.reportManager.configure();
         this.updateProgressServlet(15, "Reporting setup");
         ClientShared.LOG_DIR = ClientShared.HOME;

         try {
            if (ClientShared.isWriteableDirectory(ClientShared.LOG_DIR)) {
               ClientShared.LOG_DIR = ClientShared.HOME + File.separator;
            } else {
               String var10000 = ClientShared.getDesktopDirectory();
               ClientShared.LOG_DIR = var10000 + File.separator;
            }

            Properties var46 = this.properties;
            String var10002 = this.getSessionContext();
            var46.setProperty("logs.title", "CoMaS Logs: " + var10002 + " for " + this.getNameAndID());
            Logger.setup(Invigilator.class, "comas-system", ClientShared.LOG_DIR, ClientShared.LOGGING_LEVEL, this);
            java.util.logging.Level var47 = ClientShared.LOGGING_LEVEL;
            var10002 = this.getNameAndID();
            Logger.log(var47, "Logging started for ", var10002 + ". Using folder " + ClientShared.getBaseDirectory(this.course, this.activity));
         } catch (IOException e) {
            this.logArchiver.put(Level.DIAGNOSTIC, "Logging problem detected: " + String.valueOf(e));
         }

         boolean toolsLoaded = this.setupSessionModeMonitoringAndTestWhetherEndOnMisconfiguration();
         this.updateProgressServlet(20, "Monitoring configuration set");
         Logger.log(java.util.logging.Level.FINE, "", "Initializing");
         Logger.log(java.util.logging.Level.FINEST, "client.ini ", this.properties.toString());
         AbstractFileTask.configure(this.properties);
         WebsocketClientEndpoint.configure(this.properties);
         this.updateProgressServlet(30, "Tasks configured");
         Logger.log(java.util.logging.Level.FINE, "Making ", "CoMaS directories");
         boolean directoryOk = true;
         directoryOk = directoryOk && this.makeDirectory(ClientShared.getScreensDirectory(this.course, this.activity));
         directoryOk = directoryOk && this.makeDirectory(ClientShared.getLogsDirectory(this.course, this.activity));
         directoryOk = directoryOk && this.makeDirectory(ClientShared.getToolsDirectory(this.course, this.activity));
         directoryOk = directoryOk && this.makeDirectory(ClientShared.getResourcesDirectory(this.course, this.activity));
         if (ClientShared.AUTO_ARCHIVE) {
            directoryOk = directoryOk && this.makeDirectory(ClientShared.getArchivesDirectory(this.course, this.activity));
         }

         if (!directoryOk) {
            String var10001 = ClientShared.getActivityDirectory(this.course, this.activity);
            this.notifyObservers("Could not create " + var10001 + " subdirectory.\n" + this.resolveVariablesInMessage(ClientShared.SUPPORT_MESSAGE) + "\n\nYour session will now end");
            String var48 = this.getSessionContext();
            String logMsg = "Startup FAILED(directory creation) (version 0.8.75) for " + var48 + " using java " + ClientShared.getJavaVersion() + " on " + ClientShared.getOSDisplayString() + " " + this.hardwareAndSoftwareMonitor.getOS();
            this.setStateWaitAuthenticationThenExit(java.util.logging.Level.WARNING, logMsg, "Terminated", 5000, -2);
         }

         Logger.log(java.util.logging.Level.FINE, "Completed ", " directory creation");
         boolean logsToBeViewable = Utils.getBooleanOrDefault(this.properties, "logs.view", true);
         if (logsToBeViewable) {
            String var49 = "CoMaS Logs " + this.name + " (" + this.id + ")";
            String var55 = ClientShared.LOG_DIR + "comas-system-log.html";
            String var60 = ClientShared.getToolsDirectory(this.course, this.activity);
            HTMLFileViewGenerator.create(var49, var55, var60 + File.separator + "logs.html");
            java.util.logging.Level var50 = java.util.logging.Level.FINE;
            var60 = ClientShared.getToolsDirectory(this.course, this.activity);
            Logger.log(var50, "Created log viewer in ", var60 + File.separator + "logs.html");
         }

         this.updateProgressServlet(40, "Folders created");

         try {
            this.createExam();
         } catch (IOException e) {
            this.done = true;
            Logger.log(java.util.logging.Level.SEVERE, "Could not create " + ClientShared.getExamDirectory(this.course, this.activity) + ": ", e.getMessage());
            String var56 = ClientShared.getExamDirectory(this.course, this.activity);
            this.notifyObservers("Could not create " + var56 + ".\n" + this.resolveVariablesInMessage(ClientShared.SUPPORT_MESSAGE) + "\n\nYour session will now end");
            String var51 = this.getSessionContext();
            String logMsg = "Startup FAILED(exam creation) (version 0.8.75) for " + var51 + " using java " + ClientShared.getJavaVersion() + " on " + ClientShared.getOSDisplayString() + " " + this.hardwareAndSoftwareMonitor.getOS();
            this.setStateWaitAuthenticationThenExit(java.util.logging.Level.WARNING, logMsg, "Terminated", 5000, -3);
         }

         this.updateProgressServlet(50, "Exam setup");
         this.createResources();
         this.updateProgressServlet(55, "Exam and Resources setup");

         try {
            if (!toolsLoaded) {
               this.createOStools();
            }
         } catch (IOException e) {
            this.done = true;
            Logger.log(java.util.logging.Level.SEVERE, "Could not create OS tools: ", e.getMessage());
            String var57 = e.getMessage();
            this.notifyObservers("Could not create OS tools: " + var57 + ".\n" + this.resolveVariablesInMessage(ClientShared.SUPPORT_MESSAGE) + "\n\nYour session will now end");
            String var52 = this.getSessionContext();
            String logMsg = "Startup FAILED(OS tools creation) (version 0.8.75) for " + var52 + " using java " + ClientShared.getJavaVersion() + " on " + ClientShared.getOSDisplayString() + " " + this.hardwareAndSoftwareMonitor.getOS();
            this.setStateWaitAuthenticationThenExit(java.util.logging.Level.WARNING, logMsg, "Terminated", 5000, -4);
         }

         this.updateProgressServlet(60, "Exam, Resources and Tools setup");
         this.createStudentNotes();
         this.shutdownHook = this.addShutdownHook();
         this.setupDirectoryWatching();
         this.updateProgressServlet(70, "Folder monitoring setup");
         if (ClientShared.BLUETOOTH_MONITORING) {
            Logger.log(java.util.logging.Level.CONFIG, "Bluetooth monitoring is enabled", "");
         }

         this.fileResource = new ResourceMonitor("file.txt", "file", ClientShared.getActivityDirectory(this.course, this.activity), this.properties);
         this.fileResource.addListener(this);
         this.fileResource.open();
         this.networkResource = new ResourceMonitor("network.txt", "network", ClientShared.getActivityDirectory(this.course, this.activity), this.properties);
         this.networkResource.addListener(this);
         this.networkResource.open();
         AbstractNetworkTask ant = (AbstractNetworkTask)this.networkResource.getTask();
         ant.addHostName(this.hardwareAndSoftwareMonitor.getNetworkParams().getHostName());
         this.updateProgressServlet(75, "Network monitoring setup");

         try {
            this.moduleManager.setToken(this.getToken());
            this.moduleManager.addSharedProperty("course", this.course);
            this.moduleManager.addSharedProperty("activity", this.activity);
            this.moduleManager.addSharedProperty("name", this.name);
            this.moduleManager.addSharedProperty("canonicalStudentName", this.canonicalStudentName);
            this.moduleManager.addSharedProperty("desktop", ClientShared.COMAS_DIRECTORY);
            this.moduleManager.addSharedProperty("id", this.id);
            this.moduleManager.addSharedProperty("directory", ClientShared.getBaseDirectory(this.course, this.activity));
            this.moduleManager.addSharedProperty("protocol", ClientShared.PROTOCOL);
            this.moduleManager.addSharedProperty("token", this.getToken());
            this.moduleManager.addSharedProperty("logger", new LoggerModuleBridge(this.logArchiver));
            this.moduleManager.addSharedProperty("high priority logger", new LoggerModuleBridge(this.highPriorityLogArchiver));
            this.moduleManager.addSharedProperty("shutdown", this.shutdownHook);
            this.moduleManager.addSharedProperty("state", this);
            this.moduleManager.addSharedProperty("version", "0.8.75");
            this.moduleManager.addSharedProperty("report.format.debug", this.properties.getProperty("logs.format.debug", "false"));
            this.moduleManager.addSharedProperty("configuration", new ConfigurationBridge(this.properties));
            long agreedToMonitor = this.getClientConfiguration().getAgreedToMonitor();
            if (agreedToMonitor != 0L) {
               this.moduleManager.addSharedProperty("session.agreedToMonitor", new Date(agreedToMonitor));
            }

            this.updateSharedModulePropertiesThatAreHostRelated();
            this.moduleManager.configure(this.properties);
         } catch (Exception e1) {
            this.logArchiver.put(java.util.logging.Level.WARNING, "Module configuration exception " + String.valueOf(e1));
         }

         if (ClientShared.USE_SCREEN_SHOTS) {
            Logger.log(java.util.logging.Level.CONFIG, "Screen shots are enabled", "");
         }

         if (ClientShared.USE_WEB_CAM || ClientShared.USE_WEB_CAM_ON_SCREEN_SHOT) {
            Logger.log(java.util.logging.Level.CONFIG, "Web cam is enabled", "");
         }

         boolean blocked = PropertyValue.getValue(this, "session", "clipboard.block", false);

         try {
            String contents = ClipboardManager.getContents();
            if (!this.sessionConfigurationModeMonitor.isDNSInitializationRequired() || !this.sessionConfigurationModeMonitor.isUsingTerminal()) {
               ClipboardManager.setContents("Emptied by CoMaS");
            }

            String isBlocked = "";
            if (blocked) {
               long blockFrequency = PropertyValue.getValue(this, "session", "clipboard.block.frequency", ClipboardManager.frequencyInMsecs);
               String blockMessage = PropertyValue.getValue(this, "session", "clipboard.block.message", ClipboardManager.message);
               if (blockFrequency > 0L) {
                  ClipboardManager.setBlockFrequency(blockFrequency);
                  ClipboardManager.setBlockMessage(blockMessage);
                  ClipboardManager.block(blockFrequency);
                  isBlocked = "[blocked]";
               }
            }

            this.logArchiver.put(java.util.logging.Level.INFO, String.format("CLIPBOARD%s: %s", isBlocked, contents));
         } catch (Exception var24) {
         }

         this.updateProgressServlet(90, "Clipboard emptied" + (blocked ? " and blocked" : ""));
         if (ClientShared.USE_SCREEN_SHOTS) {
            int sizeOfImageBuffer = PropertyValue.getValue(this, "session", "image_buffer_size", 60);
            if (sizeOfImageBuffer != 0) {
               if (sizeOfImageBuffer < 30 || sizeOfImageBuffer > 300) {
                  sizeOfImageBuffer = 60;
               }

               this.circularImageBuffer = new CircularBuffer(sizeOfImageBuffer);
               TimerTask imageTask = new TimerTask() {
                  public void run() {
                     Invigilator.this.screenShotLock.lock();

                     try {
                        BufferedImage[] screenFullImage = Displays.getImages();
                        ArrayList<BufferedImage> screenAsArrayList = new ArrayList(screenFullImage.length);

                        for(BufferedImage image : screenFullImage) {
                           screenAsArrayList.add(image);
                        }

                        Invigilator.this.circularImageBuffer.add(screenAsArrayList);
                     } catch (Exception var10) {
                     } finally {
                        Invigilator.this.screenShotLock.unlock();
                     }

                  }
               };
               int samplingRate = PropertyValue.getValue(this, "session", "image_sample_interval", 1000);
               if (samplingRate < 100 || samplingRate > 2000) {
                  samplingRate = 1000;
               }

               TimerService.getInstance().scheduleAtFixedRate(imageTask, (long)samplingRate, (long)samplingRate);
            }
         }

         try {
            Logger.setup(Invigilator.class, "comas-system", ClientShared.getActivityDirectory(this.course, this.activity), ClientShared.LOGGING_LEVEL, this);
         } catch (Exception var19) {
            this.logArchiver.put(java.util.logging.Level.WARNING, "Could not create log file in " + ClientShared.getActivityDirectory(this.course, this.activity));
         }

         String microarchitecture = this.hardwareAndSoftwareMonitor.getMicroarchitecture();
         if (microarchitecture.length() > 0 && !microarchitecture.equals("unknown")) {
            microarchitecture = " (" + microarchitecture + ")";
         } else {
            microarchitecture = "";
         }

         LogArchiver var53 = this.logArchiver;
         java.util.logging.Level var58 = java.util.logging.Level.INFO;
         String var62 = this.getSessionContext();
         var53.put(var58, "Startup successful (version 0.8.75) for " + var62 + " using java version " + ClientShared.getJavaVersion() + " on " + ClientShared.getOSDisplayString() + " " + this.hardwareAndSoftwareMonitor.getOS() + microarchitecture + (this.launcherCertificate != null ? "\nLauncher: " + String.valueOf(this.launcherCertificate.getSubjectX500Principal()) : "") + (this.clientCertificate != null ? "\nClient: " + String.valueOf(this.clientCertificate.getSubjectX500Principal()) : ""), new Object[]{"session", "start"});
         this.setTitleStatus(1);
         this.performStartupConfigurationChecksAndReport();
         this.updateActualStartAndEstimatedEndTimes();
         String screenBounds = Displays.logScreenBounds();
         String graphicsCards = this.hardwareAndSoftwareMonitor.graphicsCards();
         String displays = this.hardwareAndSoftwareMonitor.displays();
         this.logArchiver.put(Level.DIAGNOSTIC, screenBounds + "\n" + graphicsCards + "\n" + displays);
         VMDetector.runAllVMChecks(this);
         this.sessionConfigurationModeMonitor.processDNSServerConfiguration();
         this.updateProgressServlet(95, "Network configuration established");
         Date var54 = new Date(this.actualStartTime);
         Logger.output("Exam start " + var54.toString());
         if (this.isBeingHarvested()) {
            ClientShared.DIR = this.cc.getFolder();
            this.isAllowedToUpload = true;
            ClientShared.USE_SCREEN_SHOTS = false;
            ClientShared.AUTO_ARCHIVE = false;
            CountDownLatch cdl = new CountDownLatch(2);
            String screensDirString = ClientShared.getScreensDirectory(this.course, this.activity);

            int startImageIndex;
            try {
               startImageIndex = Integer.parseInt(this.properties.getProperty("harvest." + this.id + ".screens.index", "1").trim());
               if (startImageIndex < 1) {
                  startImageIndex = 1;
               }
            } catch (NumberFormatException var18) {
               startImageIndex = 1;
            }

            Thread harvester = new Harvester(startImageIndex, screensDirString, this, this.screenShotArchiver, "screen shots", "jpg", cdl);
            harvester.start();
            String archivesDirString = ClientShared.getArchivesDirectory(this.course, this.activity);

            int startArchiveIndex;
            try {
               startArchiveIndex = Integer.parseInt(this.properties.getProperty("harvest." + this.id + ".archives.index", "1").trim());
               if (startArchiveIndex < 1) {
                  startArchiveIndex = 1;
               }
            } catch (NumberFormatException var17) {
               startArchiveIndex = 1;
            }

            harvester = new Harvester(startArchiveIndex, archivesDirString, this, this.examArchiver, "archives", ".zip", cdl);
            harvester.start();
            this.updateProgressServlet(50, "Harvesting session content, please wait");
         } else {
            this.cc.setFolder(ClientShared.DIR);
            this.cc.save();
            this.downloadTools();
            this.sessionConfigurationModeMonitor.addServletHandler();
            this.sessionConfigurationModeMonitor.logReportContent();
            this.reportManager.addServletHandler();
            this.websocketEndpointManager.addServletHandler();
            this.websocketEndpointManager.configure();
            if (ClientShared.AUTO_ARCHIVE) {
               ExamServlet es = new ExamServlet(this);
               es.addServletHandler();
               UploadServlet us = new UploadServlet(this);
               us.addServletHandler();
               UploadCheckServlet ucs = new UploadCheckServlet(this);
               ucs.addServletHandler();
            }

            String end_message = this.resolveVariablesInMessage(ClientShared.END_MESSAGE);
            QuitServlet.getSingleton().setMessage(end_message);
            this.sentinel.setupEndOfSessionEventPowerCheckAndAutomatedReporting();
            this.archiveSentinel.init();
            this.screenShotSentinel.init();
            this.initHasRun = true;
            this.properties.setProperty("session.initialized", "true");
            this.updateProgressServlet(100, SystemWebResources.getLocalResource("motdLandingPage", "/motd"));
         }

      }
   }

   private void launchFileExplorer(String folder) {
      File f = new File(folder);
      if (f.exists() && f.isDirectory()) {
         (new FileExplorer(f, this.logArchiver, true)).start();
      }

   }

   public void initializeDeployedWebPagesAndApplications() {
      if (Utils.getBooleanOrDefault(this.properties, "file_explorer.on_start", true)) {
         this.launchFileExplorer(ClientShared.getExamDirectory(this.course, this.activity));
      }

      if (this.sessionConfigurationModeMonitor != null) {
         this.sessionConfigurationModeMonitor.initializeProcesses();
      }

      BrowserTabsFromServer bt1 = new BrowserTabsFromServer(this);
      bt1.start();
      ApplicationConfigurationLauncher at = new ApplicationConfigurationLauncher(this);
      at.start();
   }

   public void readyForService() {
      this.servletProcessor.readyForService();
   }

   public boolean isInEndingState() {
      InvigilatorState _iState = (InvigilatorState)this.iState.get();
      return InvigilatorState.isEnding(_iState) || InvigilatorState.isEnded(_iState);
   }

   private void updateSharedModulePropertiesThatAreHostRelated() {
      this.moduleManager.addSharedProperty("port", ClientShared.PORT);
      this.moduleManager.addSharedProperty("host", ClientShared.CMS_HOST);
      this.moduleManager.addSharedProperty(SystemWebResources.getVariable("stylesheet"), SystemWebResources.getStylesheet());
   }

   public boolean isInitialized() {
      return this.initHasRun;
   }

   private boolean setupSessionModeMonitoringAndTestWhetherEndOnMisconfiguration() {
      this.sessionConfigurationModeMonitor = new SessionConfigurationModeMonitor(this);
      if (this.sessionConfigurationModeMonitor.endOnMisconfiguration()) {
         boolean rtn;
         try {
            this.createOStools();
            rtn = true;
         } catch (IOException var3) {
            rtn = false;
         }

         this.sessionConfigurationModeMonitor.getPasswordDialog().setWait(false);
         this.sessionConfigurationModeMonitor.check();
         this.sessionConfigurationModeMonitor.getPasswordDialog().setWait(true);
         return rtn;
      } else {
         return false;
      }
   }

   private void performStartupConfigurationChecksAndReport() {
      boolean terminateOnOneCondition = false;
      StringBuffer sb = new StringBuffer();
      ScreenShotPermissionTest sst = new ScreenShotPermissionTest(new File(ClientShared.getDesktopDirectory(), Password.getPassCode()), Utils.getIntegerOrDefaultInRange(this.properties, "session.screen_test_wait", 1, 1, 10) * 1000, Utils.getIntegerOrDefaultInRange(this.properties, "session.screen_offset", 50, 0, 100));
      sst.run();
      if (ClientShared.USE_SCREEN_SHOTS && !sst.hasPermission()) {
         boolean terminateOnPermission = PropertyValue.getValue(this, "session", "terminate_on_screen_recording_permission", true);
         if (terminateOnPermission) {
            terminateOnOneCondition = true;
         }

         sb.append("You do not appear to have screen recording permissions");
         sb.append("\nACTION: You must allow screen recording. Please go to");
         sb.append("\n\"System Preferences>Privacy & Security>Screen and System Audio Recording\"");
         sb.append("\nand enable screen and audio recording for CoMaS");
         this.logArchiver.put(Level.NOTED, "You do not have screen recording permissions", this.createProblemSetEvent("screen_recording_permissions"));
      }

      if (this.hardwareAndSoftwareMonitor.isBelowMemoryThreshold()) {
         String memoryMsg = String.format("Memory is below recommended limit of %dGB\n", this.hardwareAndSoftwareMonitor.getMemoryThresholdInGB());
         sb.append(memoryMsg);
      }

      NetworkIF vpn = this.sessionConfigurationModeMonitor.networkInterfaceRunningVPN();
      if (vpn != null) {
         sb.append("\nYou appear to be running a VPN on ");
         sb.append(this.hardwareAndSoftwareMonitor.getIPAddressesString(vpn.getIPv4addr()));
         sb.append("\nACTION: You should disconnect the VPN before proceeding\n");
         HardwareAndSoftwareMonitor var10002 = this.hardwareAndSoftwareMonitor;
         this.logArchiver.put(Level.NOTED, "VPN running on " + var10002.getIPAddressesString(vpn.getIPv4addr()), this.createProblemSetEvent("vpn_detected"));
         if (this.sessionConfigurationModeMonitor.terminateIfVPNRunning()) {
            terminateOnOneCondition = true;
         }
      }

      int excessMonitors = this.sessionConfigurationModeMonitor.excessMonitors();
      if (excessMonitors > 0) {
         sb.append("\nYou have ");
         sb.append(excessMonitors);
         if (excessMonitors > 1) {
            sb.append(" unapproved monitors connected.");
            sb.append("\nACTION: You should disconnect the additional monitors before proceeding\n");
         } else {
            sb.append(" unapproved monitor connected.");
            sb.append("\nACTION: You should disconnect the additional monitor before proceeding\n");
         }

         this.logArchiver.put(Level.NOTED, "You have " + excessMonitors + " unapproved monitor(s) connected", this.createProblemSetEvent("excessive_monitors"));
         if (this.sessionConfigurationModeMonitor.terminateIfExcessiveMonitors()) {
            terminateOnOneCondition = true;
         }
      }

      if (!this.sessionConfigurationModeMonitor.displayCheck(sb) && this.sessionConfigurationModeMonitor.terminateIfVirtualDisplays()) {
         terminateOnOneCondition = true;
      }

      String[] media = this.hardwareAndSoftwareMonitor.hasRemoveableMedia();
      if (media != null && media.length > 0) {
         String mediaString = String.join("\n", media);
         sb.append("\nOne or more removable disk(s) have been detected:\n");
         sb.append(mediaString);
         sb.append("\nACTION: You should disconnect the removable disk(s) before proceeding\n");
         this.logArchiver.put(Level.NOTED, "One or more removable disk(s) have been detected:\n" + mediaString, this.createProblemSetEvent("removeable_disk"));
         if (this.sessionConfigurationModeMonitor.terminateIfRemoveableVolumes()) {
            terminateOnOneCondition = true;
         }
      }

      String vmID = DetectVM.identifyVM(false);
      if (!vmID.isEmpty()) {
         if (!VMDetector.isRunnableInVM(this)) {
            terminateOnOneCondition = true;
         }

         sb.append("\nYou appear to be running in a virtual machine (VM): ");
         sb.append(vmID);
         sb.append("\nACTION: Restart CoMaS outside of the VM\n");
         this.logArchiver.put(Level.NOTED, "VM running: " + vmID, this.createProblemSetEvent("vm_running"));
      }

      if (ClientShared.isWindowsOS() && PropertyValue.getValue(this, "ai", "copilot", false)) {
         CopilotControl cc = new CopilotControl(this);
         cc.disable();
      }

      if (this.sessionConfigurationModeMonitor.isAdministratorPrivilegeRequired() && !WindowsAdmin.isAdmin()) {
         sb.append("\nYou have insufficient privilege to run this session.");
         sb.append("\nRight click on the CoMaS icon and select \"Run as administrator\" from menu.");
         sb.append("\nACTION: Restart CoMaS using \"Run as administator\"\n");
         this.logArchiver.put(Level.NOTED, "Insufficient privilege, administrator mode required", this.createProblemSetEvent("administrator_mode"));
         if (this.sessionConfigurationModeMonitor.terminateIfNoAuthentication()) {
            terminateOnOneCondition = true;
         }
      }

      if (sb.length() > 0) {
         int type = terminateOnOneCondition ? 0 : 2;
         DisappearingAlert da = new DisappearingAlert((long)ClientShared.DISAPPEARING_ALERT_TIMEOUT, 1, type);
         if (terminateOnOneCondition) {
            sb.append("\n\nYour session will now end");
            this.setInvigilatorState(InvigilatorState.ending);
            Runnable action = new SessionEndTask(this, (ProgressIndicator)null);
            da.setRunOnCloseRegardless(action);
         }

         this.sessionConfigurationModeMonitor.setReport(sb);
         da.show(sb.toString(), "CoMaS Configuration Alert");
      }

      this.sessionConfigurationModeMonitor.schedule();
   }

   public String getPropertyWithVariables(String property, String defaultValue) {
      return this.resolveVariablesInMessage(this.getProperty(property, defaultValue));
   }

   public String resolveVariablesInMessage(String msgWithVariables) {
      if (msgWithVariables != null && msgWithVariables.length() != 0) {
         String msg = msgWithVariables.replace("${NAME}", this.name);

         try {
            msg = msg.replace("\\n", "\n");
            msg = msg.replace("\\t", "\t");
         } catch (Exception var8) {
         }

         msg = msg.replace("${FOLDER}", ClientShared.DIR);
         msg = msg.replace("${ID}", this.id);
         msg = msg.replace("${COURSE}", this.course);
         msg = msg.replace("${ACTIVITY}", this.activity);
         msg = msg.replace("${TIME}", (new Date()).toString());
         msg = msg.replace("${NETWORK}", this.sessionConfigurationModeMonitor != null ? this.sessionConfigurationModeMonitor.report() : "");
         msg = msg.replace("${MODE}", this.sessionConfigurationModeMonitor != null ? this.sessionConfigurationModeMonitor.getMode().toString() : "");
         msg = msg.replace("${SESSION}", this.sessionStartContext != null ? this.sessionStartContext : "");
         String wanAddress = this.getProperty("LOCAL_ADDRESS");
         msg = msg.replace("${LOCATION}", wanAddress != null ? this.getLocation(true) : "?");
         msg = msg.replace("${WAN_ADDRESS}", wanAddress != null ? wanAddress : "?");
         msg = msg.replace("${IP_ADDRESS}", this.hardwareAndSoftwareMonitor.getIPv4Address());
         msg = msg.replace("${VERSION}", "0.8.75");
         msg = msg.replace("${WEB_SERVER}", this.servletProcessor.getService());
         String userAgent = this.properties.getProperty("session.userAgent");
         msg = msg.replace("${BROWSER}", userAgent != null ? userAgent : "");
         if (msg.contains("${LAST.")) {
            msg = msg.replace("${LAST.COURSE}", this.studentSession.session.courseActivity != null && this.studentSession.session.courseActivity.course != null ? this.studentSession.session.courseActivity.course : "?");
            msg = msg.replace("${LAST.ACTIVITY}", this.studentSession.session.courseActivity != null && this.studentSession.session.courseActivity.activity != null ? this.studentSession.session.courseActivity.activity : "?");
            msg = msg.replace("${LAST.IP_ADDRESS}", this.studentSession.session.ipAddress != null ? this.studentSession.session.ipAddress : "?");
            msg = msg.replace("${LAST.START}", this.studentSession.session.start > 0L ? (new Date(this.studentSession.session.start)).toString() : "?");
            msg = msg.replace("${LAST.FIRST_NAME}", this.studentSession.student.first != null ? this.studentSession.student.first : "?");
            msg = msg.replace("${LAST.LAST_NAME}", this.studentSession.student.last != null ? this.studentSession.student.last : "?");
            msg = msg.replace("${LAST.ID}", this.studentSession.student.id != null ? this.studentSession.student.id : "?");
         }

         msg = msg.replace("${HARDWARE}", this.hardwareAndSoftwareMonitor.reportAsString(this));
         String safe_msg = msg;
         if (Utils.getBooleanOrDefault(this.properties, "session.resolve_environment_variables", false)) {
            try {
               msg = this.resolveSystemEnvironmentVariables(safe_msg);
               msg = this.resolveSystemPropertyVariables(msg);
            } catch (Exception var7) {
               msg = msg;
            }
         }

         return msg;
      } else {
         return "";
      }
   }

   private String resolveSystemEnvironmentVariables(String msgWithVariables) {
      ArrayList<String> keys = new ArrayList();
      System.getenv().forEach((k, v) -> {
         String key = "${" + k + "}";
         if (msgWithVariables.contains(key)) {
            keys.add(k);
         }

      });
      String msg = msgWithVariables;

      for(String key : keys) {
         msg = msg.replace("${" + key + "}", System.getenv(key));
      }

      return msg;
   }

   private String resolveSystemPropertyVariables(String msgWithVariables) {
      ArrayList<String> keys = new ArrayList();
      System.getProperties().stringPropertyNames().forEach((k) -> {
         String key = "${" + k + "}";
         if (msgWithVariables.contains(key)) {
            keys.add(k);
         }

      });
      String msg = msgWithVariables;

      for(String key : keys) {
         msg = msg.replace("${" + key + "}", System.getProperty(key, ""));
      }

      return msg;
   }

   private boolean isBeing(String mode) {
      if (Utils.getBooleanOrDefault(this.properties, "session." + mode, false)) {
         return true;
      } else if (PropertyValue.getValue(this, "session", mode, false)) {
         return true;
      } else {
         int i = 1;
         String base = mode + ".course.";
         String aCourse = this.properties.getProperty(base + i);

         for(String sessionContext = this.getSessionContext(); aCourse != null; aCourse = this.properties.getProperty(base + i)) {
            aCourse = aCourse.trim();
            if (aCourse.equals(this.course) || aCourse.equals(sessionContext)) {
               return true;
            }

            ++i;
         }

         i = 1;
         base = mode + ".id.";

         for(String anId = this.properties.getProperty(base + i); anId != null; anId = this.properties.getProperty(base + i)) {
            if (anId.trim().equals(this.id)) {
               return true;
            }

            ++i;
         }

         return false;
      }
   }

   public boolean isBeingHarvested() {
      return this.isBeing("harvest");
   }

   public boolean isBeingRestarted() {
      return this.isBeing("restart");
   }

   private void createOStools() throws MalformedURLException, IOException {
      String os = ClientShared.getOSString();
      Logger.log(java.util.logging.Level.FINE, "Creating ", os + " tools");
      if (ClientShared.isWindowsOS()) {
         this.createOStoolFileFromResourceOrDownload("Eula.txt");
         this.createOStoolFileFromResourceOrDownload("handle.exe");
         if (Utils.getBooleanOrDefault(this.properties, "monitoring.processes.required", false)) {
            this.createOStoolFileFromResourceOrDownload("pssuspend.exe");
            this.createOStoolFileFromResourceOrDownload("pskill.exe");
         }
      }

      Logger.log(java.util.logging.Level.FINE, "Created ", os + " tools");
   }

   private void downloadOStoolFile(String fileName) throws IOException {
      boolean ok = false;
      int tries = 0;

      do {
         try {
            Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
            WebTarget webTarget = client.target(ClientShared.BASE_CMS).path("exam").path(fileName);
            Invocation.Builder invocationBuilder = webTarget.request();
            invocationBuilder.cookie("token", this.getToken());
            Response response = invocationBuilder.get();
            InputStream is = (InputStream)response.readEntity(InputStream.class);
            File downloadsDirectory = new File(ClientShared.DOWNLOADS_DIR);
            Utils.getAndStoreFile(is, fileName, downloadsDirectory);
            is.close();
            ok = true;
         } catch (ProcessingException | IOException e) {
            ++tries;
            if (tries >= 10) {
               throw e;
            }

            Sleeper.sleep(3000, 1000);
         }
      } while(!ok && tries < 10);

   }

   public void createOStoolFileFromResourceOrDownload(String fileName) throws IOException {
      InputStream fis = null;
      if (PropertyValue.getValue(this, "session", "app_tools_required", true)) {
         fis = Invigilator.class.getResourceAsStream("/app/" + fileName);
      }

      if (fis == null) {
         this.downloadOStoolFile(fileName);
      } else {
         File downloadedFile = new File(ClientShared.DOWNLOADS_DIR, fileName);
         FileOutputStream fos = new FileOutputStream(downloadedFile);
         Utils.copyInputStream(fis, fos);
         fis.close();
      }

   }

   private void createStudentNotes() {
      String studentNotesFileName = this.properties.getProperty("STUDENT_NOTES_FILE_NAME");
      if (studentNotesFileName != null) {
         File studentNotes = new File(studentNotesFileName);
         Logger.log(java.util.logging.Level.FINE, "Notes selected ", studentNotes.getAbsoluteFile());
         FileInputStream fis = null;

         try {
            fis = new FileInputStream(studentNotes);
            String resourcesDir = ClientShared.getResourcesDirectory(this.course, this.activity);
            if (studentNotesFileName.endsWith(".zip")) {
               Utils.unpackArchive(fis, new File(resourcesDir));
            } else {
               File dest = new File(resourcesDir + File.separator + studentNotes.getName());
               Files.copy(fis, dest.toPath(), new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
            }

            java.util.logging.Level var10000 = java.util.logging.Level.INFO;
            String var10002 = String.valueOf(studentNotes.getAbsoluteFile());
            Logger.log(var10000, "Notes chosen were ", var10002 + ", available in " + resourcesDir);
         } catch (IOException var14) {
            this.logArchiver.put(java.util.logging.Level.WARNING, "Could not access notes " + studentNotes.getAbsolutePath());
         } finally {
            if (fis != null) {
               try {
                  fis.close();
               } catch (IOException var13) {
               }
            }

         }
      }

   }

   private void createResources() {
      String resourcesDirectory = ClientShared.getResourcesDirectory(this.course, this.activity);
      Logger.log(java.util.logging.Level.FINE, "Creating ", "resources: " + resourcesDirectory);

      try {
         Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
         WebTarget webTarget = client.target(ClientShared.BASE_EXAM).path("deployed").path("resources");
         Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
         invocationBuilder.accept(new String[]{"application/zip"});
         invocationBuilder.cookie("token", this.getToken());
         Form form = new Form();
         form.param("course", this.course);
         form.param("activity", this.activity);
         form.param("version", ClientShared.VERSION);
         form.param("passkey", ClientShared.PASSKEY_EXAM);
         Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
         InputStream is = (InputStream)response.readEntity(InputStream.class);
         File zip = Utils.unpackArchive(is, new File(resourcesDirectory));
         is.close();
         Logger.log(java.util.logging.Level.INFO, "", "Resources can be found in " + resourcesDirectory);
         zip.delete();
      } catch (Exception var9) {
         Logger.log(java.util.logging.Level.INFO, "", "No " + resourcesDirectory + "resources.zip");
         File zip = new File(resourcesDirectory + "resources.zip");
         zip.delete();
         zip = new File(resourcesDirectory + "arc.zip");
         zip.delete();
         File resourcesFolder = new File(resourcesDirectory);
         File[] files = resourcesFolder.listFiles();
         if (files == null || files.length == 0) {
            resourcesFolder.delete();
         }
      }

      Logger.log(java.util.logging.Level.FINE, "Created ", "resources: " + resourcesDirectory);
   }

   private String encryptedArchiveCore() {
      return this.course + "-" + this.activity + "-" + this.canonicalStudentName;
   }

   private void createExam() throws MalformedURLException, IOException {
      String key = this.properties.getProperty("session.key");
      File examDirectory = new File(ClientShared.getExamDirectory(this.course, this.activity));
      File zip = null;
      boolean okayToRestart = this.isBeingRestarted() && key != null;
      String encryptedArchive = null;
      InputStream is = null;
      if (okayToRestart) {
         try {
            File directoryFile;
            if (Utils.getBooleanOrDefault(this.properties, "session.use_comas_folder_for_archive", true)) {
               directoryFile = new File(ClientShared.COMAS_DIRECTORY);
            } else {
               directoryFile = new File(ClientShared.HOME);
            }

            String prefix = Utils.getStringOrDefault(this.properties, "session.prefix", "CoMaS");
            prefix = this.resolveFolderVariables(prefix);
            okayToRestart = false;
            key = key.trim();
            String var10002 = ClientShared.getArchivesDirectory(this.course, this.activity);
            File actualSessionEncryptedArchive = new File(var10002 + "encrypted-" + this.canonicalStudentName + "-exam.zip");
            if (actualSessionEncryptedArchive.exists() && actualSessionEncryptedArchive.canRead()) {
               encryptedArchive = actualSessionEncryptedArchive.getAbsolutePath();
               okayToRestart = this.decryptArchive(key, encryptedArchive, prefix, directoryFile);
               if (okayToRestart) {
                  this.restartedArchiveSessionTime = actualSessionEncryptedArchive.lastModified();
               }

               this.isRestartedSession = okayToRestart;
            }

            if (!okayToRestart) {
               File[] encryptedArchivefilesForStudent = directoryFile.listFiles(new FileFilter() {
                  public boolean accept(File f) {
                     return f.getName().endsWith(Invigilator.this.encryptedArchiveCore());
                  }
               });
               if (encryptedArchivefilesForStudent != null && encryptedArchivefilesForStudent.length > 0) {
                  Arrays.sort(encryptedArchivefilesForStudent, new Comparator() {
                     public int compare(File a, File b) {
                        long alastModified = a.lastModified();
                        long blastModified = b.lastModified();
                        if (alastModified > blastModified) {
                           return -1;
                        } else {
                           return alastModified == blastModified ? 0 : 1;
                        }
                     }
                  });
                  if (encryptedArchivefilesForStudent[0].canRead()) {
                     encryptedArchive = encryptedArchivefilesForStudent[0].getAbsolutePath();
                     okayToRestart = this.decryptArchive(key, encryptedArchive, prefix, directoryFile);
                     this.isRestartedSession = okayToRestart;
                     if (okayToRestart) {
                        this.restartedArchiveSessionTime = encryptedArchivefilesForStudent[0].lastModified();
                     }
                  }
               }
            }
         } catch (IOException var14) {
            okayToRestart = false;
            this.isRestartedSession = okayToRestart;
         }
      }

      if (!okayToRestart) {
         this.isRestartedSession = okayToRestart;
         if (this.properties.containsKey("NO_DOWNLOAD_REQUIRED")) {
            Logger.log(java.util.logging.Level.INFO, "", "No download was required, exam directory not overwritten");
            return;
         }

         Logger.log(java.util.logging.Level.FINE, "Processing ", "exam: " + ClientShared.getExamDirectory(this.course, this.activity));
         boolean ok = false;
         int tries = 0;

         do {
            try {
               Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
               WebTarget webTarget = client.target(ClientShared.BASE_EXAM).path("deployed").path("activity");
               Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
               invocationBuilder.accept(new String[]{"application/zip"});
               invocationBuilder.cookie("token", this.getToken());
               Form form = new Form();
               form.param("course", this.course);
               form.param("activity", this.activity);
               form.param("version", ClientShared.VERSION);
               form.param("passkey", ClientShared.PASSKEY_EXAM);
               Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
               is = (InputStream)response.readEntity(InputStream.class);
               zip = Utils.unpackArchive(is, examDirectory);
               is.close();
               zip.delete();
               ok = true;
            } catch (ProcessingException | IOException e) {
               ++tries;
               if (tries >= 10) {
                  throw e;
               }

               Sleeper.sleep(3000, 1000);
            }
         } while(!ok && tries < 10);

         this.sessionStartContext = "Started from instructor archive";
         this.isRestartedSession = false;
         this.updateActualStartAndEstimatedEndTimes();
         if (examDirectory.exists()) {
            File[] examFiles = examDirectory.listFiles(EXAM_FOLDER_FILTER);
            if (examFiles != null && examFiles.length != 0) {
               Logger.log(java.util.logging.Level.INFO, "", "Exam can be found in " + ClientShared.getExamDirectory(this.course, this.activity));
            } else {
               DirectoryUtils.destroyDirectory(examDirectory.getAbsolutePath());
               ClientShared.AUTO_ARCHIVE = false;
            }
         }

         Logger.log(java.util.logging.Level.FINE, "Processed ", "exam: " + ClientShared.getExamDirectory(this.course, this.activity));
      }

   }

   private boolean decryptArchive(String key, String encryptedArchive, String prefix, File directoryFile) throws IOException {
      boolean okayToRestart = false;
      if (encryptedArchive != null) {
         File encryptedArchiveFile = new File(encryptedArchive);
         File examDirectory = new File(ClientShared.getExamDirectory(this.course, this.activity));
         File zip = null;
         File decryptedArchiveFile = File.createTempFile(prefix, ".zip", directoryFile);
         String checksum = "<unknown>";
         InputStream is = null;

         try {
            checksum = Checksum.getSHA256Checksum(encryptedArchive);
            StreamCryptoUtils.streamDecrypt(key, encryptedArchive, decryptedArchiveFile.getAbsolutePath());
            is = new FileInputStream(decryptedArchiveFile);
            zip = Utils.unpackArchive(is, examDirectory);
            okayToRestart = true;
            this.logArchiver.put(Level.DIAGNOSTIC, String.format("Restarted exam from local encrypted archive: %s with checksum: %s", encryptedArchive, checksum));
            String creationTime = sdf_h_mm_ss_a_z.format(new Date(encryptedArchiveFile.lastModified()));
            this.sessionStartContext = "Restarted from local archive created " + creationTime;
            if (Utils.getBooleanOrDefault(this.properties, "session.alert_on_archive_restart", false)) {
               this.alert(this.sessionStartContext);
            }
         } catch (Exception e) {
            okayToRestart = false;
            this.logArchiver.put(Level.DIAGNOSTIC, "Failed to decrypt local encrypted archive to restart exam: " + String.valueOf(e));
         } finally {
            if (is != null) {
               is.close();
            }

            if (zip != null) {
               zip.delete();
            }

            if (decryptedArchiveFile != null) {
               decryptedArchiveFile.delete();
            }

         }
      }

      return okayToRestart;
   }

   private boolean makeDirectory(String dirname) {
      File file = new File(dirname);
      if (!file.exists()) {
         if (!file.mkdirs()) {
            this.done = true;
            Logger.log(java.util.logging.Level.SEVERE, "", "Could not make directory " + dirname);
            this.notifyObservers("Could not make directory " + dirname);
            return false;
         }

         Logger.log(java.util.logging.Level.CONFIG, "", "Directory made: " + dirname);
      } else if (!file.isDirectory()) {
         this.done = true;
         Logger.log(java.util.logging.Level.SEVERE, "", dirname + " is not a directory");
         this.notifyObservers(dirname + " is not a directory");
         return false;
      }

      return true;
   }

   private void setupDirectoryWatching() {
      Logger.log(java.util.logging.Level.FINE, "", "Setting up directory monitoring");
      int maxDepth = Utils.getIntegerOrDefaultInRange(this.properties, "monitor.folder.max_depth", 1, 1, 6);
      boolean log = Utils.getBooleanOrDefault(this.properties, "monitor.folder.log", false);
      ArrayList<String> folders = new ArrayList();
      this.setupFolderOptions("monitor.folder." + ClientShared.getOSString() + ".", folders);
      this.setupFolderOptions("monitor.folder." + this.id + ".", folders);
      this.fileSystemMonitor = new FileSystemMonitor(this.logArchiver, this.name, this.course, this.activity, folders, maxDepth, log, this.resolveVariablesInMessage(ClientShared.SUPPORT_MESSAGE));
      this.fileSystemMonitor.addListener(this);
      this.fileSystemMonitor.open();
      Logger.log(java.util.logging.Level.FINE, "", "Set up directory monitoring with " + folders.size() + " extra folders");
   }

   private void setupFolderOptions(String base, ArrayList folders) {
      int i = 1;

      for(String folderToMonitor = this.properties.getProperty(base + i); folderToMonitor != null; folderToMonitor = this.properties.getProperty(base + i)) {
         String folder = folderToMonitor.trim();
         if (folder.length() > 0) {
            folder = this.resolveFolderVariables(folder);
            File f = new File(folder);
            if (f.exists() && f.isDirectory() && f.canRead() && !folders.contains(folder)) {
               folders.add(folder);
            }
         }

         ++i;
      }

   }

   public String resolveFolderVariables(String folder) {
      String resolved = folder.replace("${NAME}", this.canonicalStudentName);
      resolved = resolved.replace("${/}", File.separator);
      resolved = resolved.replace("${HOME}", System.getProperty("user.home"));
      resolved = resolved.replace("${ID}", this.id);
      resolved = resolved.replace("${COURSE}", this.course);
      resolved = resolved.replace("${ACTIVITY}", this.activity);
      resolved = resolved.replace("${TIME}", String.format("%d", System.currentTimeMillis()));
      resolved = resolved.replace("${RANDOM}", Password.getPassCode());
      String value = this.getStudentPassword();
      resolved = resolved.replace("${PASSWORD}", value == null ? "" : value);
      value = this.properties.getProperty("PASSCODE");
      if (value == null) {
         value = this.properties.getProperty("student.directory.PASSCODE");
      }

      resolved = resolved.replace("${PASSCODE}", value == null ? "" : value);
      resolved = resolved.replace("${TEMP}", System.getProperty("java.io.tmpdir"));
      resolved = resolved.replace("${DRIVE}", ClientShared.isWindowsOS() ? ClientShared.DIR_DRIVE : "");
      resolved = resolved.replace("${MACHINE}", this.hardwareAndSoftwareMonitor.getComputerIdentifier());

      try {
         if (ClientShared.isWindowsOS()) {
            resolved = resolved.replaceAll("\\{2,}", "\\");
         } else {
            resolved = resolved.replaceAll("/{2,}", "/");
         }
      } catch (Exception var6) {
         resolved = resolved;
      }

      return resolved;
   }

   public String getStudentPassword() {
      String value = this.properties.getProperty("PASSWORD");
      if (value == null) {
         value = this.properties.getProperty("student.directory.PASSWORD");
      }

      if (value == null) {
         value = this.properties.getProperty("PASSCODE");
      }

      return value;
   }

   private void stop() {
      Logger.log(java.util.logging.Level.INFO, "", "Services stopping");
      this.updateProgressServlet(20, "Services stopping");

      try {
         if (this.moduleManager != null) {
            this.moduleManager.stop();
         }

         if (this.fileSystemMonitor != null) {
            this.fileSystemMonitor.stop(this.latch);
            Logger.log(java.util.logging.Level.FINE, "", "file monitor closed");
            this.manageUnsanctionedFilesCreated();
            this.fileSystemMonitor = null;
            this.updateProgressServlet(25, "File monitoring ended");
         }

         if (this.sentinel != null) {
            this.sentinel.stop();
            Logger.log(java.util.logging.Level.FINE, "", "Sentinel stopped");
         }

         if (!this.screenShotArchiver.isStopped()) {
            this.screenShotArchiver.stop(this.latch);
            Logger.log(java.util.logging.Level.FINE, "", "Screen shot archiver stopped");
            this.updateProgressServlet(35, "Screen shots stopped");
         }

         if (!this.examArchiver.isStopped()) {
            this.examArchiver.stop(this.latch);
            Logger.log(java.util.logging.Level.FINE, "", "Archiver stopped");
            this.updateProgressServlet(40, "Archiving stopped");
         }

         if (!this.logArchiver.isStopped()) {
            this.logArchiver.stop(this.latch);
            Logger.log(java.util.logging.Level.FINE, "", "Logging stopped");
            this.updateProgressServlet(45, "Logging stopped");
         }

         if (!this.highPriorityLogArchiver.isStopped()) {
            this.highPriorityLogArchiver.stop(this.latch);
            Logger.log(java.util.logging.Level.FINE, "", "High priority logging stopped");
            this.updateProgressServlet(45, "Logging stopped");
         }

         if (!this.stateDistributor.isStopped()) {
            this.stateDistributor.stop(this.latch);
            Logger.log(java.util.logging.Level.FINE, "", "Event management stopped");
            this.updateProgressServlet(50, "Event management stopped");
         }

         if (this.fileResource != null) {
            this.fileResource.close();
            this.fileResource = null;
            Logger.log(java.util.logging.Level.FINE, "", "Open file monitoring stopped");
         }

         if (this.networkResource != null) {
            this.networkResource.close();
            this.networkResource = null;
            Logger.log(java.util.logging.Level.FINE, "", "Connection monitoring stopped");
            this.updateProgressServlet(60, "Connection monitoring stopped");
         }

         if (this.sessionConfigurationModeMonitor != null && this.sessionConfigurationModeMonitor.isRunning()) {
            this.sessionConfigurationModeMonitor.stop();
            Logger.log(java.util.logging.Level.FINE, "", "Session configuration monitor stopped");
            this.updateProgressServlet(70, "Session management stopped");
         }
      } catch (Exception e) {
         this.logArchiver.put(Level.DIAGNOSTIC, "Exception while stopping. " + String.valueOf(e));

         while(this.latch.getCount() > 0L) {
            this.latch.countDown();
         }
      } finally {
         if (this.sessionConfigurationModeMonitor != null && this.sessionConfigurationModeMonitor.isRunning()) {
            this.sessionConfigurationModeMonitor.stop();
         }

      }

      this.stopTools(true);
      this.updateProgressServlet(75, "Tools stopped");
      this.websocketEndpointManager.close();
      this.updateProgressServlet(80, "Communication services stopped");
      TimerService.stop();
      Logger.log(java.util.logging.Level.INFO, "", "Services stopped");
      this.updateProgressServlet(81, "Services stopped");
   }

   public Response authenticate() throws ProcessingException {
      Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
      WebTarget webTarget = client.target(ClientShared.BASE_LOGIN).path("authenticate");
      Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
      invocationBuilder.accept(new String[]{"application/json"});
      Form form = new Form();
      form.param("course", this.course);
      form.param("activity", this.activity);
      form.param("version", ClientShared.VERSION);
      form.param("passkey", ClientShared.PASSKEY_DIRECTORY);
      form.param("name", this.name);
      form.param("url", ClientShared.VIDEO_URL + "/" + this.course + "/" + this.activity + "/" + this.name);
      form.param("type", this.state);
      form.param("description", this.id);
      if (this.passcode != null) {
         form.param("password", this.passcode);
      }

      String token = this.getToken();
      if (token != null) {
         invocationBuilder.cookie("token", token);
         form.param("token", token);
      }

      try {
         Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
         this.authenticationStartProblemTime = 0L;
         this.authenticationFailureCause = null;
         this.successfulAuthenticationTime = System.currentTimeMillis();
         return response;
      } catch (ProcessingException var8) {
         if (this.authenticationFailureCause == null) {
            this.authenticationFailureCause = var8.getCause();
            this.authenticationStartProblemTime = System.currentTimeMillis();
         }

         Logger.output("Authentication Processing Exception: " + String.valueOf(this.authenticationFailureCause));
         throw var8;
      }
   }

   public Response authenticateUsingEmail() throws ProcessingException {
      Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
      WebTarget webTarget = client.target(ClientShared.BASE_LOGIN).path("authenticateUsingEmail");
      Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
      invocationBuilder.accept(new String[]{"application/json"});
      Form form = new Form();
      form.param("course", this.course);
      form.param("activity", this.activity);
      form.param("version", ClientShared.VERSION);
      form.param("passkey", ClientShared.PASSKEY_DIRECTORY);
      form.param("email", this.email);
      form.param("type", this.state);
      if (this.passcode != null) {
         form.param("password", this.passcode);
      }

      String token = this.getToken();
      if (token != null) {
         invocationBuilder.cookie("token", token);
         form.param("token", token);
      }

      try {
         Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
         this.authenticationStartProblemTime = 0L;
         this.authenticationFailureCause = null;
         this.successfulAuthenticationTime = System.currentTimeMillis();
         return response;
      } catch (ProcessingException var8) {
         if (this.authenticationFailureCause == null) {
            this.authenticationFailureCause = var8.getCause();
            this.authenticationStartProblemTime = System.currentTimeMillis();
         }

         Logger.output("Authentication Processing Exception: " + String.valueOf(this.authenticationFailureCause));
         throw var8;
      }
   }

   private long timeSinceSuccessfulAuthentication() {
      return System.currentTimeMillis() - this.successfulAuthenticationTime;
   }

   private String activityKey(String activity, String key) {
      return activity + "-" + key;
   }

   public int canStart() {
      try {
         Response response = this.authenticate();
         String rtn = (String)response.readEntity(String.class);
         if (rtn.equalsIgnoreCase("{\"DOES NOT EXIST\"}")) {
            return -1;
         } else if (rtn.equalsIgnoreCase("{\"ILLEGAL VERSION\"}")) {
            return -2;
         } else if (!rtn.equalsIgnoreCase("{\"STOPPED\"}") && !rtn.equalsIgnoreCase("{\"STOPPING\"}")) {
            if (rtn.equalsIgnoreCase("{\"IS RESTRICTED\"}")) {
               return -4;
            } else {
               JsonReader reader = Json.createReader(new StringReader(rtn));
               JsonObject meAsJson = reader.readObject();
               if (meAsJson != null) {
                  String nowAsString = meAsJson.getJsonString(this.activityKey(this.activity, "ACCESS")).getString();
                  this.properties.setProperty("CURRENT_TIME", nowAsString);
                  String startAsString = this.getJsonString(meAsJson, "START_MSECS", "0");
                  this.properties.setProperty("student.directory.ALLOWED_START_TIME", this.getJsonString(meAsJson, "START", "1970/01/01 7:00 AM"));
                  String endAsString = this.getJsonString(meAsJson, "END_MSECS", "9223372036854775807");
                  this.properties.setProperty("student.directory.ALLOWED_END_TIME", this.getJsonString(meAsJson, "END", "2099/01/01 7:00 AM"));
                  String durationAsString = this.getJsonString(meAsJson, "DURATION", this.properties.getProperty("DURATION", "180"));
                  this.properties.setProperty("student.directory.ALLOWED_DURATION", durationAsString);
                  String tokenAsString = meAsJson.getJsonString("TOKEN").getString();
                  this.properties.setProperty("TOKEN", tokenAsString);
                  long now = Long.parseLong(nowAsString);
                  long start = Long.parseLong(startAsString);
                  long end = Long.parseLong(endAsString);
                  if (now < start) {
                     return 0;
                  } else {
                     return now > end ? 0 : 1;
                  }
               } else {
                  return 0;
               }
            }
         } else {
            return -3;
         }
      } catch (Exception e) {
         Logger.log(java.util.logging.Level.WARNING, "Start exam time check error: ", e);
         return 0;
      }
   }

   String getJsonString(JsonObject o, String key, String defaultValue) {
      JsonString jsonString = o.getJsonString(this.activityKey(this.activity, key));
      return jsonString == null ? defaultValue : jsonString.getString();
   }

   private boolean authenticateAndValidateSession() {
      if (!this.prefersToAuthenticate() && !this.isLoggingIn()) {
         return true;
      } else {
         try {
            Response response = this.authenticate();
            if (response == null) {
               return false;
            } else {
               String rtn = (String)response.readEntity(String.class);
               if (rtn == null) {
                  return false;
               } else {
                  this.isAllowedToUpload = true;
                  if (rtn.equalsIgnoreCase("{\"IS RESTRICTED\"}")) {
                     if (!this.isInEndingState()) {
                        this.setInvigilatorState(InvigilatorState.ending);
                        Thread t = new Thread(new SessionEndTask(this, ProgressServlet.getSingleton(), "Session ended because " + this.getNameAndID() + " denied access"));
                        t.start();
                        this.setEndOfSessionStateFlags();
                        WebAlert.errorDialog(this.getNameAndID() + " has been denied access.\n\nYour session will now end");
                        this.setTitleStatus(-3);
                        return false;
                     }
                  } else if (rtn.equalsIgnoreCase("{\"DOES NOT EXIST\"}")) {
                     this.setEndOfSessionStateFlags();
                     this.notifyObservers("You are not registered. Please quit now.");
                  } else if (rtn.equalsIgnoreCase("{\"ILLEGAL VERSION\"}")) {
                     this.setEndOfSessionStateFlags();
                     this.logArchiver.put(java.util.logging.Level.SEVERE, "Illegal version detected (" + ClientShared.VERSION + ")");
                     String var10001 = ClientShared.VERSION;
                     this.notifyObservers("Illegal version detected (" + var10001 + ").\n" + this.resolveVariablesInMessage(ClientShared.SUPPORT_MESSAGE) + "\n\nYour session will now end");
                     System.exit(0);
                  } else if (rtn.equalsIgnoreCase("{\"STOPPED\"}")) {
                     this.done = true;
                     String endLogMessage = "Session is over, please wait for archive upload";
                     if (!ClientShared.AUTO_ARCHIVE) {
                        endLogMessage = "Session is over, please wait for session to end";
                     }

                     Logger.log(java.util.logging.Level.INFO, "", endLogMessage);
                     this.setTitleStatus(-4);
                     this.notifyObservers(endLogMessage);
                     this.endTheSession();
                     this.exitAfterSessionEnd(0);
                  } else if (rtn.equalsIgnoreCase("{\"STOPPING\"}")) {
                     Logger.log(java.util.logging.Level.FINE, "Server is stopping", "");
                  } else {
                     JsonReader reader = Json.createReader(new StringReader(rtn));
                     JsonObject meAsJson = reader.readObject();
                     String tokenAsString = meAsJson.getJsonString("TOKEN").getString();
                     if (tokenAsString != null) {
                        this.setToken(tokenAsString);
                     }

                     Logger.log(java.util.logging.Level.FINE, "[" + response.getStatus() + "] ", "Login");
                  }

                  boolean okay = response.getStatus() < 204;
                  if (!this.isDone()) {
                     if (okay) {
                        if (this.state.equals("Login")) {
                           this.setTitleStatus(1);
                        }
                     } else {
                        this.setTitleStatus(-1);
                     }
                  }

                  return okay;
               }
            }
         } catch (Exception e) {
            Logger.log(java.util.logging.Level.INFO, "", "Could not login: " + String.valueOf(e));
            this.setTitleStatus(-1);
            return false;
         }
      }
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public String getTitle() {
      return this.title;
   }

   private void setTitleStatus(int status) {
      if (status == 1) {
         this.setTitle(" ✅ ");
      } else if (status == -1) {
         this.setTitle(" ❌ ");
      } else if (status == 0) {
         this.setTitle(" \ud83d\udd50 ");
      } else if (status == -2) {
         this.setTitle(" \ue252 ");
      } else if (status == -3) {
         this.setTitle(" ⛔ ");
      } else if (status == -4) {
         this.setTitle(" ⏻ ");
      }

   }

   private void downloadTools() {
      if (PropertyValue.getValue(this, "session", "tools_required", false)) {
         int tries = 0;
         boolean succeeded = false;
         Exception exception = null;

         do {
            ++tries;

            try {
               Logger.log(java.util.logging.Level.FINE, "Processing ", "tools: " + ClientShared.getToolsDirectory(this.course, this.activity));
               Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
               WebTarget webTarget = client.target(ClientShared.BASE_CMS).path("exam").path("tools.zip");
               Invocation.Builder invocationBuilder = webTarget.request();
               invocationBuilder.accept(new String[]{"application/zip"});
               invocationBuilder.cookie("token", this.getToken());
               Response response = invocationBuilder.get();
               InputStream is = (InputStream)response.readEntity(InputStream.class);
               File toolsDirectory = new File(ClientShared.getToolsDirectory(this.course, this.activity));
               File zip = Utils.unpackArchive(is, toolsDirectory);
               is.close();
               Logger.log(java.util.logging.Level.INFO, "", "Tools can be found in " + ClientShared.getToolsDirectory(this.course, this.activity));
               zip.delete();
               succeeded = true;
            } catch (Exception e) {
               exception = e;
               Sleeper.sleep(1000, 1000);
            }
         } while(tries < 10 && !succeeded);

         if (!succeeded && exception != null) {
            Logger.log(java.util.logging.Level.WARNING, "Could not process tools or services: ", exception.toString());
            this.notifyObservers("No tools available");
         }

      }
   }

   public Thread runExam() {
      this.thread = new Thread(new Worker());
      this.thread.setName("session");
      this.thread.setUncaughtExceptionHandler(this);
      this.thread.start();
      return this.thread;
   }

   private void monitorUser() {
      int i = 0;

      while(this.continueSession()) {
         this.websocketEndpointManager.ping();
         this.takeScreenShot(ClientShared.MIN_INTERVAL_BETWEEN_SCREEN_SHOTS_IN_MSECS);

         try {
            long timeToSleep = 1000L * (long)ClientShared.MIN_INTERVAL + ThreadLocalRandom.current().nextLong(1000L * (long)ClientShared.MAX_INTERVAL);
            this.wakeup = System.currentTimeMillis() + timeToSleep;
            Thread.sleep(timeToSleep);
         } catch (InterruptedException var4) {
         }

         ++i;
         boolean timeToArchive = i % ClientShared.AUTO_ARCHIVE_FREQUENCY == 0;
         if (timeToArchive) {
            this.createAndUploadArchive(true);
         }
      }

   }

   private boolean continueSession() {
      String currentState = this.state;
      if (this.failures > 0 && this.needsToAuthenticate()) {
         this.state = "Logging in";
      }

      boolean okay = this.authenticateAndValidateSession();
      if (!this.isEndedSession()) {
         this.websocketEndpointManager.createCommandAndControlChannel();
      }

      if (this.failures > 0) {
         this.state = currentState;
      }

      if (!okay) {
         if (this.failures == 0) {
            this.failureStartTime = System.currentTimeMillis();
            String failureMessage = String.format("Login failed %.03f seconds into session", (double)(this.failureStartTime - this.actualStartTime) / (double)1000.0F);
            this.logArchiver.put(java.util.logging.Level.WARNING, failureMessage, this.createProblemSetEvent("login"));
            this.setTitleStatus(-1);
         }

         this.processFailure();
      } else {
         if (this.failures > 0) {
            this.state = "Login";
            long failureInterval = (System.currentTimeMillis() - this.failureStartTime) / 1000L;
            String failureMessage = String.format("Login successful after %d failures in %d seconds", this.failures, failureInterval);
            this.logArchiver.put(java.util.logging.Level.INFO, failureMessage, this.createProblemClearEvent("login"));
            this.setTitleStatus(1);
         } else {
            this.state = "Login";
         }

         this.failures = 0;
      }

      return this.failures < ClientShared.MAX_SESSION_FAILURES && !this.isDone() && !this.isEndedSession();
   }

   public boolean isConnected() {
      return this.failures == 0;
   }

   public boolean needsToAuthenticate() {
      return this.timeSinceSuccessfulAuthentication() > (long)ClientShared.LEASE_TIME * 800L;
   }

   public boolean prefersToAuthenticate() {
      return this.timeSinceSuccessfulAuthentication() > (long)ClientShared.MIN_AUTHENTICATION_INTERVAL * 1000L;
   }

   private void processFailure() {
      ++this.failures;
      if (this.failures >= ClientShared.FAILURES_UNTIL_MOVE_TO_BACKUP && ClientShared.BACKUP_HOST != null && ClientShared.BACKUP_SERVERS != null) {
         if (this.canChangeServerAfterException(this.authenticationFailureCause)) {
            if (ClientShared.BACKUP_SERVERS.nextIsOkToUse()) {
               String server = ClientShared.BACKUP_SERVERS.next(true);
               if (this.changeServer(server, "automatic")) {
                  return;
               }

               ClientShared.BACKUP_SERVERS.reset(server);
            }
         } else if (this.failures % ClientShared.ALERT_FAILURE_FREQUENCY != 0 && this.failures % ClientShared.FAILURES_UNTIL_MOVE_TO_BACKUP == 0) {
            String msg = String.format("You have a networking problem. The issue is:\n%s.\nUnable to switch to a backup server.\n\nYour session will continue", this.resolvedNetworkingError(this.authenticationFailureCause));
            (new DisappearingAlert((long)ClientShared.DISAPPEARING_ALERT_TIMEOUT, 1, 2)).show(msg, "CoMaS Networking Alert");
            return;
         }
      }

      if (this.failures % ClientShared.ALERT_FAILURE_FREQUENCY == 0) {
         String msg;
         if (this.authenticationFailureCause != null) {
            float disconnectionTime = (float)(System.currentTimeMillis() - this.authenticationStartProblemTime) / 1000.0F;
            msg = String.format("You have been disconnected for %.03f seconds.\nYou have failed to connect %d times to %s. The issue is:\n%s.\n%s\n\nYour session will continue", disconnectionTime, this.failures, ClientShared.DIRECTORY_HOST, this.resolvedNetworkingError(this.authenticationFailureCause), this.resolveVariablesInMessage(ClientShared.SUPPORT_MESSAGE));
         } else {
            msg = String.format("Failed to connect %d times to %s.\n%s\n\nYour session will continue", this.failures, ClientShared.DIRECTORY_HOST, this.resolveVariablesInMessage(ClientShared.SUPPORT_MESSAGE));
         }

         (new DisappearingAlert((long)ClientShared.DISAPPEARING_ALERT_TIMEOUT, 1, 2)).show(msg, "CoMaS Networking Alert");
      }

   }

   public boolean isNetworkUnreachable() {
      return !this.canChangeServerAfterException(this.authenticationFailureCause);
   }

   private boolean canChangeServerAfterException(Throwable exception) {
      if (exception == null) {
         return true;
      } else if (exception instanceof NoRouteToHostException) {
         return false;
      } else {
         String msg = exception.getMessage();
         if (msg == null) {
            return true;
         } else {
            return !msg.startsWith("Network is unreachable");
         }
      }
   }

   public String resolvedNetworkingError(Throwable exception) {
      if (exception == null) {
         return "Unknown exception";
      } else if (exception instanceof NoRouteToHostException) {
         return "There is no route to " + ClientShared.DIRECTORY_HOST + ". A connection cannot be made";
      } else if (exception instanceof SocketTimeoutException) {
         return "A connection to " + ClientShared.DIRECTORY_HOST + " timed out. Your network is slow";
      } else if (exception instanceof UnknownHostException) {
         return "The address of " + ClientShared.DIRECTORY_HOST + " cannot be determined. This may be a DNS problem";
      } else if (exception instanceof PortUnreachableException) {
         return "A service on " + ClientShared.DIRECTORY_HOST + " cannot be reached. This may be a VPN or firewall problem";
      } else {
         String msg = exception.getMessage();
         return msg == null ? exception.toString() : msg;
      }
   }

   private void stopTools(boolean tryOnFail) {
      this.sendMessageToTools(tryOnFail, "stop");
   }

   public void sendMessageToTools(boolean tryOnFail, String message) {
      if (this.initHasRun) {
         try {
            String var10000 = this.name.replace(" ", "-");
            String identity = var10000 + "-" + this.id;
            this.websocketEndpointManager.sendMessage(new Message(identity, identity, message, ""));
         } catch (Exception var4) {
            this.websocketEndpointManager.close();
            if (tryOnFail) {
               this.websocketEndpointManager.createCommandAndControlChannel();
               this.sendMessageToTools(false, message);
            }
         }
      }

   }

   public Object[] createProblemUnknownEvent(String name) {
      return this.reportManager.createProblemUnknownEvent(name);
   }

   public Object[] createProblemSetEvent(String name) {
      return this.reportManager.createProblemSetEvent(name);
   }

   public Object[] createProblemClearEvent(String name) {
      return this.reportManager.createProblemClearEvent(name);
   }

   public Object[] createProblemUpdateEvent(String name) {
      return this.reportManager.createProblemUpdateEvent(name);
   }

   public boolean annotateAndSaveImageBefore(long time) throws IOException {
      if (this.circularImageBuffer == null) {
         return false;
      } else {
         ArrayList<BufferedImage> images = (ArrayList)this.circularImageBuffer.before(time);
         if (images == null) {
            return false;
         } else {
            int i = 0;
            String screensDirString = ClientShared.getScreensDirectory(this.course, this.activity);

            for(BufferedImage image : images) {
               this.overlayImage(image, true, time);
               String fileName = screensDirString + this.name + "-" + (time + (long)i) + ".jpg";
               File imageFile = new File(fileName);
               ImageIO.write(image, "jpg", imageFile);
               this.screenShotArchiver.put(new AnnotatedObject(fileName, true));
               ++i;
            }

            return true;
         }
      }
   }

   private BufferedImage getQRCode(String barcodeText) throws WriterException {
      QRCodeWriter barcodeWriter = new QRCodeWriter();
      BitMatrix bitMatrix = barcodeWriter.encode(barcodeText, BarcodeFormat.QR_CODE, 50, 50);
      return MatrixToImageWriter.toBufferedImage(bitMatrix);
   }

   public void takeScreenShot() {
      this.takeScreenShot(0L, false);
   }

   public void takeScreenShot(long minIntervalSinceLastScreenShot) {
      this.takeScreenShot(minIntervalSinceLastScreenShot, false);
   }

   public void takeScreenShot(boolean annotate) {
      this.takeScreenShot(0L, annotate);
   }

   public void takeScreenShot(long minIntervalSinceLastScreenShot, boolean annotate) {
      if (ClientShared.USE_SCREEN_SHOTS) {
         this.screenShotLock.lock();

         try {
            String screensDirString = ClientShared.getScreensDirectory(this.course, this.activity);
            File screensDir = new File(screensDirString);
            if (!screensDir.exists() || !screensDir.isDirectory() || !screensDir.canWrite()) {
               throw new IOException(screensDirString + " cannot be accessed");
            }

            long timeInMsec = System.currentTimeMillis();
            if (timeInMsec - this.lastSnapshotTime >= minIntervalSinceLastScreenShot) {
               BufferedImage[] screenFullImage = Displays.getImages();
               if (timeInMsec > this.wakeup + (long)Math.max(60000, ClientShared.MAX_INTERVAL * 1000)) {
                  this.logArchiver.put(java.util.logging.Level.INFO, String.format("Machine was possibly suspended (%s)", (new Date(this.wakeup)).toString()), this.createProblemUnknownEvent("suspended"));
               }

               this.lastSnapshotTime = timeInMsec;

               for(int indexOfScreenShot = 0; indexOfScreenShot < screenFullImage.length; ++indexOfScreenShot) {
                  if (screenFullImage[indexOfScreenShot] == null) {
                     throw new IOException("Full screen image cannot be acquired");
                  }

                  this.overlayImage(screenFullImage[indexOfScreenShot], annotate, timeInMsec);
                  String fileName = screensDirString + this.name + "-" + (timeInMsec + (long)indexOfScreenShot) + ".jpg";
                  File imageFile = new File(fileName);
                  ImageIO.write(screenFullImage[indexOfScreenShot], "jpg", imageFile);
                  this.screenShotArchiver.put(new AnnotatedObject(fileName, annotate));
                  this.state = "Login";
                  if (this.screenShotProblem) {
                     this.screenShotProblem = false;
                     this.setTitleStatus(1);
                     String msg = "Screen shot saving is now okay";
                     this.logArchiver.put(java.util.logging.Level.INFO, msg, this.createProblemClearEvent("screenshot"));
                  }
               }

               return;
            }
         } catch (Exception e) {
            this.screenShotProblem = true;
            this.setTitleStatus(-1);
            this.state = "Issue:Screen";
            String msg;
            if (e.getMessage() != null) {
               msg = "Cannot save screen: " + e.getMessage();
            } else {
               msg = "Cannot save screen: " + String.valueOf(e);
            }

            this.logArchiver.put(java.util.logging.Level.WARNING, msg, this.createProblemSetEvent("screenshot"));
            return;
         } finally {
            this.screenShotLock.unlock();
         }

      }
   }

   public void overlayImage(BufferedImage image, boolean annotate, long time) {
      if (this.annotationCode == null) {
         try {
            String annotationImage = this.properties.getProperty("screenshot.annotation", "/images/warning-icon-64x64.png");
            this.annotationCode = ImageIO.read(Invigilator.class.getResourceAsStream(annotationImage));
         } catch (Exception var6) {
            annotate = false;
         }
      }

      this.overlayImage(image, annotate ? this.annotationCode : null, time);
   }

   public void overlayImage(BufferedImage image, BufferedImage annotationImage, long time) {
      if (this.qrCode == null && ClientShared.SCREEN_SHOT_QR_CODE_REQUIRED) {
         try {
            String host = this.properties.getProperty("LOCAL_ADDRESS");
            String macAddress = MacAddress.getMACAddresses();
            String processorID = this.hardwareAndSoftwareMonitor.getProcessorSerialNumber();
            if (host != null && macAddress != null) {
               this.qrLabel = String.format("%s/%s/%s/%s/%s/%s/%s", this.course, this.activity, this.name, this.id, host, macAddress, processorID);
            } else {
               this.qrLabel = String.format("%s/%s/%s/%s/%s/%s", this.course, this.activity, this.name, this.id, host, processorID);
            }

            this.qrCode = this.getQRCode(this.qrLabel);
         } catch (Exception var9) {
         }
      }

      Graphics g = image.getGraphics();
      if (this.qrCode != null) {
         g.drawImage(this.qrCode, image.getWidth() - this.qrCode.getWidth(), 0, (ImageObserver)null);
      }

      if (annotationImage != null) {
         g.drawImage(annotationImage, (image.getWidth() - annotationImage.getWidth()) / 2, (image.getHeight() - annotationImage.getHeight()) / 2, (ImageObserver)null);
      }

      BufferedImage wci = WebcamServlet.getSingleton().getWebcamImage();
      if (wci == null) {
         wci = IDVerificationServlet.getSingleton().getWebcamIDImage();
      }

      if (wci != null) {
         g.drawImage(wci, image.getWidth() - wci.getWidth(), image.getHeight() - wci.getHeight(), (ImageObserver)null);
      }

      if (ClientShared.SCREEN_SHOT_TIMESTAMP_REQUIRED) {
         int x = Math.round((float)image.getWidth() * ClientShared.SCREEN_SHOT_TIMESTAMP_WIDTH) - 150;
         if (x < 0) {
            x = 0;
         }

         if (x > image.getWidth() - 320) {
            x = image.getWidth() - 320;
         }

         int y = Math.round((float)image.getHeight() * ClientShared.SCREEN_SHOT_TIMESTAMP_HEIGHT);
         if (y > image.getHeight() - 50) {
            y = image.getHeight() - 50;
         }

         g.clearRect(x, y, 320, 50);
         g.setFont(g.getFont().deriveFont(20.0F));
         if (this.state.equals("Login")) {
            g.setColor(Color.white);
         } else if (this.state.contains("Issue:") || this.archiveProblem || this.screenShotProblem || annotationImage != null) {
            g.setColor(Color.ORANGE);
         }

         if (!this.websocketEndpointManager.isOpen()) {
            g.setColor(Color.YELLOW);
         }

         if (!this.isConnected()) {
            g.setColor(Color.RED);
         }

         g.drawString((new Date(time)).toString(), x + 10, y + 25);
      }

      g.dispose();
   }

   public File archiveFile() {
      String archive_name = this.canonicalStudentName + "-exam.zip";
      String archive_directory = ClientShared.getArchivesDirectory(this.course, this.activity);
      String archive = archive_directory + archive_name;
      File file = new File(archive);
      return file;
   }

   public boolean createAndUploadArchive(boolean upload) {
      if (!this.initHasRun) {
         return true;
      } else if (!ClientShared.AUTO_ARCHIVE) {
         return true;
      } else {
         this.archiveLock.lock();

         try {
            String examDir = ClientShared.getExamDirectory(this.course, this.activity);
            File examDirFile = new File(examDir);
            if (!this.archiveProblem) {
               if (!examDirFile.exists()) {
                  return true;
               }

               File[] filesInExamDir = examDirFile.listFiles(EXAM_FOLDER_FILTER);
               if (filesInExamDir == null || filesInExamDir.length == 0) {
                  return true;
               }
            }

            boolean rtn = true;
            String archive_name = this.canonicalStudentName + "-exam.zip";
            String archive_directory = ClientShared.getArchivesDirectory(this.course, this.activity);
            File archiveDirectoryFile = new File(archive_directory);
            if (!archiveDirectoryFile.exists() || !archiveDirectoryFile.isDirectory() || !archiveDirectoryFile.canWrite()) {
               this.logArchiver.put(java.util.logging.Level.WARNING, "Cannot access or write to archive directory: " + archive_directory, this.createProblemSetEvent("archive"));
               this.state = "Issue:Archive";
               this.setTitleStatus(-1);
               this.archiveProblem = true;
               rtn = false;
            }

            String archive = archive_directory + archive_name;
            File file = new File(archive);
            if (file.exists()) {
               String var10000 = this.canonicalStudentName;
               archive_name = var10000 + "-" + (new Date()).getTime() + "-exam.zip";
               file.renameTo(new File(archive_directory + archive_name));
            }

            try {
               this.createHiddenState(examDirFile);
               if (examDirFile.exists()) {
                  Zip.pack(examDir, archive);
                  UploadCheckServlet.getSingleton().computeArchiveAttributes();
                  Logger.log(java.util.logging.Level.INFO, "", "Archive created: " + archive);
               }
            } catch (Exception e) {
               this.state = "Issue:Archive";
               this.archiveProblem = true;
               this.setTitleStatus(-1);
               String msg = "Could not create archive: " + archive + " {" + e.getClass().getName() + "}";
               this.logArchiver.put(java.util.logging.Level.WARNING, msg, this.createProblemSetEvent("archive"));
               rtn = false;
            }

            if (rtn && upload) {
               this.examArchiver.put(new AnnotatedObject(archive, false));
            }

            if (this.archiveProblem && rtn) {
               this.setTitleStatus(1);
               String msg = "Archive creation is now okay";
               this.archiveProblem = false;
               this.logArchiver.put(java.util.logging.Level.INFO, msg, this.createProblemClearEvent("archive"));
            }

            boolean var13 = rtn;
            return var13;
         } finally {
            this.archiveLock.unlock();
         }
      }
   }

   private void createHiddenState(File dir) {
      if (dir.exists() && dir.canRead() && dir.canWrite()) {
         File comas_hidden_file = new File(dir, ".comas");
         PrintWriter pw = null;

         try {
            pw = new PrintWriter(comas_hidden_file, StandardCharsets.UTF_8);
            pw.print("session.time=");
            pw.println(System.currentTimeMillis());
            if (this.actualStartTime > 0L) {
               pw.print("session.start_time=");
               pw.println(this.actualStartTime);
            }

            if (this.isDone() || this.isEndedSession()) {
               pw.print("session.final_archive=");
               pw.println(this.isDone() || this.isEndedSession());
            }

            if (this.actualEndTime > 0L) {
               pw.print("session.end_time=");
               pw.println(this.actualEndTime);
            }

            pw.print("session.date=");
            pw.println(new Date());
            pw.print("session.location=");
            pw.println(this.getLocation(true));
            pw.print("session.id=");
            pw.println(this.course + "/" + this.activity);
            pw.print("student.id=");
            pw.println(this.id);
            pw.print("student.name=");
            pw.println(this.canonicalStudentName);
            if (this.qrLabel != null) {
               pw.print("session.label=");
               pw.println(this.qrLabel);
            }

            pw.print("machine.id=");
            pw.println(this.hardwareAndSoftwareMonitor.getComputerIdentifier());
         } catch (FileNotFoundException var16) {
         } catch (IOException var17) {
         } finally {
            if (pw != null) {
               pw.close();
            }

            if (ClientShared.isWindowsOS() && comas_hidden_file.exists()) {
               Path path = comas_hidden_file.toPath();

               try {
                  Files.setAttribute(path, "dos:hidden", true);
               } catch (IOException var15) {
               }
            }

         }
      }

   }

   public void finalizeOnArchive(File file) {
      synchronized(this.properties) {
         boolean fileHasBeenDeleted = false;
         if (Utils.getBooleanOrDefault(this.properties, "session.encrypt_on_archive", false)) {
            Logger.log(java.util.logging.Level.FINE, "Finalize: ", file);
            String key = this.properties.getProperty("session.key");
            if (key != null && this.wantToEncryptArchivedFile(file)) {
               key = key.trim();

               try {
                  File encryptedFile = new File(file.getParent(), "encrypted-" + file.getName());
                  if (encryptedFile.exists()) {
                     this.updateFileSystemMonitorDontCareFiles(encryptedFile);
                     String var10003 = file.getParent();
                     long var10004 = file.lastModified();
                     encryptedFile.renameTo(new File(var10003, "encrypted-" + var10004 + "-" + file.getName()));
                  }

                  StreamCryptoUtils.streamEncrypt(key, file.getAbsolutePath(), encryptedFile.getAbsolutePath());
                  if (Utils.getBooleanOrDefault(this.properties, "session.delete_archive_on_encrypt", true)) {
                     this.updateFileSystemMonitorDontCareFiles(file);
                     file.delete();
                     fileHasBeenDeleted = true;
                  }
               } catch (Exception e) {
                  this.logArchiver.put(Level.DIAGNOSTIC, "Failed to create local encrypted file: " + String.valueOf(e));
               }
            }
         }

         if (!fileHasBeenDeleted && Utils.getBooleanOrDefault(this.properties, "session.delete_on_archive", false) && this.wantToDeleteArchivedFile(file)) {
            this.updateFileSystemMonitorDontCareFiles(file);
            file.delete();
         }

      }
   }

   private boolean wantToEncryptArchivedFile(File file) {
      return file.getName().endsWith(".zip") && Utils.getBooleanOrDefault(this.properties, "session.encrypt_zip", true) || file.getName().endsWith(".jpg") && Utils.getBooleanOrDefault(this.properties, "session.encrypt_jpg", true);
   }

   private boolean wantToDeleteArchivedFile(File file) {
      return file.getName().endsWith(".zip") && Utils.getBooleanOrDefault(this.properties, "session.delete_zip", true) || file.getName().endsWith(".jpg") && Utils.getBooleanOrDefault(this.properties, "session.delete_jpg", true);
   }

   private void updateFileSystemMonitorDontCareFiles(File file) {
      if (this.fileSystemMonitor != null) {
         this.fileSystemMonitor.addDontCareFile(file);
      }

   }

   public void resourceEvent(Resource resource, String type, String description) {
      if (resource instanceof FileSystemMonitor) {
         if (type.equals("PROBLEM")) {
            (new DisappearingAlert((long)ClientShared.DISAPPEARING_ALERT_TIMEOUT, 1)).show(description);
         } else if (type.equals("ALERT")) {
            if (Utils.getBooleanOrDefault(this.properties, "alert.show_on_delete", true)) {
               this.alert(description);
            }

            this.logArchiver.put(java.util.logging.Level.WARNING, description, new Object[]{resource.getClass().getName(), type});
         } else if (type.equals("CLOSE")) {
            if (!this.isEndedSession()) {
               this.state = "Issue:Files";
               this.logArchiver.put(java.util.logging.Level.SEVERE, description, new Object[]{resource.getClass().getName(), type});
               if (this.fileSystemMonitor != null) {
                  this.fileSystemMonitor.restart();
               }
            }
         } else {
            this.logArchiver.put(java.util.logging.Level.parse(type), description, this.createProblemUnknownEvent("file_deletion"));
         }
      } else if (resource instanceof ResourceMonitor && !this.isEndedSession()) {
         this.logArchiver.put(Level.LOGGED, description, new Object[]{resource.getClass().getName(), type});
         if (resource == this.fileResource && description.contains("handle.exe")) {
            try {
               this.createOStoolFileFromResourceOrDownload("handle.exe");
            } catch (IOException var5) {
            }
         }
      }

   }

   public void alert(String description) {
      this.alert = description;
      this.notifyObservers();
   }

   public void notifyObservers(Object arg) {
      this.setChanged();
      super.notifyObservers(arg);
   }

   private void manageLogFiles() {
      boolean logsToBeSaved = Utils.getBooleanOrDefault(this.properties, "logs.save", true);
      if (logsToBeSaved) {
         try {
            Path pathToLogFile = Paths.get(ClientShared.DIR, this.course, this.activity, "logs", "comas-system-log.html");
            File logFile = pathToLogFile.toFile();
            if (logFile.exists()) {
               String savedName = "comas-system-" + System.currentTimeMillis() + "-log.html";
               logFile.renameTo(Paths.get(ClientShared.DIR, this.course, this.activity, "logs", savedName).toFile());
            }

            String logDir = ClientShared.HOME;
            if (ClientShared.isWriteableDirectory(logDir)) {
               logDir = ClientShared.HOME;
            } else {
               logDir = ClientShared.getDesktopDirectory();
            }

            Logger.close();
            Path path = Paths.get(logDir, "comas-system-log.html");
            Files.copy(path, pathToLogFile, StandardCopyOption.REPLACE_EXISTING);
            if (Utils.getBooleanOrDefault(this.properties, "logs.delete_on_exit", false)) {
               path.toFile().delete();
               path = Paths.get(logDir, "comas-system-log.csv");
               path.toFile().delete();
               path = Paths.get(logDir, "comas-base-log.html");
               path.toFile().delete();
               path = Paths.get(logDir, "comas-base-log.csv");
               path.toFile().delete();
               boolean logsToBeViewable = Utils.getBooleanOrDefault(this.properties, "logs.view", true);
               if (logsToBeViewable) {
                  pathToLogFile = Paths.get(ClientShared.DIR, this.course, this.activity, "tools", "logs.html");
                  pathToLogFile.toFile().delete();
               }
            }
         } catch (Exception e) {
            Logger.log(Level.NOTED, "Could not copy log file: ", e);
         }
      }

   }

   private void manageResourceFiles() {
      boolean delete = Utils.getBooleanOrDefault(this.properties, "resources.delete_on_exit", false) || Utils.getBooleanOrDefault(this.properties, "all.delete_on_exit", false);
      if (delete) {
         Path path = Paths.get(ClientShared.DIR, this.course, this.activity, "resources");
         this.manageFilesCommon(path, "Could not remove resources: ");
      }

   }

   private void manageExamFiles() {
      boolean delete = Utils.getBooleanOrDefault(this.properties, "exam.delete_on_exit", false) || Utils.getBooleanOrDefault(this.properties, "all.delete_on_exit", false);
      if (delete) {
         Path path = Paths.get(ClientShared.DIR, this.course, this.activity, "exam");
         this.manageFilesCommon(path, "Could not remove exam: ");
      }

   }

   private void manageScreenFiles() {
      boolean delete = Utils.getBooleanOrDefault(this.properties, "screens.delete_on_exit", false) || Utils.getBooleanOrDefault(this.properties, "all.delete_on_exit", false);
      if (delete) {
         Path path = Paths.get(ClientShared.DIR, this.course, this.activity, "screens");
         this.manageFilesCommon(path, "Could not remove screens: ");
      }

   }

   private void manageArchiveFiles() {
      boolean delete = Utils.getBooleanOrDefault(this.properties, "archives.delete_on_exit", false) || Utils.getBooleanOrDefault(this.properties, "all.delete_on_exit", false);
      if (delete) {
         Path path = Paths.get(ClientShared.DIR, this.course, this.activity, "archives");
         this.manageFilesCommon(path, "Could not remove archives: ");
      }

   }

   private void manageToolFiles() {
      boolean delete = Utils.getBooleanOrDefault(this.properties, "tools.delete_on_exit", false) || Utils.getBooleanOrDefault(this.properties, "all.delete_on_exit", false);
      if (delete) {
         Path path = Paths.get(ClientShared.DIR, this.course, this.activity, "tools");
         this.manageFilesCommon(path, "Could not remove tools: ");
      }

   }

   private void manageSessionFiles() {
      boolean delete = Utils.getBooleanOrDefault(this.properties, "session.delete_on_exit", false);
      if (delete) {
         Path path = Paths.get(ClientShared.DIR, this.course, this.activity);
         this.manageFilesCommon(path, "Could not remove session: ");
      }

   }

   private void manageCourseFiles() {
      boolean delete = Utils.getBooleanOrDefault(this.properties, "course.delete_on_exit", false);
      if (delete) {
         Path path = Paths.get(ClientShared.DIR, this.course);
         this.manageFilesCommon(path, "Could not remove course: ");
      }

   }

   private synchronized void manageFilesCommon(Path path, String exceptionLogPrefix) {
      File file = path.toFile();
      if (file.exists()) {
         try {
            DirectoryUtils.destroyDirectory(file.getCanonicalPath());
         } catch (Exception e) {
            Logger.log(Level.NOTED, exceptionLogPrefix, e);
         }

         try {
            DirectoryUtils.destroyDirectoryOnExit(file.getCanonicalPath());
         } catch (Exception var5) {
         }
      }

   }

   private void manageUnsanctionedFilesCreated() {
      boolean delete = Utils.getBooleanOrDefault(this.properties, "unsanctioned.delete_on_exit", false);
      String key = this.properties.getProperty("session.key", "").trim();
      boolean encrypt = Utils.getBooleanOrDefault(this.properties, "unsanctioned.encrypt_on_exit", false) && key.length() > 0 && !delete;
      if ((delete || encrypt) && this.fileSystemMonitor != null) {
         String[] unsanctionedFiles = this.fileSystemMonitor.unsanctionedFiles();
         if (unsanctionedFiles != null && unsanctionedFiles.length > 0) {
            StringBuffer sb = new StringBuffer("The following unsanctioned files were created:\n");

            for(String uf : unsanctionedFiles) {
               File f = new File(uf);
               if (f.exists()) {
                  if (delete) {
                     f.delete();
                  } else if (encrypt) {
                     File ef = new File(f.getParent(), "encrypted-" + f.getName());

                     try {
                        StreamCryptoUtils.streamEncrypt(key, f.getAbsolutePath(), ef.getAbsolutePath());
                        f.delete();
                     } catch (GeneralSecurityException var13) {
                     } catch (IOException var14) {
                     }
                  }

                  sb.append(uf);
                  sb.append("\n");
               }
            }

            if (delete) {
               sb.append("\nThe files were deleted");
            }

            if (encrypt) {
               sb.append("\nThe files were encrypted. Contact instructor for access");
            }

            this.logArchiver.put(Level.LOGGED, sb.toString());
         }
      }

   }

   private synchronized void manageOSToolFiles() {
      if (ClientShared.isWindowsOS()) {
         File f = new File(ClientShared.DOWNLOADS_DIR, "Eula.txt");
         if (f.exists()) {
            f.delete();
         }

         f = new File(ClientShared.DOWNLOADS_DIR, "handle.exe");
         if (f.exists()) {
            this.hardwareAndSoftwareMonitor.terminateRunningProcess("handle64");
            f.delete();
         }

         if (ClientShared.PROCESS_MONITORING) {
            f = new File(ClientShared.DOWNLOADS_DIR, "pskill.exe");
            if (f.exists()) {
               this.hardwareAndSoftwareMonitor.terminateRunningProcess("pskill64");
               f.delete();
            }

            f = new File(ClientShared.DOWNLOADS_DIR, "pssuspend.exe");
            if (f.exists()) {
               this.hardwareAndSoftwareMonitor.terminateRunningProcess("pssuspend64");
               f.delete();
            }
         }
      }

   }

   private synchronized void manageClipboard() {
      if (Utils.getBooleanOrDefault(this.properties, "clipboard.delete_on_exit", true) || Utils.getBooleanOrDefault(this.properties, "all.delete_on_exit", false) || Utils.getBooleanOrDefault(this.properties, "course.delete_on_exit", false) || Utils.getBooleanOrDefault(this.properties, "session.delete_on_exit", false)) {
         ClipboardManager.setContents("Emptied by CoMaS");
      }

      boolean blocked = PropertyValue.getValue(this, "session", "clipboard.block", false);
      if (blocked) {
         ClipboardManager.unblock();
      }

   }

   public void uncaughtException(Thread t, Throwable e) {
      if (t == this.thread) {
         String var10000 = t.getName();
         String msg = "Restarted \"" + var10000 + "\". Cause: " + String.valueOf(e) + "\n";
         this.logArchiver.put(Level.DIAGNOSTIC, msg + Utils.printException(e));
         if (!this.isEndedSession()) {
            Sleeper.sleep(1000);
            this.runExam();
         }
      } else {
         LogArchiver var4 = this.logArchiver;
         java.util.logging.Level var10001 = Level.DIAGNOSTIC;
         String var10002 = t.getName();
         var4.put(var10001, "Abnormal \"" + var10002 + "\" termination. Cause:\n" + Utils.printFirstApplicationStackFrameOrException(e));
         if (t.getName().equals("session end")) {
            System.exit(-1);
         }
      }

   }

   public void updateProgressServlet(int progress, String message) {
      ProgressServlet ps = ProgressServlet.getSingleton();
      if (ps != null) {
         ps.setProgress(progress);
         ps.setProgressMessage(message);
      }

   }

   public String getActualState() {
      return this.state;
   }

   public void setActualState(String state) {
      this.state = state;
   }

   public void setState(String _state) {
      if (_state != null) {
         this.reportedState = _state;
         if (this.reportedState.equals("Terminated")) {
            this.setInvigilatorState(InvigilatorState.ending);
         }
      }

   }

   public String toString() {
      return String.format("%s %s %s %s \"%s\" %s", this.getToken(), this.getInvigilatorState(), this.getActualState(), this.getSessionContext(), this.getName(), this.getID());
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$java$util$concurrent$TimeUnit() {
      int[] var10000 = $SWITCH_TABLE$java$util$concurrent$TimeUnit;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[TimeUnit.values().length];

         try {
            var0[TimeUnit.DAYS.ordinal()] = 7;
         } catch (NoSuchFieldError var7) {
         }

         try {
            var0[TimeUnit.HOURS.ordinal()] = 6;
         } catch (NoSuchFieldError var6) {
         }

         try {
            var0[TimeUnit.MICROSECONDS.ordinal()] = 2;
         } catch (NoSuchFieldError var5) {
         }

         try {
            var0[TimeUnit.MILLISECONDS.ordinal()] = 3;
         } catch (NoSuchFieldError var4) {
         }

         try {
            var0[TimeUnit.MINUTES.ordinal()] = 5;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[TimeUnit.NANOSECONDS.ordinal()] = 1;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[TimeUnit.SECONDS.ordinal()] = 4;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$java$util$concurrent$TimeUnit = var0;
         return var0;
      }
   }

   private class Worker implements Runnable {
      public void run() {
         boolean wantToContinue = true;

         try {
            Invigilator.this.init();
         } catch (Exception e) {
            String msg = "Session monitoring initialization exception: " + Utils.printFirstApplicationStackFrameOrException(e);
            Invigilator.this.logArchiver.put(Level.DIAGNOSTIC, msg);
         }

         while(wantToContinue) {
            try {
               Invigilator.this.monitorUser();
               wantToContinue = false;
               Logger.log(java.util.logging.Level.INFO, "", "Session monitoring ended normally (code 0)");
            } catch (Exception var5) {
               wantToContinue = !Invigilator.this.isDone() && Invigilator.this.failures < ClientShared.MAX_SESSION_FAILURES && !Invigilator.this.isEndedSession();
               if (wantToContinue) {
                  String msg = "Session monitoring restarted: " + Utils.printFirstApplicationStackFrameOrException(var5);
                  Invigilator.this.logArchiver.put(Level.DIAGNOSTIC, msg);
               }

               if (Invigilator.this.failures >= ClientShared.MAX_SESSION_FAILURES) {
                  Invigilator.this.logArchiver.put(Level.INFO, "Session monitoring ended (code 1): " + String.valueOf(var5));
               }

               if (Invigilator.this.isDone()) {
                  Invigilator.this.logArchiver.put(Level.INFO, "Session monitoring ended (code 2): " + String.valueOf(var5));
               }
            }
         }

      }
   }
}
