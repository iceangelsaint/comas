package edu.carleton.cas.jetty.embedded;

import com.cogerent.utility.PropertyValue;
import edu.carleton.cas.background.timers.TimerService;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.resources.Configurable;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.ui.WebAlert;
import edu.carleton.cas.utility.Mimetypes;
import edu.carleton.cas.utility.PatternConstants;
import edu.carleton.cas.utility.Sleeper;
import java.awt.Desktop;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletMapping;

public class ServletProcessor implements Runnable, Thread.UncaughtExceptionHandler, Configurable {
   private static final int DEFAULT_PORT = 0;
   private static final int MINIMUM_PORT = 0;
   private static final int MAXIMUM_PORT = 65535;
   private static final long SHUTDOWN_TIME_IN_MSECS = 5000L;
   private static final String DEFAULT_HOST = "http://localhost";
   private static int PING_TIMEOUT_IN_MSECS = 2000;
   private static final FileFilter TOOLS_FILTER = new FileFilter() {
      public boolean accept(File f) {
         return !f.isHidden() && f.canRead() && !f.getName().equals("logs.html") && (f.getName().endsWith(".htm") || f.getName().endsWith(".html"));
      }
   };
   private static final FileFilter RESOURCES_FILTER = new FileFilter() {
      public boolean accept(File f) {
         return !f.isHidden() && f.canRead();
      }
   };
   private static ServerSocket LOCK_SOCKET;
   private String ERROR_MESSAGE = "<html><head>%s</head><body><br/><div class=\"w3-container w3-center\">%s<h1>Sorry, the request %s returned %d</h1></div></body></html>";
   private String WINDOW_OPTIONS = "scrollbars=yes,resizable=yes,top=500,left=500,width=400,height=400";
   private boolean WINDOW_CHOICE = true;
   private String COLOUR_BACKGROUND_ON_EXIT = "white";
   private String TITLE_COLOUR_BACKGROUND_ON_EXIT = "white";
   private String USEFUL_TOKEN = "";
   private int SERVLET_REFRESH_IN_MSECS = 30000;
   private int SERVLET_ALERT_TIMEOUT_IN_MSECS = 5000;
   private boolean SERVLET_VARIABLE_REFRESH_SUPPORTED = false;
   private int AUTOSAVE_FREQUENCY_IN_MSECS = 300000;
   private final HashMap links;
   private final HashMap handlers;
   private int port;
   private String host;
   private Thread thread;
   private String service;
   private Server server;
   private ServletProcessorClassLoader classLoader;
   private final AtomicBoolean stopped;
   private final Invigilator invigilator;
   private final ServletRouter router;
   private final CountDownLatch readyForService;
   private final AtomicLong lastAccessTime;

   private ServletProcessor(Invigilator invigilator) {
      this.invigilator = invigilator;
      this.router = new ServletRouter(this.invigilator);
      this.port = Utils.getIntegerOrDefaultInRange(invigilator.getProperties(), "web_server.port", 0, 0, 65535);
      this.host = Utils.getStringOrDefault(invigilator.getProperties(), "web_server.host", "http://localhost");
      this.setupServiceValue();
      this.classLoader = null;
      this.stopped = new AtomicBoolean(false);
      this.readyForService = new CountDownLatch(1);
      this.lastAccessTime = new AtomicLong(System.currentTimeMillis());
      this.links = new HashMap();
      this.handlers = new HashMap();
   }

   public Invigilator getInvigilator() {
      return this.invigilator;
   }

   public static ServletProcessor create(Invigilator invigilator) {
      Logger.log(Level.INFO, "Web Server (" + Server.getVersion() + "): ", "Starting");
      ServletProcessor sp = new ServletProcessor(invigilator);
      int lock_port = Utils.getIntegerOrDefaultInRange(invigilator.getProperties(), "session.store.web_server." + String.valueOf(ClientShared.getOS()) + ".lock", -1, 44000, 55000);
      if (lock_port == -1) {
         lock_port = Utils.getIntegerOrDefaultInRange(invigilator.getProperties(), "session.store.web_server.lock", -1, 44000, 55000);
      }

      if (lock_port > 0) {
         try {
            LOCK_SOCKET = new ServerSocket(lock_port);
            LOCK_SOCKET.setReuseAddress(true);
         } catch (IOException var6) {
            return null;
         }
      }

      int tries = 0;
      int max_creation_tries = Utils.getIntegerOrDefaultInRange(invigilator.getProperties(), "session.store.web_server.attempts", 10, 1, 50);

      while(sp.port != 0 && tries < max_creation_tries) {
         ++tries;
         if (sp.isPortOpen()) {
            String response = Utils.getURL(sp.getService("ping"), 1000);
            if (response.length() == 0) {
               ++sp.port;
               sp.setupServiceValue();
               invigilator.setProperty("web_server.port", "" + sp.port);
            }
         }
      }

      sp.start();
      return sp;
   }

   private void setupServiceValue() {
      this.service = this.host + ":" + this.port + "/";
   }

   public boolean isPortOpen() {
      return Utils.isPortOpen("localhost", this.port);
   }

   public long getTimeSinceLastAccessInMillis() {
      return System.currentTimeMillis() - this.lastAccessTime.get();
   }

   public ServletRouter getRouter() {
      return this.router;
   }

   public int getPort() {
      return this.port;
   }

   public String getHost() {
      return this.host;
   }

   public String getService() {
      return this.service;
   }

   public String getService(String url) {
      if (url.startsWith("/")) {
         String var10000 = this.service;
         return var10000 + url.substring(1);
      } else {
         return this.service + url;
      }
   }

   public long updateLastAccessTimeRegardlessOfState() {
      long time = System.currentTimeMillis();
      this.lastAccessTime.set(time);
      return time;
   }

   public long updateLastAccessTime() {
      if (this.invigilator.getInvigilatorState() == InvigilatorState.loggingIn) {
         return this.lastAccessTime.get();
      } else {
         long time = System.currentTimeMillis();
         this.lastAccessTime.set(time);
         return time;
      }
   }

   public String getServletButton(String name, String label, String colour, char accessKey) {
      return "<button accesskey=\"" + accessKey + "\" class=\"w3-button " + colour + " w3-round-large\" onclick='goTo(\"" + this.getService(name) + "\",\"" + label + "\")'>" + label + "</button>";
   }

   public String getMailButton() {
      return this.getMailButton((String)null, (String)null);
   }

   public String getMailButtonSeparatorBefore() {
      return this.getMailButton("&nbsp;&nbsp;&nbsp;&nbsp;", (String)null);
   }

   public String getMailButtonSeparatorAfter() {
      return this.getMailButton((String)null, "&nbsp;&nbsp;&nbsp;&nbsp;");
   }

   public String getMailButton(String separatorBefore, String separatorAfter) {
      String to = PropertyValue.getValue(this.invigilator, "mail", "to", "").trim();
      if (to.length() != 0 && to.contains("@")) {
         if (!PatternConstants.emailPattern.matcher(to).matches()) {
            return "";
         } else {
            StringBuffer sb = new StringBuffer();
            if (separatorBefore != null) {
               sb.append(separatorBefore);
            }

            sb.append("<a accesskey=\"m\" class=\"w3-button w3-blue w3-round-large\" href=\"mailto:");
            sb.append(to);
            String var10000 = this.invigilator.getSessionContext();
            String defaultSubject = var10000 + "%20" + this.invigilator.getNameAndID();
            String subject = PropertyValue.getValue(this.invigilator, "mail", "subject", defaultSubject);
            sb.append("?subject=");
            sb.append(this.invigilator.resolveVariablesInMessage(subject).replace("\t", "%09").replace(" ", "%20").replace("\n", "%0D%0A"));
            var10000 = this.invigilator.getSessionContext();
            String defaultBody = var10000 + "%20" + this.invigilator.getNameAndID();
            String body = PropertyValue.getValue(this.invigilator, "mail", "body", defaultBody);
            sb.append("&body=");
            sb.append(this.invigilator.resolveVariablesInMessage(body).replace("\t", "%09").replace(" ", "%20").replace("\n", "%0D%0A"));
            String cc = PropertyValue.getValue(this.invigilator, "mail", "cc");
            if (cc != null) {
               sb.append("&cc=");
               sb.append(cc.trim().replace(" ", "%20"));
            }

            String bcc = PropertyValue.getValue(this.invigilator, "mail", "bcc");
            if (bcc != null) {
               sb.append("&bcc=");
               sb.append(bcc.trim().replace(" ", "%20"));
            }

            sb.append("\">Mail</a>");
            if (separatorAfter != null) {
               sb.append(separatorAfter);
            }

            return sb.toString();
         }
      } else {
         return "";
      }
   }

   public String getServletButton(String name, String label, String colour) {
      return this.getServletButton(name, label, colour, Character.toLowerCase(label.charAt(0)));
   }

   public String getServletButton(String name, String label, char accessKey) {
      return this.getServletButton(name, label, "w3-blue", accessKey);
   }

   public String getServletButton(String name, String label) {
      return this.getServletButton(name, label, "w3-blue");
   }

   public String getMapping() {
      return this.invigilator.getSessionContext();
   }

   private void start() {
      this.thread = new Thread(this);
      this.thread.setName("web server");
      this.thread.setUncaughtExceptionHandler(this);
      this.thread.start();
   }

   public void setupExtensions() {
      if (this.server.getHandler() instanceof ServletHandler) {
         this.processHandlerExtensions((ServletHandler)this.server.getHandler());
      }

   }

   public void run() {
      try {
         this.server = new Server();
         ServerConnector connector = new ServerConnector(this.server);
         connector.setPort(this.port);
         this.server.addConnector(connector);
         ServletHandler handler = new ServletHandler();
         this.server.setHandler(handler);
         this.addServlet(new ServerCheckServlet(), "/server");
         this.router.addRule("/server", InvigilatorState.running);
         this.addServlet(new LogServlet(), "/log");
         this.router.addRule("/log", InvigilatorState.running);
         this.addServlet(new StatisticsServlet(), "/statistics");
         this.router.addRule("/statistics", InvigilatorState.running);
         this.addServlet(new ApplicationResourceServlet("text/html", true), "/pages/*");
         this.addServlet(new ApplicationResourceServlet("image/png", false), "/images/*");
         this.addServlet(new FolderServlet("tools", TOOLS_FILTER), "/tools/*");
         this.addServlet(new FolderServlet("resources", RESOURCES_FILTER), "/resources/*");
         ExamQuestionServlet eqs = new ExamQuestionServlet(this.invigilator);
         eqs.addServletHandler();
         ErrorHandler eh = new ErrorHandler(this.ERROR_MESSAGE);
         eh.setShowStacks(false);
         this.server.setErrorHandler(eh);
         this.server.setStopAtShutdown(true);
         this.server.setStopTimeout(5000L);
         this.server.start();
      } catch (Exception var6) {
         try {
            Desktop.getDesktop().browse(new URI(this.getService()));
         } catch (URISyntaxException | IOException var5) {
         }

         WebAlert.exitAfterAlert("Web Server failed to start. CoMaS may be running.\nBrowser will open with last session context.\n\nPlease click on a Quit button to end a CoMaS session", -1);
      }

   }

   public boolean updateService() {
      Connector[] connectors = this.server.getConnectors();
      if (this.port == 0 && connectors.length > 0) {
         ServerConnector sc = (ServerConnector)connectors[0];
         this.port = sc.getLocalPort();
         this.setupServiceValue();
         return true;
      } else {
         return false;
      }
   }

   public boolean isRunning() {
      boolean rtn = this.server != null && this.server.isRunning();
      if (rtn) {
         this.updateService();
      }

      return rtn;
   }

   public boolean isStarted() {
      return this.server != null && this.server.isStarted();
   }

   public boolean isStopped() {
      return this.server != null && this.server.isStopped();
   }

   public void readyForService() {
      this.readyForService.countDown();
   }

   public void waitForService() {
      boolean var5 = false;

      label67: {
         try {
            var5 = true;
            this.readyForService.await();
            var5 = false;
            break label67;
         } catch (InterruptedException var6) {
            var5 = false;
         } finally {
            if (var5) {
               if (this.port != 0) {
                  this.invigilator.logArchiver.put(edu.carleton.cas.logging.Level.LOGGED, "Using non-standard web server port (" + this.port + ")");
               }

            }
         }

         if (this.port != 0) {
            this.invigilator.logArchiver.put(edu.carleton.cas.logging.Level.LOGGED, "Using non-standard web server port (" + this.port + ")");
         }

         return;
      }

      if (this.port != 0) {
         this.invigilator.logArchiver.put(edu.carleton.cas.logging.Level.LOGGED, "Using non-standard web server port (" + this.port + ")");
      }

   }

   public Servlet getServlet(String mapping) {
      try {
         ServletHandler handler = (ServletHandler)this.server.getHandler();
         ServletHolder holder = handler.getServlet(mapping);
         if (holder != null) {
            return holder.getServlet();
         }
      } catch (ServletException var4) {
      }

      return null;
   }

   public ServletMapping getServletMapping(String mapping) {
      ServletHandler sh = (ServletHandler)this.server.getHandler();
      return sh.getServletMapping(mapping);
   }

   public ServletHolder addServlet(HttpServlet servlet, String mapping) {
      try {
         ServletHandler handler = (ServletHandler)this.server.getHandler();
         ServletHolder sh = new ServletHolder(servlet);
         handler.addServletWithMapping(sh, mapping);
         return sh;
      } catch (Exception var5) {
         Logger.log(Level.WARNING, "Unable to add ", mapping);
         return null;
      }
   }

   public String endOfSessionDialog(boolean canClose) {
      StringBuffer sb = new StringBuffer();
      sb.append("<dialog style=\"width:20%\" id=\"alertDialog\">");
      sb.append("<button class=\"close\" onclick='alertDialog.close();'>x</button>");
      sb.append("<div id=\"msg\">Your session has ended</div>");
      if (canClose) {
         sb.append("<div><button class=\"w3-button w3-round-large w3-blue\" onclick='window.close();'>Close</button></div>");
      }

      sb.append("<style> .close {\n\tcolor: #aaa;\n\tfloat: right;\n\tfont-size: 16px;\n margin: 5px;\n\t//font-weight: bold;\n\t}\n\t.close:hover,\n\t.close:focus {\n\tcolor: black;\n\ttext-decoration: none;\n\tcursor: pointer;\n\t}</style>");
      sb.append("</dialog>");
      return sb.toString();
   }

   public String checkForServletCode() {
      return this.checkForServletCode(this.SERVLET_REFRESH_IN_MSECS, this.WINDOW_CHOICE, this.WINDOW_OPTIONS, "");
   }

   public String checkForServletCode(String options) {
      return this.checkForServletCode(this.SERVLET_REFRESH_IN_MSECS, options != null, options, "");
   }

   public String checkForServletCode(int millis, boolean canClose, String options, String codeOnExit) {
      StringBuffer sb = new StringBuffer();
      sb.append(this.endOfSessionDialog(canClose));
      sb.append("<script>\n");
      sb.append("var timeout=");
      sb.append(millis);
      sb.append(";\n");
      sb.append("var autoRefreshTimer = null;\n");
      sb.append("var okay = true;\n");
      sb.append("function openWindow(url, page) {\n");
      sb.append("   if (okay) {\n");
      sb.append("      var handle = window.open(url);\n");
      sb.append("      if (handle === null) {\n");
      sb.append("         alertWithTimeout(\"The \" + page + \" page is unavailable because your session has ended\", ");
      sb.append(this.SERVLET_ALERT_TIMEOUT_IN_MSECS);
      sb.append(");\n");
      sb.append("      }\n");
      sb.append("   } else {\n      alertWithTimeout(\"The \" + page + \" page is unavailable because your session has ended\", ");
      sb.append(this.SERVLET_ALERT_TIMEOUT_IN_MSECS);
      sb.append(");\n   }\n}\n");
      sb.append("function goTo(url, page) {\n");
      sb.append("   if (okay) {\n");
      if (canClose) {
         sb.append("     var handle = window.open(url, \"_blank\", \"");
         sb.append(options);
         sb.append("\");\n");
         sb.append("      if (handle === null) {\n");
         sb.append("         alertWithTimeout(\"The \" + page + \" page is unavailable because your session has ended\", ");
         sb.append(this.SERVLET_ALERT_TIMEOUT_IN_MSECS);
         sb.append(");\n");
         sb.append("      }\n");
      } else {
         sb.append("      window.location.assign(url);\n");
      }

      sb.append("   } else {\n      alertWithTimeout(\"The \" + page + \" page is unavailable because your session has ended\", ");
      sb.append(this.SERVLET_ALERT_TIMEOUT_IN_MSECS);
      sb.append(");\n   }\n}\n");
      sb.append("function check() {\n   if (timeout > 0) {\n      autoRefreshTimer = setTimeout(check, timeout);\n   }\n   const err = new XMLHttpRequest();\n   var erf = function() {\n      okay = false;\n" + codeOnExit + "      let title = document.getElementById(\"reportTitle\");\n      if (title !== null) {\n         title.style.backgroundColor = \"" + this.TITLE_COLOUR_BACKGROUND_ON_EXIT + "\";\n      }\n      document.title = \"CoMaS Session Ended\";\n      document.body.style.backgroundColor = \"");
      sb.append(this.COLOUR_BACKGROUND_ON_EXIT);
      sb.append("\";\n      alertWithTimeout(\"Your session has now ended\", ");
      sb.append(this.SERVLET_ALERT_TIMEOUT_IN_MSECS);
      sb.append(");\n   }\n   err.onerror = erf;\n   err.onabort = erf;\n   err.ontimeout = erf;\n   err.onreadystatechange = function() {\n      if (this.readyState == XMLHttpRequest.DONE && this.status == 200) {\n         okay = true;\n         location.reload();\n      }\n   }\n   err.timeout = 2000;   err.open(\"GET\",\"");
      sb.append(this.getService());
      sb.append("server\");\n   err.send();\n}\n");
      sb.append(SystemWebResources.getAlertWithTimeout());
      sb.append("function setCookieValue(name, value, exDays) {\n   var d = new Date();\n   d.setTime(d.getTime() + (exDays*24*60*60*1000));\n   var expires = \"expires=\"+ d.toUTCString();\n   document.cookie = name + \"=\" + value + \";\" + expires + \";path=/\";\n}\nfunction autosave() {\n   save();\n   timerId = setTimeout(autosave, " + this.AUTOSAVE_FREQUENCY_IN_MSECS + ");\n}\nfunction cancelAutosave() {   \n   if (timerId) {\n       clearTimeout(timerId); \n       timerId = 0; \n   }\n}\nfunction getCookieValue(name) {\n   var nameEQ = name + \"=\";\n   var ca = document.cookie.split(';');\n   for(var i=0;i < ca.length;i++) {\n       var c = ca[i];\n       while (c.charAt(0)==' ') c = c.substring(1,c.length);\n       if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length,c.length);\n   }\n   return null;\n}\nfunction eraseCookie(name) {\n   document.cookie = name+'=; Max-Age=-99999999;';\n}\n");
      if (millis > 0) {
         sb.append("autoRefreshTimer = setTimeout(check, timeout);\n");
      }

      sb.append("</script>\n");
      return sb.toString();
   }

   public String refreshScript() {
      return this.refreshScript(this.SERVLET_REFRESH_IN_MSECS / 100);
   }

   public String refreshScript(int timeout) {
      return this.SERVLET_VARIABLE_REFRESH_SUPPORTED ? "<script>\nfunction autoRefresh() {\n   var value = document.getElementById(\"autorefresh-timeout-range\").value;\n   timeout = document.getElementById(\"autorefresh-timeout-range\").value * 1000;\n   document.getElementById(\"autorefresh-value\").innerHTML = value+\"/120\";\n   if (autoRefreshTimer !== null) {\n      clearTimeout(autoRefreshTimer);\n   }\n   if (value>0) {\n      autoRefreshTimer = setTimeout(check, timeout);\n   } else {\n      autoRefreshTimer = null;\n   }\n}\nfunction refreshChanged() {\n   var value = document.getElementById(\"autorefresh-timeout-range\").value;\n   setCookieValue(\"autoRefresh\", value, 30);\n   autoRefresh();\n}\nfunction initializeRefresh() {\n   let cookieValue = getCookieValue(\"autoRefresh\");\n   if (cookieValue === null) {\n      document.getElementById(\"autorefresh-timeout-range\").value=" + timeout + ";\n   } else {\n      document.getElementById(\"autorefresh-timeout-range\").value=cookieValue;\n   }\n   timeout = document.getElementById(\"autorefresh-timeout-range\").value*1000;\n}\ninitializeRefresh();\nautoRefresh();\n</script>\n" : "";
   }

   public String refreshField() {
      return this.refreshField(this.SERVLET_REFRESH_IN_MSECS / 1000);
   }

   public String refreshField(int timeout) {
      if (!this.SERVLET_VARIABLE_REFRESH_SUPPORTED) {
         return "";
      } else {
         if (timeout < 0 || timeout > 120) {
            timeout = this.SERVLET_REFRESH_IN_MSECS / 1000;
         }

         return "<div class=\"w3-panel w3-center\"><span style=\"width:20%;margin:auto;align-items:center;\">0&nbsp;<input type=\"range\" id=\"autorefresh-timeout-range\" onChange='refreshChanged()' value=\"" + timeout + "\" max=\"120\" min=\"0\" step=\"10\">&nbsp;<span id=\"autorefresh-value\"></span></span></div>";
      }
   }

   public String refreshForServlet() {
      return this.SERVLET_VARIABLE_REFRESH_SUPPORTED ? this.refreshForServlet(this.SERVLET_REFRESH_IN_MSECS / 1000) : "";
   }

   public String refreshForServlet(int timeout) {
      if (this.SERVLET_VARIABLE_REFRESH_SUPPORTED) {
         String var10000 = this.refreshField(timeout);
         return var10000 + this.refreshScript(timeout);
      } else {
         return "";
      }
   }

   public void setupCloseScenarioDetection() {
      long timeout = (long)(Utils.getIntegerOrDefaultInRange(this.invigilator.getProperties(), "session.browser_inactivity", 120, 0, 300) * 1000);
      if (timeout > 0L) {
         TimerService.scheduleAtFixedRate(new ClosedBrowserScenarioTimerTask(this.invigilator), timeout, timeout);
      }

   }

   public String pingForServlet() {
      return this.pingForServlet(Math.max(ClientShared.THIRTY_SECONDS_IN_MSECS, this.SERVLET_REFRESH_IN_MSECS));
   }

   public String pingForServlet(boolean closeOnFail) {
      return this.pingForServlet(Math.max(ClientShared.THIRTY_SECONDS_IN_MSECS, this.SERVLET_REFRESH_IN_MSECS), closeOnFail);
   }

   public String pingForServlet(int timeout) {
      return this.pingForServlet(timeout, false);
   }

   public String pingForServlet(int timeout, boolean closeOnFail) {
      StringBuffer sb = new StringBuffer(435);
      sb.append("   var pingTimer;\n");
      sb.append("   var websocket_host = \"");
      sb.append(ClientShared.WEBSOCKET_HOST.trim());
      sb.append("\";\n");
      sb.append("   var websocket_port = \"");
      sb.append(ClientShared.PORT.trim());
      sb.append("\";\n");
      sb.append("   function ping() {\n");
      sb.append("      var xhttp = new XMLHttpRequest();\n");
      sb.append("      var erf = function() {\n");
      sb.append("         clearTimeout(pingTimer);\n");
      sb.append("         if (typeof check !== 'undefined') {\n");
      if (closeOnFail) {
         sb.append("            document.documentElement.innerHTML='");
         String footer = this.footerForServlet(false, true, (String)null);
         String page = "<h2>Your session has now ended</h2>" + footer;
         sb.append(SystemWebResources.htmlPage("CoMaS Session Ended", page));
         sb.append("';\n");
         sb.append("            setTimeout(function () { window.close() }, 5000);\n");
      } else {
         sb.append("            check();\n");
      }

      sb.append("         } else {\n");
      sb.append("            alertWithTimeout(\"Your session has now ended\", 5000);\n");
      if (closeOnFail) {
         sb.append("            setTimeout(function () { window.close() }, 5000);\n");
      }

      sb.append("         }\n");
      sb.append("      };\n");
      sb.append("      xhttp.onerror = erf;\n");
      sb.append("      xhttp.onabort = erf;\n");
      sb.append("      xhttp.ontimeout = erf;\n");
      sb.append("      xhttp.onreadystatechange = function() {\n");
      sb.append("         if (this.readyState === 4 && this.status === 200) {\n");
      sb.append("            var tokens = this.response.split(\":\");\n");
      sb.append("            websocket_host = tokens[0].trim();\n");
      sb.append("            websocket_port = tokens[1].trim();\n");
      sb.append("         } else if (this.readyState === 4 && this.status === 404) {\n");
      sb.append("            alertWithTimeout(\"Your session has now ended\", 5000 );\n");
      if (closeOnFail) {
         sb.append("            window.location.href='/end';\n");
         sb.append("            setTimeout(function () { window.close() }, 5000);\n");
      }

      sb.append("         }\n");
      sb.append("      };\n");
      sb.append("      xhttp.timeout=");
      sb.append(PING_TIMEOUT_IN_MSECS);
      sb.append(";\n");
      sb.append("      xhttp.open(\"GET\", \"");
      sb.append(this.getService());
      sb.append("ping\");\n");
      sb.append("      xhttp.send();\n");
      sb.append("   }\n");
      sb.append("   pingTimer = setInterval(ping, ");
      sb.append(timeout);
      sb.append(");\n");
      return sb.toString();
   }

   public String footerForServlet(boolean quitButtonIncluded, boolean textIsCentered) {
      return this.footerForServlet(quitButtonIncluded, textIsCentered, (String)null);
   }

   public String footerForServlet(boolean quitButtonIncluded, boolean textIsCentered, String extra) {
      StringBuffer sb = new StringBuffer();
      if (textIsCentered) {
         sb.append("<div style=\"text-align:center\">");
      }

      if (extra != null) {
         sb.append("<span>");
         sb.append(extra);
         if (quitButtonIncluded) {
            sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
            sb.append(QuitServlet.getQuitButton());
            sb.append("<br/><br/>");
         }

         sb.append("</span>");
      }

      sb.append("<h5>");
      sb.append("Generated: ");
      sb.append((new Date()).toString());
      sb.append(" using v");
      sb.append("0.8.75");
      sb.append("</h5>");
      if (quitButtonIncluded && extra == null) {
         sb.append(QuitServlet.getQuitButton());
         sb.append("<br/><br/>");
      }

      if (textIsCentered) {
         sb.append("</div>");
      }

      return sb.toString();
   }

   public String footerForServlet() {
      return this.footerForServlet(false, false);
   }

   public String[] availableServlets() {
      return new String[]{"/pages/Chat.html"};
   }

   public boolean hasResources() {
      String resource = ClientShared.getBaseDirectory(this.invigilator.getCourse(), this.invigilator.getActivity()) + "resources";
      File f = new File(resource);
      if (f.exists() && f.isDirectory() && f.canRead()) {
         File[] files = f.listFiles(RESOURCES_FILTER);
         return files.length > 0;
      } else {
         return false;
      }
   }

   public boolean hasTools() {
      String resource = ClientShared.getBaseDirectory(this.invigilator.getCourse(), this.invigilator.getActivity()) + "tools";
      File f = new File(resource);
      if (f.exists() && f.isDirectory() && f.canRead()) {
         File[] files = f.listFiles(TOOLS_FILTER);
         return this.links.size() > 0 || this.handlers.size() > 0 || files.length > 0;
      } else {
         return this.links.size() > 0 || this.handlers.size() > 0;
      }
   }

   public void stop() {
      if (this.stopped.compareAndSet(false, true)) {
         if (!this.invigilator.isInInvigilatorState(InvigilatorState.ended)) {
            Sleeper.sleep(1000);
         }

         try {
            if (this.server != null && this.server.isStarted()) {
               this.server.stop();
            }
         } catch (Exception var2) {
         }
      }

   }

   private void processHandlerExtensions(ServletHandler handler) {
      this.handlers.forEach((key, value) -> {
         try {
            Class<?> clazz;
            try {
               clazz = Class.forName(key);
            } catch (ClassNotFoundException var6) {
               if (this.classLoader != null) {
                  clazz = Class.forName(key, true, this.classLoader);
               } else {
                  clazz = null;
               }
            }

            if (clazz != null) {
               if (!value.startsWith("/")) {
                  value = "/" + value;
               }

               handler.addServletWithMapping(new ServletHolder(clazz), value);
            }
         } catch (Exception e) {
            Logger.log(edu.carleton.cas.logging.Level.DIAGNOSTIC, "Web Server (" + Server.getVersion() + "): ", e);
         }

      });
   }

   private ServletProcessorClassLoader getServletClassLoader(Properties properties) {
      String classLoaderURL = properties.getProperty("web_server.load.url");
      ServletProcessorClassLoader cl = null;
      if (classLoaderURL != null) {
         try {
            classLoaderURL = classLoaderURL.trim();
            if (classLoaderURL.startsWith("/")) {
               classLoaderURL = ClientShared.service(ClientShared.PROTOCOL, ClientShared.CMS_HOST, ClientShared.PORT, classLoaderURL);
            }

            URL url = new URL(classLoaderURL);
            cl = new ServletProcessorClassLoader(url, this.USEFUL_TOKEN);
         } catch (MalformedURLException e) {
            cl = null;
            Logger.log(edu.carleton.cas.logging.Level.DIAGNOSTIC, "Web Server (" + Server.getVersion() + "): ", e.getMessage());
         }
      }

      return cl;
   }

   public void uncaughtException(Thread t, Throwable e) {
      Logger.log(edu.carleton.cas.logging.Level.DIAGNOSTIC, "Web Server (" + Server.getVersion() + ") abnormal " + t.getName() + " termination. Cause: ", e);
   }

   public void configure() {
      this.configure(this.invigilator.getProperties());
   }

   public void configure(Properties properties) {
      PING_TIMEOUT_IN_MSECS = Utils.getIntegerOrDefaultInRange(properties, "web_server.timeout", 2000, 0, 60000);
      this.COLOUR_BACKGROUND_ON_EXIT = Utils.getStringOrDefault(properties, "web_server.window_exit_colour", this.COLOUR_BACKGROUND_ON_EXIT);
      this.TITLE_COLOUR_BACKGROUND_ON_EXIT = Utils.getStringOrDefault(properties, "web_server.title_exit_colour", this.COLOUR_BACKGROUND_ON_EXIT);
      this.WINDOW_OPTIONS = Utils.getStringOrDefault(properties, "web_server.window_options", this.WINDOW_OPTIONS);
      this.WINDOW_CHOICE = Utils.getBooleanOrDefault(properties, "web_server.window_open", false);
      this.SERVLET_REFRESH_IN_MSECS = Utils.getIntegerOrDefaultInRange(properties, "web_server.page_refresh", 30, 0, 120) * 1000;
      this.SERVLET_ALERT_TIMEOUT_IN_MSECS = Utils.getIntegerOrDefaultInRange(properties, "web_server.alert_timeout", 5, 1, 300) * 1000;
      this.port = Utils.getIntegerOrDefaultInRange(properties, "web_server.port", 0, 0, 65535);
      this.host = Utils.getStringOrDefault(properties, "web_server.host", "http://localhost");
      this.setupServiceValue();
      this.updateService();
      this.ERROR_MESSAGE = Utils.getStringOrDefault(properties, "web_server.error.message", this.ERROR_MESSAGE);
      this.classLoader = this.getServletClassLoader(properties);
      this.USEFUL_TOKEN = Utils.getStringOrDefault(properties, "web_server.token", this.USEFUL_TOKEN);
      this.SERVLET_VARIABLE_REFRESH_SUPPORTED = Utils.getBooleanOrDefault(properties, "web_server.variable_refresh", false);
      this.AUTOSAVE_FREQUENCY_IN_MSECS = Utils.getIntegerOrDefaultInRange(properties, "web_server.autosave_frequency", 300, 60, 900) * 1000;
      int i = 1;
      this.handlers.clear();
      this.links.clear();
      String base = "web_server.handler.";

      for(String handler = properties.getProperty(base + i); handler != null; handler = properties.getProperty(base + i)) {
         String[] tokens = handler.split(",");
         if (tokens != null && tokens.length == 2) {
            String linkOrClass = tokens[0].trim();
            String mapping = tokens[1].trim();
            if (linkOrClass.startsWith("http")) {
               this.links.put(this.resolveVariables(linkOrClass), mapping);
            } else if (linkOrClass.startsWith("/")) {
               this.links.put(this.service + linkOrClass.substring(1), mapping);
            } else {
               this.handlers.put(linkOrClass, mapping);
            }
         } else {
            Logger.log(edu.carleton.cas.logging.Level.DIAGNOSTIC, "Web Server " + Server.getVersion() + "): ", "Illegal handler specification (" + handler + ")");
         }

         ++i;
      }

   }

   private String resolveVariables(String stringWithVariables) {
      String resolvedData = stringWithVariables.replace("${HOST}", ClientShared.DIRECTORY_HOST);
      resolvedData = resolvedData.replace("${PORT}", ClientShared.PORT);
      resolvedData = resolvedData.replace("${LOCAL_PORT}", "" + this.port);
      resolvedData = resolvedData.replace("${LOCAL_HOST}", "" + this.host);
      resolvedData = resolvedData.replace("${ID}", this.invigilator.getID());
      resolvedData = resolvedData.replace("${COURSE}", this.invigilator.getCourse());
      resolvedData = resolvedData.replace("${ACTIVITY}", this.invigilator.getActivity());
      resolvedData = resolvedData.replace("${NAME}", this.invigilator.getName().replace(" ", "%20"));
      resolvedData = resolvedData.replace("${IP_ADDRESS}", this.invigilator.getHardwareAndSoftwareMonitor().getIPv4Address());
      resolvedData = resolvedData.replace("${PASSWORD}", this.invigilator.getProperty("student.directory.PASSWORD"));
      return resolvedData;
   }

   public class ServerCheckServlet extends HttpServlet {
      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         ServletProcessor.this.updateLastAccessTime();
         if (ServletProcessor.this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
            ServletProcessor.this.invigilator.setLastServlet("server");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("text/html");
            PrintWriter wr = response.getWriter();
            wr.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\">");
            wr.println(SystemWebResources.getStylesheet());
            wr.println(SystemWebResources.getIcon());
            wr.print("<title>");
            wr.print(ServletProcessor.this.invigilator.getTitle());
            wr.print("CoMaS Server</title></head><body>");
            wr.print(ServletProcessor.this.checkForServletCode());
            wr.print("<div class=\"w3-panel\">");
            wr.print("<h1>Server is ");
            wr.print(ClientShared.DIRECTORY_HOST);
            wr.print("</h1>");
            wr.print("<h1>Web Server (");
            wr.print(Server.getVersion());
            wr.print("): Running on port ");
            wr.print(ServletProcessor.this.port);
            wr.print("</h1>");
            wr.print(ServletProcessor.this.footerForServlet(true, false, SystemWebResources.getHomeButton()));
            wr.print("</div>");
            wr.print("</body></html>");
            response.setStatus(200);
         }

      }
   }

   public class StatisticsServlet extends HttpServlet {
      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         ServletProcessor.this.updateLastAccessTime();
         if (ServletProcessor.this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
            ServletProcessor.this.invigilator.setLastServlet("statistics");
            String reportRequired = "Statistics";
            String header = String.format("<h1>%s Report for %s (%s) for %s</h1>%s%s\n", reportRequired, ServletProcessor.this.invigilator.getName(), ServletProcessor.this.invigilator.getID(), ServletProcessor.this.invigilator.getSessionContext(), ServletProcessor.this.checkForServletCode(), ServletProcessor.this.refreshForServlet());
            String title = reportRequired + " Report";
            String report = ServletProcessor.this.invigilator.getStatisticsReport();
            report = report + ServletProcessor.this.footerForServlet(true, true, SystemWebResources.getHomeButton());
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("text/html");
            PrintWriter wr = response.getWriter();
            wr.println(SystemWebResources.htmlPage(title, header, report));
            response.setStatus(200);
         }

      }
   }

   public class LogServlet extends HttpServlet {
      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         ServletProcessor.this.updateLastAccessTime();
         if (!ServletProcessor.this.invigilator.isInInvigilatorState(InvigilatorState.running)) {
            response.sendError(404);
         } else {
            ServletProcessor.this.invigilator.setLastServlet("log");
            String resource = ClientShared.LOG_DIR + "comas-system-log.html";
            File f = new File(resource);
            String mimeType = "application/octet-stream";
            int statusCode = 200;
            ServletOutputStream os = response.getOutputStream();
            if (f.exists()) {
               FileInputStream fis = new FileInputStream(resource);
               Utils.copyInputStream(fis, os, String.format("</table>\n%s\n%s\n%s\n</div></body></html>", ServletProcessor.this.checkForServletCode(), ServletProcessor.this.refreshScript(), ServletProcessor.this.footerForServlet(true, true, SystemWebResources.getHomeButton())));
            } else {
               try {
                  PrintWriter var10000 = response.getWriter();
                  String var10001 = SystemWebResources.getStylesheet();
                  var10000.println("<html><head>" + var10001 + SystemWebResources.getIcon() + "</head><body><div class=\"w3-container w3-center\"><img alt=\"CoMaS logo\" src=\"" + SystemWebResources.getAppImage() + "\"><h1>This log file no longer exists. Please close the window</h1></div></body></html>");
                  statusCode = 404;
               } catch (IllegalStateException var9) {
               }
            }

            response.setContentType(mimeType);
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setStatus(statusCode);
         }
      }
   }

   public class ApplicationResourceServlet extends HttpServlet {
      private final String mimeType;
      private final boolean saveAsLast;

      ApplicationResourceServlet(String mimeType, boolean saveAsLast) {
         this.mimeType = mimeType;
         this.saveAsLast = saveAsLast;
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         ServletProcessor.this.updateLastAccessTime();
         String name = request.getRequestURI();
         response.addHeader("Access-Control-Allow-Origin", "*");
         response.setContentType(this.mimeType);

         try {
            ServletOutputStream os = response.getOutputStream();
            InputStream fis = ServletProcessor.class.getResourceAsStream(name);
            if (fis == null) {
               response.sendError(404);
            } else {
               Utils.copyInputStream(fis, os);
               if (this.saveAsLast) {
                  ServletProcessor.this.invigilator.setLastServlet(name.substring(1));
               }
            }
         } catch (Exception var6) {
            response.sendError(404);
         }

      }
   }

   public class FolderServlet extends HttpServlet {
      private final String folder;
      private final FileFilter filter;

      public FolderServlet(String folder, FileFilter filter) {
         this.folder = folder;
         this.filter = filter;
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         ServletProcessor.this.updateLastAccessTime();
         if (!this.isInAcceptableState()) {
            response.sendError(404);
         } else {
            String name = request.getRequestURI();
            ServletProcessor.this.invigilator.setLastServlet(name.substring(1));
            int statusCode = 200;
            String resource;
            if (name.startsWith("/")) {
               String var10000 = ClientShared.getBaseDirectory(ServletProcessor.this.invigilator.getCourse(), ServletProcessor.this.invigilator.getActivity());
               resource = var10000 + name.substring(1);
            } else {
               String var26 = ClientShared.getBaseDirectory(ServletProcessor.this.invigilator.getCourse(), ServletProcessor.this.invigilator.getActivity());
               resource = var26 + name;
            }

            if (resource.endsWith("/")) {
               String mimeType = "text/html";
               File f = new File(resource);
               if (f.exists() && f.isDirectory() && f.canRead()) {
                  File[] files = f.listFiles(this.filter);
                  response.addHeader("Access-Control-Allow-Origin", "*");
                  response.setContentType(mimeType);
                  PrintWriter wr = response.getWriter();
                  wr.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\"><title>");
                  wr.print(ServletProcessor.this.invigilator.getTitle());
                  wr.print("CoMaS ");
                  wr.print(f.getName());
                  wr.print(" for ");
                  wr.print(ServletProcessor.this.invigilator.getSessionContext());
                  wr.println("</title>");
                  wr.println(SystemWebResources.getStylesheet());
                  wr.println(SystemWebResources.getIcon());
                  wr.println("</head><body><div class=\"w3-container\"><div class=\"w3-panel\">");
                  wr.print("<h1 id=\"reportTitle\">Listing for ");
                  wr.print(f.getName());
                  wr.print(" for ");
                  wr.print(ServletProcessor.this.invigilator.getSessionContext());
                  wr.println("</h1>");
                  wr.println(ServletProcessor.this.checkForServletCode());
                  wr.println(ServletProcessor.this.refreshForServlet());
                  String openFunction;
                  if (Utils.getBooleanOrDefault(ServletProcessor.this.invigilator.getProperties(), "web_server.window_open." + this.folder, false)) {
                     openFunction = "openWindow";
                  } else {
                     openFunction = "goTo";
                  }

                  if (files != null) {
                     for(File file : files) {
                        String var28 = file.getParentFile().getName();
                        String location = var28 + "/" + file.getName();
                        wr.print("<div style=\"padding:10px\"><button class=\"w3-button w3-round-large w3-blue\" onclick='");
                        wr.print(openFunction);
                        wr.print("(\"/");
                        wr.print(location);
                        wr.print("\", \"");
                        wr.print(file.getName());
                        wr.print("\");'>");
                        wr.print(Utils.removeExtension(file));
                        wr.println("</button></div>");
                     }
                  }

                  if (this.folder.equals("tools")) {
                     ServletProcessor.this.handlers.forEach((k, v) -> {
                        wr.print("<div style=\"padding:10px\"><button class=\"w3-button w3-round-large w3-blue\" onclick='");
                        wr.print(openFunction);
                        wr.print("(\"/");
                        if (v.startsWith("/")) {
                           wr.print(v.substring(1));
                        } else {
                           wr.print(v);
                        }

                        wr.print("\", \"");
                        wr.print(v);
                        wr.print("\");'>");
                        if (v.startsWith("/")) {
                           wr.print(v.substring(1));
                        } else {
                           wr.print(v);
                        }

                        wr.println("</button></div>");
                     });
                     if (PropertyValue.getValue(ServletProcessor.this.invigilator, "session", "tool_pages_required", true)) {
                        String[] var24;
                        for(String ar : var24 = ServletProcessor.this.availableServlets()) {
                           File arf = new File(ar);
                           String label = Utils.removeExtension(arf);
                           wr.print("<div style=\"padding:10px\"><button class=\"w3-button w3-round-large w3-blue\" onclick='");
                           wr.print(openFunction);
                           wr.print("(\"");
                           wr.print(ar);
                           wr.print("\", \"");
                           wr.print(label);
                           wr.print("\");'>");
                           wr.print(label);
                           wr.println("</button></div>");
                        }
                     }

                     ServletProcessor.this.links.forEach((k, v) -> {
                        wr.print("<div style=\"padding:10px\"><button class=\"w3-button w3-round-large w3-blue\" onclick='");
                        wr.print(openFunction);
                        wr.print("(\"");
                        wr.print(k);
                        wr.print("\", \"");
                        wr.print(v);
                        wr.print("\");'>");
                        if (v.startsWith("/")) {
                           wr.print(v.substring(1));
                        } else {
                           wr.print(v);
                        }

                        wr.println("</button></div>");
                     });
                     wr.println("<br/>");
                  }

                  wr.println(ServletProcessor.this.footerForServlet(true, false, SystemWebResources.getHomeButton()));
                  wr.println("</div></div></body></html>");
               } else {
                  PrintWriter var27 = response.getWriter();
                  String var10001 = SystemWebResources.getStylesheet();
                  var27.println("<html><head>" + var10001 + SystemWebResources.getIcon() + "</head><body><h1>The " + f.getName() + " folder is not accessible</h1></body></html>");
                  statusCode = 404;
               }
            } else {
               String mimeType = Mimetypes.getInstance().getMimetype(resource);
               File f = new File(resource);
               if (f.exists() && f.canRead()) {
                  ServletOutputStream os = response.getOutputStream();
                  FileInputStream fis = new FileInputStream(resource);
                  Utils.copyInputStream(fis, os);
               } else {
                  statusCode = 404;
                  PrintWriter var29 = response.getWriter();
                  String var30 = SystemWebResources.getStylesheet();
                  var29.println("<html><head>" + var30 + "</head><body><h1>The " + f.getName() + " file is not accessible</h1></body></html>");
               }

               response.addHeader("Access-Control-Allow-Origin", "*");
               response.setContentType(mimeType);
            }

            response.setStatus(statusCode);
         }
      }

      private boolean isInAcceptableState() {
         return ServletProcessor.this.invigilator.isInInvigilatorState(InvigilatorState.running);
      }
   }

   public class TestServlet extends HttpServlet {
      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         ServletProcessor.this.updateLastAccessTime();
         ServletProcessor.this.invigilator.setLastServlet("test");
         String resource = ClientShared.DIR;
         File f = new File(resource);
         String mimeType = "text/html";
         if (f.exists() && f.isDirectory() && f.canRead()) {
            File[] files = f.listFiles();
            PrintWriter wr = response.getWriter();
            wr.println("<html><head><title>");
            wr.print(ServletProcessor.this.invigilator.getTitle());
            wr.print("CoMaS Folder Listing</title>");
            wr.println(SystemWebResources.getStylesheet());
            wr.println(SystemWebResources.getIcon());
            wr.println("</head><body><div class=\"w3-container\">");
            wr.println("<h1 id=\"reportTitle\">");
            wr.println(resource);
            wr.println("</h1>");
            wr.println(ServletProcessor.this.checkForServletCode());
            wr.println("<ol>");

            for(File file : files) {
               wr.print("<li>");
               wr.print(file.getName());
               wr.println("</li>");
            }

            wr.println("</ol>");
            wr.println(ServletProcessor.this.footerForServlet());
            wr.println("</div></body></html>");
         } else {
            response.getWriter().println("This folder no longer exists.");
         }

         response.setContentType(mimeType);
         response.addHeader("Access-Control-Allow-Origin", "*");
         response.setStatus(200);
      }
   }
}
