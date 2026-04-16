package edu.carleton.cas.reporting;

import com.cogerent.utility.ObjectWithTimestamp;
import com.cogerent.utility.Temporal;
import edu.carleton.cas.background.LogArchiver;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.events.Event;
import edu.carleton.cas.events.EventListener;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.jetty.embedded.GazeServlet;
import edu.carleton.cas.jetty.embedded.IDVerificationServlet;
import edu.carleton.cas.jetty.embedded.QuitServlet;
import edu.carleton.cas.jetty.embedded.ServletProcessor;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.resources.BrowserHistoryReader;
import edu.carleton.cas.resources.SystemWebResources;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import oshi.software.os.OSDesktopWindow;
import oshi.software.os.OSProcess;

public final class ReportManager implements EventListener {
   private static final String DEFAULT_DATE_FORMAT = "HH:mm:ss";
   private static final String[] REPORT_OPTIONS = new String[]{"Activity", "Apps", "Browser", "Context", "Desktop", "Filesystem", "Gaze", "Networking", "Processes", "Session", "Statistics", "Summary"};
   private SimpleDateFormat sdf;
   private boolean debug;
   private String tableClass;
   private boolean reportWindowAsProcess;
   private final ConcurrentHashMap reactor;
   private final Invigilator invigilator;
   static long screenShotGlobalTime = 0L;

   public ReportManager(Invigilator invigilator) {
      this.invigilator = invigilator;
      this.reactor = new ConcurrentHashMap();
      this.reactor.put("network", new NetworkProcessor());
      this.reactor.put("file", new FileProcessor());
      this.reactor.put("unsanctioned_files", new UnsanctionedFileProcessor());
      this.reactor.put("reported_processes", new ProcessesReportedProcessor());
      this.reactor.put("reported_windows", new WindowsReportedProcessor());
      this.reactor.put("reported_pages", new PagesReportedProcessor());
      this.reactor.put("session", new SessionProcessor());
      this.reactor.put("problem", new ProblemProcessor());
      this.reactor.put("annotation", new AnnotationProcessor());
      this.reactor.put("event", new EventProcessor());
      this.reactor.put("default", new DefaultProcessor());
      this.configure();
   }

   public void configure() {
      this.debug = Utils.getBooleanOrDefault(this.invigilator.getProperties(), "report.format.debug", false);

      try {
         this.sdf = new SimpleDateFormat(Utils.getStringOrDefault(this.invigilator.getProperties(), "report.format.date", "HH:mm:ss"));
      } catch (IllegalArgumentException var2) {
         Logger.log(Level.WARNING, "Illegal report format for date, using ", "HH:mm:ss");
         this.sdf = new SimpleDateFormat("HH:mm:ss");
      }

      this.tableClass = this.invigilator.getProperty("report.table.class");
      this.reportWindowAsProcess = Utils.getBooleanOrDefault(this.invigilator.getProperties(), "report.include_window_as_process", false);
      this.reactor.forEach((k, v) -> v.configure(this.invigilator.getProperties()));
   }

   public synchronized void start() {
      this.invigilator.logArchiver.register(this);
      this.invigilator.highPriorityLogArchiver.register(this);
      if (this.debug) {
         this.invigilator.highPriorityLogArchiver.put(Level.DIAGNOSTIC, "Report generation is online");
      }

   }

   public synchronized void clear() {
      ProblemProcessor pp = (ProblemProcessor)this.reactor.get("problem");
      pp.clear();
   }

   public synchronized void stop() {
      this.invigilator.logArchiver.deregister(this);
      this.invigilator.highPriorityLogArchiver.deregister(this);
   }

   public synchronized void notify(Event arg0) {
      if (this.debug) {
         System.out.println("---------------START---------------------------");
         System.out.println(arg0.get("description"));
         System.out.println(arg0.get("severity"));
         System.out.println(arg0.get("logged"));
         System.out.println(arg0.get("time"));
      }

      try {
         if (arg0.containsKey("args")) {
            Object[] args = arg0.get("args");
            if (this.debug) {
               System.out.print("args: [ ");
            }

            for(Object arg : args) {
               if (this.debug) {
                  System.out.print(String.valueOf(arg) + " ");
               }

               Processor p = (Processor)this.reactor.get(arg);
               if (p == null) {
                  p = (Processor)this.reactor.get("default");
               }

               p.process(arg0);
            }

            if (this.debug) {
               System.out.println("]");
            }
         }
      } catch (Exception e) {
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Report exception: " + Utils.printFirstApplicationStackFrameOrException(e));
      }

      if (this.debug) {
         System.out.println("---------------END-----------------------------");
      }

   }

   public boolean reportWindowAsProcess() {
      return this.reportWindowAsProcess;
   }

   public boolean reportProcessorIsChanged(String processor) {
      Processor p = (Processor)this.reactor.get(processor);
      return p == null ? false : p.isChanged();
   }

   public void resetReportProcessorChanged(String processor) {
      Processor p = (Processor)this.reactor.get(processor);
      if (p != null) {
         p.resetChanged();
      }

   }

   public synchronized String getReactorProcessorReport(String processor) {
      Processor p = (Processor)this.reactor.get(processor);
      if (p == null) {
         p = (Processor)this.reactor.get("default");
      }

      return p.toString();
   }

   public boolean hasProblemWithSetStatus(String name) {
      return this.hasProblemWithStatus(name, ReportManager.ProblemStatus.set);
   }

   public boolean hasProblemWithClearStatus(String name) {
      return this.hasProblemWithStatus(name, ReportManager.ProblemStatus.clear);
   }

   public boolean hasProblemWithUnknownStatus(String name) {
      return this.hasProblemWithStatus(name, ReportManager.ProblemStatus.unknown);
   }

   public boolean hasProblemWithStatus(String name, ProblemStatus status) {
      ProblemProcessor pp = (ProblemProcessor)this.reactor.get("problem");
      return pp.hasProblemWithStatus(name, status, 1);
   }

   public boolean hasProblemOccurred(String name, int threshold) {
      ProblemProcessor pp = (ProblemProcessor)this.reactor.get("problem");
      return pp.hasProblemOccurred(name, threshold);
   }

   private Event eventCommon() {
      Event evt = new Event();
      evt.put("time", System.currentTimeMillis());
      evt.put("severity", "event");
      evt.put("logged", 1);
      return evt;
   }

   public void annotateReport(String annotation) {
      Event evt = this.eventCommon();
      evt.put("description", annotation);
      evt.put("args", new Object[]{"annotation"});
      this.notify(evt);
   }

   public void annotateReport(OSProcess process) {
      Event evt = this.eventCommon();
      int var10002 = process.getProcessID();
      evt.put("description", var10002 + " " + process.getName() + " " + process.getUpTime());
      evt.put(OSProcess.class.getSimpleName(), process);
      evt.put("args", new Object[]{"reported_processes"});
      this.notify(evt);
   }

   public void annotateReport(OSDesktopWindow window) {
      Event evt = this.eventCommon();
      String var10002 = window.getTitle();
      evt.put("description", var10002 + " " + window.getCommand());
      evt.put(OSDesktopWindow.class.getSimpleName(), window);
      evt.put("args", new Object[]{"reported_windows"});
      this.notify(evt);
   }

   public void annotateReport(BrowserHistoryReader.HistoryEntry page) {
      Event evt = this.eventCommon();
      String var10002 = page.getUrl();
      evt.put("description", var10002 + " " + String.valueOf(page.getVisitTime()));
      evt.put(BrowserHistoryReader.HistoryEntry.class.getSimpleName(), page);
      evt.put("args", new Object[]{"reported_pages"});
      this.notify(evt);
   }

   public void generateSessionReports(String stateOfSession, String footer) {
      String base = "session.reports." + stateOfSession + this.invigilator.getID();
      String value = this.invigilator.getProperty(base);
      if (value == null) {
         if (stateOfSession.equals("")) {
            base = "session.reports";
         } else if (stateOfSession.equals("start.")) {
            base = "session.reports.start";
         } else if (stateOfSession.equals("end.")) {
            base = "session.reports.end";
         } else if (stateOfSession.endsWith(".")) {
            base = "session.reports." + stateOfSession.substring(0, stateOfSession.length());
         }

         value = this.invigilator.getProperty(base);
      }

      if (value != null) {
         String[] reports = value.trim().split(",");
         if (reports != null) {
            for(String report : reports) {
               this.generateReport(report.trim(), footer);
            }
         }
      } else {
         base = "session.report." + stateOfSession + this.invigilator.getID() + ".";
         int i = 1;
         value = this.invigilator.getProperty(base + i);
         if (value == null) {
            base = "session.report." + stateOfSession;
            value = this.invigilator.getProperty(base + i);
         }

         while(value != null) {
            this.generateReport(value.trim(), footer);
            ++i;
            value = this.invigilator.getProperty(base + i);
         }
      }

   }

   public void generateReport(String reportRequired, String footer) {
      this.generateReport(reportRequired, this.invigilator.logArchiver, footer);
   }

   public synchronized void generateReport(String reportRequired, LogArchiver logArchiver, String footer) {
      if (reportRequired != null && reportRequired.length() > 1) {
         String var10000 = reportRequired.substring(0, 1).toUpperCase();
         reportRequired = var10000 + reportRequired.substring(1);
         String title = reportRequired + " Report";
         if (reportRequired.equalsIgnoreCase("activity")) {
            this.instantReport(logArchiver, (Object[])null, footer);
            return;
         }

         String header;
         String report;
         if (reportRequired.equalsIgnoreCase("statistics")) {
            header = String.format("<h1>%s Report for %s (%s) for %s</h1>", reportRequired, this.invigilator.getName(), this.invigilator.getID(), this.invigilator.getSessionContext());
            report = this.invigilator.getStatisticsReport();
         } else if (reportRequired.equalsIgnoreCase("session")) {
            header = String.format("<h1>%s Report for %s (%s) for %s</h1>", reportRequired, this.invigilator.getName(), this.invigilator.getID(), this.invigilator.getSessionContext());
            report = this.invigilator.getSessionConfigurationModeMonitor().report();
         } else if (reportRequired.equalsIgnoreCase("gaze")) {
            header = String.format("<h1>%s Report for %s (%s) for %s</h1>", reportRequired, this.invigilator.getName(), this.invigilator.getID(), this.invigilator.getSessionContext());
            report = GazeServlet.getSingleton().report();
         } else {
            header = "";
            report = this.invigilator.getHardwareAndSoftwareMonitor().report(reportRequired);
         }

         if (report != null && report.length() > 0) {
            report = report + this.invigilator.getServletProcessor().footerForServlet(false, true, footer);
            logArchiver.put(Level.REPORT, SystemWebResources.htmlPage(title, header, report), reportRequired);
         }
      }

   }

   private String createReport(String footer) {
      return this.createReport((String)null, (String)null, (String)null, false, false, footer);
   }

   private synchronized String createReport(String meta, String bodyScript, String refreshScript, boolean includeQuitButton, boolean textIsCentered, String footer) {
      StringBuilder sb = new StringBuilder();
      sb.append("<html lang=\"en\" xml:lang=\"en\"><head>");
      sb.append(SystemWebResources.getStylesheet());
      sb.append(SystemWebResources.getIcon());
      if (meta != null) {
         sb.append(meta);
      }

      sb.append("</head><body>\n");
      if (bodyScript != null) {
         sb.append(bodyScript);
      }

      sb.append("<div class=\"w3-container\">");
      sb.append("<h1 id=\"reportTitle\">Report for ");
      sb.append(this.invigilator.getCourse());
      sb.append("/");
      sb.append(this.invigilator.getActivity());
      sb.append(" for ");
      sb.append(this.invigilator.getName());
      sb.append(" (");
      sb.append(this.invigilator.getID());
      sb.append(")</h1>");
      if (refreshScript != null) {
         sb.append(refreshScript);
      }

      sb.append(((Processor)this.reactor.get("session")).toString());
      sb.append(((Processor)this.reactor.get("event")).toString());
      sb.append(((Processor)this.reactor.get("problem")).toString());
      sb.append(((Processor)this.reactor.get("reported_pages")).toString());
      sb.append(((Processor)this.reactor.get("reported_windows")).toString());
      sb.append(((Processor)this.reactor.get("reported_processes")).toString());
      sb.append(((Processor)this.reactor.get("file")).toString());
      sb.append(((Processor)this.reactor.get("unsanctioned_files")).toString());
      sb.append(((Processor)this.reactor.get("network")).toString());
      sb.append(((Processor)this.reactor.get("annotation")).toString());
      String footerRequired = this.invigilator.getProperty("report.footer", "true");
      if (footerRequired.equalsIgnoreCase("true")) {
         if (textIsCentered) {
            sb.append("<div style=\"text-align:center\">");
         }

         if (footer != null) {
            sb.append("<span>");
            sb.append(footer);
            sb.append("</span>");
         }

         sb.append("<h5>");
         sb.append("Generated: ");
         sb.append((new Date()).toString());
         sb.append(" using v");
         sb.append("0.8.75");
         sb.append("</h5>");
         if (includeQuitButton) {
            sb.append(SystemWebResources.getHomeButton());
            sb.append(QuitServlet.getQuitButton());
            sb.append("<br/><br/>");
         }

         if (textIsCentered) {
            sb.append("</div>");
         }
      }

      sb.append("</div></body></html>");
      return sb.toString();
   }

   public String health() {
      return this.health(true);
   }

   public String health(boolean asHTML) {
      ProblemProcessor pp = (ProblemProcessor)this.reactor.get("problem");
      int ns = pp.getNumberOfProblems(ReportManager.ProblemStatus.set);
      int nc = pp.getNumberOfProblems(ReportManager.ProblemStatus.clear);
      int nu = pp.getNumberOfProblems(ReportManager.ProblemStatus.unknown);
      if (asHTML) {
         String ss = this.invigilator.getServletProcessor().getService() + "summary";
         if (ns > 0) {
            return this.getHTMLforHealth(ss, "red", "Summary");
         } else if (nu > 0) {
            return this.getHTMLforHealth(ss, "blue", "Summary");
         } else {
            return nc > 0 ? this.getHTMLforHealth(ss, "light-grey", "Summary") : this.getHTMLforHealth(ss, "green", "Summary");
         }
      } else if (ns > 0) {
         return "red";
      } else if (nu > 0) {
         return "blue";
      } else {
         return nc > 0 ? "light-grey" : "green";
      }
   }

   private String getHTMLforHealth(String service, String colour, String label) {
      return String.format("<button class=\"w3-button w3-%s w3-round\" onclick='goTo(\"%s\", \"%s\");'>%s</button>", colour, service, label, colour);
   }

   public synchronized void instantReport(LogArchiver loggerToUse, Object[] args, String footer) {
      Event evt = new Event();
      evt.put("time", System.currentTimeMillis());
      evt.put("description", "instant report");
      evt.put("severity", "event");
      evt.put("logged", 1);
      if (args != null) {
         evt.put("args", args);
      }

      this.notify(evt);
      String reportContent = this.createReport(footer);
      if (loggerToUse != null) {
         loggerToUse.put(Level.REPORT, reportContent, "Activity");
         if (this.debug) {
            System.out.println(reportContent);
         }
      }

   }

   public void notifyProblem(ProblemState problem) {
      this.notifyProblemUsingLogger(this.invigilator.logArchiver, problem);
   }

   public void notifyProblemUsingLogger(LogArchiver loggerToUse, ProblemState problem) {
      if (loggerToUse != null) {
         loggerToUse.put(Level.PROBLEM, problem.toJSON());
         if (this.debug) {
            System.out.println(problem.toJSON());
         }
      }

   }

   public void instantReport(Object[] args, String footer) {
      this.instantReport(this.invigilator.highPriorityLogArchiver, args, footer);
   }

   public void instantReport(Object[] args) {
      this.instantReport(args, (String)null);
   }

   public Object[] createProblemClearEvent(String name) {
      return this.createProblemEvent(name, ReportManager.ProblemStatus.clear());
   }

   public Object[] createProblemSetEvent(String name) {
      return this.createProblemEvent(name, ReportManager.ProblemStatus.set());
   }

   public Object[] createProblemUnknownEvent(String name) {
      return this.createProblemEvent(name, ReportManager.ProblemStatus.unknown());
   }

   public Object[] createProblemUpdateEvent(String name) {
      return this.createProblemEvent(name, ReportManager.ProblemStatus.update());
   }

   public Object[] createProblemEvent(String name, String status) {
      return new Object[]{"problem", name, status};
   }

   public Object[] createProblemEvent(String name, String status, Object... other) {
      Object[] otherArgs = other;
      Object[] combined = new Object[3 + other.length];
      combined[0] = "problem";
      combined[1] = name;
      combined[2] = status;

      for(int i = 0; i < otherArgs.length; ++i) {
         combined[i + 3] = otherArgs[i];
      }

      return combined;
   }

   public void addServletHandler() {
      SummaryReportServlet srs = new SummaryReportServlet(this);
      this.invigilator.getServletProcessor().addServlet(srs, "/" + srs.getMapping());
   }

   private class NetworkProcessor implements Processor {
      HashSet set = new HashSet();
      boolean visible;

      NetworkProcessor() {
      }

      public boolean isChanged() {
         return false;
      }

      public void resetChanged() {
      }

      public void configure(Properties properties) {
         this.visible = Utils.getBooleanOrDefault(properties, "report.network.display", true);
      }

      public void process(Event e) {
         String description = (String)e.get("description");
         if (!description.contains("exception:")) {
            this.set.add(description);
         }
      }

      public int numberOfConnections() {
         return this.set.size();
      }

      public String toString() {
         if (!this.set.isEmpty() && this.visible) {
            StringBuilder sb = new StringBuilder("\n<h2>Connections Accessed (");
            sb.append(this.numberOfConnections());
            sb.append("):</h2><table");
            if (ReportManager.this.tableClass != null) {
               sb.append(" class=\"");
               sb.append(ReportManager.this.tableClass);
               sb.append("\"");
            }

            sb.append("><tbody>\n");
            String sq = SystemWebResources.getSearchQuery();

            for(String s : this.set) {
               sb.append("<tr class=\"network\" id=\"connection\"><td>");
               sb.append("<a style=\"text-decoration:none\" target=\"_blank\" href=\"");
               sb.append(sq);
               String[] query = s.split(" ");
               sb.append(String.join("+", query));
               sb.append("\">");
               sb.append(s);
               sb.append("</a></td></tr>\n");
            }

            sb.append("</tbody></table>");
            return sb.toString();
         } else {
            return "";
         }
      }

      public ObjectWithTimestamp getObservation(String item) {
         return null;
      }
   }

   private class PagesReportedProcessor implements Processor {
      private final ConcurrentHashMap map = new ConcurrentHashMap();
      private boolean changed = false;
      private boolean linkFull;
      private boolean visible;

      PagesReportedProcessor() {
      }

      public void process(Event e) {
         BrowserHistoryReader.HistoryEntry page = (BrowserHistoryReader.HistoryEntry)e.get(BrowserHistoryReader.HistoryEntry.class.getSimpleName());
         ObjectWithTimestamp<BrowserHistoryReader.HistoryEntry> pwt = new ObjectWithTimestamp(page);
         if (pwt.isValid()) {
            ObjectWithTimestamp<BrowserHistoryReader.HistoryEntry> oldPage = (ObjectWithTimestamp)this.map.get(page.getUrl());
            if (oldPage != null) {
               pwt.timestamp = oldPage.timestamp;
               if (((BrowserHistoryReader.HistoryEntry)pwt.object).getVisitTime().after(((BrowserHistoryReader.HistoryEntry)oldPage.object).getVisitTime())) {
                  pwt.count = oldPage.count + 1;
                  this.changed = true;
               }
            } else {
               this.changed = true;
            }

            this.map.put(page.getUrl(), pwt);
         }

         if (ReportManager.this.debug) {
            PrintStream var10000 = System.out;
            String var10001 = this.getClass().getName();
            var10000.println(var10001 + ":" + String.valueOf(e));
         }

      }

      public boolean isChanged() {
         return this.changed;
      }

      public void resetChanged() {
         this.changed = false;
      }

      public void configure(Properties properties) {
         this.linkFull = Utils.getBooleanOrDefault(ReportManager.this.invigilator.getProperties(), "report.link.display", false);
         this.visible = Utils.getBooleanOrDefault(properties, "report.reported_pages.display", true);
      }

      public int numberOfPages() {
         return this.map.size();
      }

      public String toString() {
         if (!this.map.isEmpty() && this.visible) {
            StringBuilder sb = new StringBuilder("\n<h2>Pages (");
            sb.append(this.numberOfPages());
            sb.append("):</h2>\n<table");
            if (ReportManager.this.tableClass != null) {
               sb.append(" class=\"");
               sb.append(ReportManager.this.tableClass);
               sb.append("\"");
            }

            sb.append("><th id=\"page\">Time</th><th id=\"page\">Browser</th><th id=\"page\" style='width:50%'>Page URL</th><th id=\"page\">Visited</th>\n");
            this.map.forEach((k, v) -> {
               sb.append("<tr class=\"process\" id=\"application\"><td>");
               sb.append(ReportManager.this.sdf.format(new Date(v.timestamp)));
               sb.append("</td><td>");
               sb.append(((BrowserHistoryReader.HistoryEntry)v.object).getBrowser());
               sb.append("</td><td>");
               sb.append("<a style=\"text-decoration:none\" target=\"_blank\" href=\"");
               sb.append(((BrowserHistoryReader.HistoryEntry)v.object).getUrl());
               sb.append("\">");
               if (this.linkFull) {
                  sb.append(((BrowserHistoryReader.HistoryEntry)v.object).getUrl());
               } else {
                  try {
                     URI uri = new URI(((BrowserHistoryReader.HistoryEntry)v.object).getUrl());
                     sb.append(uri.getScheme());
                     sb.append(":");
                     sb.append("//");
                     sb.append(uri.getAuthority());
                     sb.append(uri.getPath());
                  } catch (URISyntaxException var5) {
                     sb.append("-");
                  }
               }

               sb.append("</a></td><td>");
               sb.append(ReportManager.this.sdf.format(((BrowserHistoryReader.HistoryEntry)v.object).getVisitTime()));
               if (v.count > 1) {
                  sb.append(" (");
                  sb.append(v.count);
                  sb.append(")");
               }

               sb.append("</td></tr>");
            });
            sb.append("</table>\n");
            sb.append("<script>");
            sb.append(SystemWebResources.sorting("page"));
            sb.append("</script>");
            return sb.toString();
         } else {
            return "";
         }
      }

      public ObjectWithTimestamp getObservation(String item) {
         return (ObjectWithTimestamp)this.map.get(item);
      }
   }

   private class WindowsReportedProcessor implements Processor {
      private final ConcurrentHashMap map = new ConcurrentHashMap();
      private boolean changed = false;
      private boolean visible;

      WindowsReportedProcessor() {
      }

      public void process(Event e) {
         OSDesktopWindow window = (OSDesktopWindow)e.get(OSDesktopWindow.class.getSimpleName());
         ObjectWithTimestamp<OSDesktopWindow> wwt = new ObjectWithTimestamp(window);
         if (wwt.isValid()) {
            if (this.map.get(window.getTitle()) == null) {
               this.changed = true;
            }

            this.map.put(window.getTitle(), wwt);
         }

         if (ReportManager.this.debug) {
            PrintStream var10000 = System.out;
            String var10001 = this.getClass().getName();
            var10000.println(var10001 + ":" + String.valueOf(e));
         }

      }

      public boolean isChanged() {
         return this.changed;
      }

      public void resetChanged() {
         this.changed = false;
      }

      public void configure(Properties properties) {
         this.visible = Utils.getBooleanOrDefault(properties, "report.reported_windows.display", true);
      }

      public int numberOfWindows() {
         return this.map.size();
      }

      public String toString() {
         if (!this.map.isEmpty() && this.visible) {
            StringBuilder sb = new StringBuilder("\n<h2>Windows (");
            sb.append(this.numberOfWindows());
            sb.append("):</h2>\n<table");
            if (ReportManager.this.tableClass != null) {
               sb.append(" class=\"");
               sb.append(ReportManager.this.tableClass);
               sb.append("\"");
            }

            sb.append("><th id=\"window\">Time</th><th id=\"window\">(Location)&[Size]</th><th id=\"window\">Window Name</th>\n");
            String sq = SystemWebResources.getSearchQuery();
            this.map.forEach((k, v) -> {
               sb.append("<tr class=\"process\" id=\"application\"><td>");
               sb.append(ReportManager.this.sdf.format(new Date(v.timestamp)));
               sb.append("</td><td>(");
               Rectangle r = ((OSDesktopWindow)v.object).getLocAndSize();
               sb.append(r.getLocation().getX());
               sb.append(",");
               sb.append(r.getLocation().getY());
               sb.append(")&nbsp;[");
               sb.append(r.getWidth());
               sb.append(",");
               sb.append(r.getHeight());
               sb.append("]</td><td>");
               sb.append("<a style=\"text-decoration:none\" target=\"_blank\" href=\"");
               sb.append(sq);
               String[] query = k.split(" ");
               sb.append(String.join("+", query));
               sb.append("\">");
               sb.append(k);
               sb.append("</a></td></tr>\n");
            });
            sb.append("</table>\n");
            sb.append("<script>");
            sb.append(SystemWebResources.sorting("window"));
            sb.append("</script>");
            return sb.toString();
         } else {
            return "";
         }
      }

      public ObjectWithTimestamp getObservation(String item) {
         return (ObjectWithTimestamp)this.map.get(item);
      }
   }

   private class ProcessesReportedProcessor implements Processor {
      private final ConcurrentHashMap map = new ConcurrentHashMap();
      private boolean changed = false;
      private boolean visible;

      ProcessesReportedProcessor() {
      }

      public boolean isChanged() {
         return this.changed;
      }

      public void resetChanged() {
         this.changed = false;
      }

      public void configure(Properties properties) {
         this.visible = Utils.getBooleanOrDefault(properties, "report.reported_processes.display", true);
      }

      public void process(Event e) {
         OSProcess process = (OSProcess)e.get(OSProcess.class.getSimpleName());
         ObjectWithTimestamp<OSProcess> pwt = new ObjectWithTimestamp(process);
         if (pwt.isValid()) {
            if (this.map.get(process.getName()) == null) {
               this.changed = true;
            }

            this.map.put(process.getName(), pwt);
         }

         if (ReportManager.this.debug) {
            PrintStream var10000 = System.out;
            String var10001 = this.getClass().getName();
            var10000.println(var10001 + ":" + String.valueOf(e));
         }

      }

      public int numberOfProcesses() {
         return this.map.size();
      }

      public String toString() {
         if (!this.map.isEmpty() && this.visible) {
            StringBuilder sb = new StringBuilder("\n<h2>Processes (");
            sb.append(this.numberOfProcesses());
            sb.append("):</h2>\n<table");
            if (ReportManager.this.tableClass != null) {
               sb.append(" class=\"");
               sb.append(ReportManager.this.tableClass);
               sb.append("\"");
            }

            sb.append("><th id=\"process\">Time</th><th id=\"process\">Up Time</th><th id=\"process\">Process Name</th>\n");
            String sq = SystemWebResources.getSearchQuery();
            this.map.forEach((k, v) -> {
               sb.append("<tr class=\"process\" id=\"application\"><td>");
               sb.append(ReportManager.this.sdf.format(new Date(v.timestamp)));
               sb.append("</td><td>");
               sb.append(Utils.convertMsecsToHoursMinutesSeconds(((OSProcess)v.object).getUpTime()));
               sb.append("</td><td>");
               sb.append("<a style=\"text-decoration:none\" target=\"_blank\" href=\"");
               sb.append(sq);
               String[] query = k.split(" ");
               sb.append(String.join("+", query));
               sb.append("\">");
               sb.append(k);
               sb.append("</a></td></tr>\n");
            });
            sb.append("</table>\n");
            sb.append("<script>");
            sb.append(SystemWebResources.sorting("process"));
            sb.append("</script>");
            return sb.toString();
         } else {
            return "";
         }
      }

      public ObjectWithTimestamp getObservation(String item) {
         return (ObjectWithTimestamp)this.map.get(item);
      }
   }

   private class UnsanctionedFileProcessor implements Processor {
      ConcurrentHashMap map = new ConcurrentHashMap();
      boolean visible;

      UnsanctionedFileProcessor() {
      }

      public boolean isChanged() {
         return false;
      }

      public void resetChanged() {
      }

      public void configure(Properties properties) {
         this.visible = Utils.getBooleanOrDefault(properties, "report.unsanctioned_files.display", true);
      }

      public void process(Event e) {
         Object[] args = e.get("args");

         for(Object arg : args) {
            String name = arg.toString();
            if (name.startsWith("file:")) {
               File f = new File(name.substring("file:".length()));
               this.map.put(f.getAbsolutePath(), f);
            }
         }

         if (ReportManager.this.debug) {
            PrintStream var10000 = System.out;
            String var10001 = this.getClass().getName();
            var10000.println(var10001 + ":" + String.valueOf(e));
         }

      }

      public int numberOfFiles() {
         return this.map.size();
      }

      public String toString() {
         if (!this.map.isEmpty() && this.visible) {
            StringBuilder sb = new StringBuilder("\n<h2>Unsanctioned Files Created (");
            sb.append(this.numberOfFiles());
            sb.append("):</h2>\n<table");
            if (ReportManager.this.tableClass != null) {
               sb.append(" class=\"");
               sb.append(ReportManager.this.tableClass);
               sb.append("\"");
            }

            sb.append("><thead><th>Time</th><th>File</th></thead><tbody>\n");

            for(Map.Entry e : this.map.entrySet()) {
               sb.append("<tr class=\"file\" id=\"application\"><td>");
               sb.append(ReportManager.this.sdf.format(new Date(((File)e.getValue()).lastModified())));
               sb.append("</td><td>");
               sb.append(((File)e.getValue()).getAbsolutePath());
               sb.append("</td></tr>\n");
            }

            sb.append("</tbody>\n</table>\n");
            return sb.toString();
         } else {
            return "";
         }
      }

      public ObjectWithTimestamp getObservation(String item) {
         return null;
      }
   }

   private class FileProcessor implements Processor {
      ConcurrentHashMap map = new ConcurrentHashMap();
      ConcurrentHashMap timeMap = new ConcurrentHashMap();
      boolean visible;

      FileProcessor() {
      }

      public boolean isChanged() {
         return false;
      }

      public void resetChanged() {
      }

      public void configure(Properties properties) {
         this.visible = Utils.getBooleanOrDefault(properties, "report.file.display", true);
      }

      public void process(Event e) {
         String description = (String)e.get("description");
         if (!description.contains("exception:")) {
            int index = description.indexOf(": ");
            if (index != -1) {
               String application = description.substring(0, index);
               String file = description.substring(index + 1);
               ArrayList<String> filesOpenedByApplication = (ArrayList)this.map.get(application);
               if (filesOpenedByApplication == null) {
                  long time = (Long)e.get("time");
                  filesOpenedByApplication = new ArrayList();
                  filesOpenedByApplication.add(file);
                  this.map.put(application, filesOpenedByApplication);
                  this.timeMap.put(file, new Long[]{time, time});
               } else if (!filesOpenedByApplication.contains(file)) {
                  long time = (Long)e.get("time");
                  filesOpenedByApplication.add(file);
                  this.timeMap.put(file, new Long[]{time, time});
               } else {
                  Long[] times = (Long[])this.timeMap.get(file);
                  if (times != null) {
                     long time = (Long)e.get("time");
                     times[1] = time;
                     this.timeMap.put(file, times);
                  }
               }
            } else if (ReportManager.this.debug) {
               PrintStream var10000 = System.out;
               String var10001 = this.getClass().getName();
               var10000.println(var10001 + ":" + String.valueOf(e));
            }

         }
      }

      public int numberOfFiles() {
         return this.timeMap.size();
      }

      public String toString() {
         if (!this.map.isEmpty() && this.visible) {
            StringBuilder sb = new StringBuilder("\n<h2>Files Accessed (");
            sb.append(this.numberOfFiles());
            sb.append("):</h2>\n<table");
            if (ReportManager.this.tableClass != null) {
               sb.append(" class=\"");
               sb.append(ReportManager.this.tableClass);
               sb.append("\"");
            }

            sb.append("><thead><th>Application</th>\t\t\t<th>Time</th><th>File</th></thead><tbody>\n");
            String sq = SystemWebResources.getSearchQuery();

            for(Map.Entry e : this.map.entrySet()) {
               sb.append("<tr class=\"file\" id=\"application\"><td>");
               sb.append("<a style=\"text-decoration:none\" target=\"_blank\" href=\"");
               sb.append(sq);
               String[] query = ((String)e.getKey()).split(" ");
               sb.append(String.join("+", query));
               sb.append("\">");
               sb.append((String)e.getKey());
               sb.append("</a></td><td></td></tr>\n");

               for(String f : (ArrayList)e.getValue()) {
                  sb.append("\t\t\t<tr class=\"file\" id=\"name\"><td></td><td>");
                  Long[] times = (Long[])this.timeMap.get(f);
                  sb.append((new Date(times[0])).toString());
                  sb.append(" (");
                  sb.append(ReportManager.this.sdf.format(new Date(times[1])));
                  sb.append(")\t\t\t</td><td>");
                  sb.append(f);
                  sb.append("</td></tr>\n");
               }
            }

            sb.append("</tbody>\n</table>\n");
            return sb.toString();
         } else {
            return "";
         }
      }

      public ObjectWithTimestamp getObservation(String item) {
         return null;
      }
   }

   private class SituationStatus {
      final String name;
      long time;
      int count;

      SituationStatus(String name) {
         this.name = name;
         this.time = 0L;
         this.count = 0;
      }

      public long get() {
         return this.time;
      }

      public void set(long time) {
         this.time = time;
         ++this.count;
      }

      public String toString() {
         return String.format(" %s(%d): %s", this.name, this.count, ReportManager.this.sdf.format(new Date(this.time)));
      }

      public String toString(long _time) {
         return this.time > 0L ? this.toString() : String.format(" %s(%d): %s", this.name, this.count, ReportManager.this.sdf.format(new Date(_time)));
      }
   }

   private class SessionProcessor implements Processor {
      long start = 0L;
      long stop = 0L;
      String screenShots = "";
      long screenShotTime = 0L;
      long screenShotBacklog = 0L;
      String archives = "";
      long archiveTime = 0L;
      long archiveBacklog = 0L;
      boolean terminated = false;
      boolean webcam = false;
      boolean inPIP = false;
      String webcamStatus = "";
      SituationStatus webcamStartTime = ReportManager.this.new SituationStatus("Start");
      SituationStatus webcamStopTime = ReportManager.this.new SituationStatus("Stop");
      SituationStatus pipStartTime = ReportManager.this.new SituationStatus("PIP Start");
      SituationStatus pipStopTime = ReportManager.this.new SituationStatus("PIP Stop");
      boolean student_id_detected = false;
      long student_id_detected_time;
      boolean visible;
      ProblemState reset = ReportManager.this.new ProblemState("problem monitoring", "Session problem monitoring", "Monitors the start/stop of a session");

      SessionProcessor() {
      }

      public boolean isChanged() {
         return false;
      }

      public void resetChanged() {
      }

      public void configure(Properties properties) {
         this.visible = Utils.getBooleanOrDefault(properties, "report.session.display", true);
      }

      public void process(Event e) {
         long time = (Long)e.get("time");
         Object[] args = e.get("args");

         for(Object arg : args) {
            if (arg.equals("start")) {
               this.start = time;
               this.reset.updateState(ReportManager.ProblemStatus.set, time, "Problem monitoring started");
            } else if (arg.equals("stop")) {
               this.stop = time;
               this.reset.updateState(ReportManager.ProblemStatus.clear, time, "Problem monitoring stopped");
            } else if (arg.equals("terminated")) {
               this.stop = time;
               this.terminated = true;
               this.reset.updateState(ReportManager.ProblemStatus.clear, time, "Problem monitoring terminated");
            } else if (arg.equals("webcam:start")) {
               this.webcamStatus = "start";
               this.webcamStartTime.set(time);
               this.webcam = true;
            } else if (!arg.equals("webcam:error") && !arg.equals("webcam:stop") && !arg.equals("webcam:close")) {
               if (arg.equals("pip:enter") && !this.inPIP) {
                  this.pipStartTime.set(time);
                  this.inPIP = true;
               } else if (arg.equals("pip:leave") && this.inPIP) {
                  this.pipStopTime.set(time);
                  this.inPIP = false;
               } else if (arg.equals("student-id-detected")) {
                  this.student_id_detected_time = time;
                  this.student_id_detected = true;
               } else {
                  String argAsString = arg.toString();
                  int index = argAsString.indexOf("screen:");
                  if (index > -1) {
                     this.screenShots = argAsString.substring(index + "screen:".length());
                  }

                  index = argAsString.indexOf("archive:");
                  if (index > -1) {
                     this.archives = argAsString.substring(index + "archive:".length());
                  }

                  index = argAsString.indexOf("screenShotTime:");
                  if (index > -1) {
                     try {
                        this.screenShotTime = Long.parseLong(argAsString.substring(index + "screenShotTime:".length()));
                     } catch (NumberFormatException var15) {
                        this.screenShotTime = 0L;
                     }
                  }

                  index = argAsString.indexOf("screenShotBacklog:");
                  if (index > -1) {
                     try {
                        this.screenShotBacklog = Long.parseLong(argAsString.substring(index + "screenShotBacklog:".length()));
                     } catch (NumberFormatException var14) {
                        this.screenShotBacklog = 0L;
                     }
                  }

                  index = argAsString.indexOf("archiveTime:");
                  if (index > -1) {
                     try {
                        this.archiveTime = Long.parseLong(argAsString.substring(index + "archiveTime:".length()));
                     } catch (NumberFormatException var13) {
                        this.archiveTime = 0L;
                     }
                  }

                  index = argAsString.indexOf("archiveBacklog:");
                  if (index > -1) {
                     try {
                        this.archiveBacklog = Long.parseLong(argAsString.substring(index + "archiveBacklog:".length()));
                     } catch (NumberFormatException var12) {
                        this.archiveBacklog = 0L;
                     }
                  }
               }
            } else {
               this.webcamStatus = arg.toString().substring("webcam:".length());
               this.webcamStopTime.set(time);
               if (this.webcamStatus.equals("error")) {
                  this.webcam = false;
               }

               if (this.inPIP) {
                  this.pipStopTime.set(time);
                  this.inPIP = false;
               }
            }
         }

      }

      public String toString() {
         if (!this.visible) {
            return "";
         } else {
            StringBuilder sb = new StringBuilder();
            sb.append("\n<h2>Session:</h2><table");
            if (ReportManager.this.tableClass != null) {
               sb.append(" class=\"");
               sb.append(ReportManager.this.tableClass);
               sb.append("\"");
            }

            sb.append("><tbody><tr class=\"session\" id=\"started\"><td>\n\tstarted:</td><td>\t");
            sb.append((new Date(this.start)).toString());
            sb.append("</td></tr><tr class=\"session\" id=\"stopped\"><td>\n\tstopped:</td><td>\t");
            if (this.stop < this.start) {
               sb.append("unknown");
            } else {
               sb.append((new Date(this.stop)).toString());
            }

            if (this.stop < this.start) {
               sb.append("</td></tr><tr class=\"session\" id=\"duration\"><td>duration:</td><td>no logoff</td></tr>");
            } else {
               long durationInMillis = this.stop - this.start;
               String durationAsString = Utils.convertMsecsToHoursMinutesSeconds(durationInMillis);
               sb.append(String.format("</td></tr><tr class=\"session\" id=\"duration\"><td>\n\tduration:</td>\t<td>%.03f seconds (%s)</td></tr>", (double)durationInMillis / (double)1000.0F, durationAsString));
            }

            if (this.terminated) {
               sb.append("<tr><td>\n\tsession was terminated</td><td></td></tr>");
            }

            if (this.screenShots.length() > 0) {
               sb.append("\n<tr class=\"session\" id=\"screenshots\"><td>\tscreenshots:</td><td>");
               sb.append(this.screenShots);
               if (this.screenShotTime > 0L) {
                  sb.append(" Latest: ");
                  sb.append(new Date(this.screenShotTime));
                  if (this.screenShotBacklog > 0L) {
                     sb.append(" Backlog: ");
                     sb.append(this.screenShotBacklog);
                  }
               }

               sb.append("</td></tr>");
            }

            if (this.archives.length() > 0) {
               sb.append("\n<tr class=\"session\" id=\"archives\"><td>\tarchives:</td><td>");
               sb.append(this.archives);
               if (this.archiveTime > 0L) {
                  sb.append(" Latest: ");
                  sb.append(new Date(this.archiveTime));
                  if (this.archiveBacklog > 0L) {
                     sb.append(" Backlog: ");
                     sb.append(this.archiveBacklog);
                  }
               }

               sb.append("</td></tr>");
            }

            sb.append("\n<tr class=\"session\" id=\"webcam\"><td>\twebcam:</td><td>");
            if (this.webcam) {
               sb.append("yes(");
               sb.append(this.webcamStatus);
               sb.append(")");
               if (this.inPIP) {
                  sb.append(", in PIP. ");
               } else {
                  sb.append(", not in PIP. ");
               }
            } else {
               sb.append("no");
            }

            if (this.webcamStartTime.get() > 0L) {
               sb.append(this.webcamStartTime);
            }

            if (this.webcamStopTime.get() > 0L || this.stop > this.start && this.webcamStartTime.get() > 0L) {
               sb.append(this.webcamStopTime.toString(this.stop));
            }

            if (this.pipStartTime.get() > 0L) {
               sb.append(this.pipStartTime);
            }

            if (this.pipStopTime.get() > 0L || this.stop > this.start && this.pipStartTime.get() > 0L) {
               sb.append(this.pipStopTime.toString(this.stop));
            }

            sb.append("</td>");
            sb.append("</tr>");
            if (this.student_id_detected) {
               sb.append("<tr><td>student id was detected:</td><td>");
               sb.append((new Date(this.student_id_detected_time)).toString());
               sb.append(" (");
               sb.append(IDVerificationServlet.getSingleton().getPercentageConfidence());
               sb.append("% in ");
               int attempts = IDVerificationServlet.getSingleton().getNumberOfAttempts();
               sb.append(attempts);
               if (attempts > 1) {
                  sb.append(" attempts)</td></tr>");
               } else {
                  sb.append(" attempt)</td></tr>");
               }
            }

            String location = ReportManager.this.invigilator.getLocation(true);
            if (location.length() > 0) {
               sb.append("<tr><td>location:</td><td>");
               sb.append(ReportManager.this.invigilator.getProperty("LOCAL_ADDRESS", ""));
               sb.append(" ");
               sb.append(location);
               sb.append("</td></tr>");
            }

            sb.append("</tbody></table>");
            return sb.toString();
         }
      }

      public ObjectWithTimestamp getObservation(String item) {
         return null;
      }
   }

   public static enum ProblemIndex {
      login,
      vm_test,
      vm_display,
      vm_interfaces,
      vm_running,
      screen_recording_permissions,
      screenshot,
      webcam_image_content,
      webcam_not_authorized,
      id_not_verified,
      excessive_monitors,
      deleted_archives,
      deleted_screenshots,
      archive,
      suspended,
      sleeping,
      faces,
      multiple_faces,
      screen_off,
      webcam_error,
      slow_screenshot_upload,
      slow_archive_upload,
      file_deletion,
      network_configuration,
      removeable_disk,
      denied_processes,
      denied_windows,
      denied_pages,
      software_configuration,
      no_activity,
      password_error,
      administrator_mode,
      vpn_detected,
      unsanctioned_files,
      file_deletion_archives,
      file_deletion_tools,
      file_deletion_screens;
   }

   private class ProblemProcessor implements Processor {
      private ProblemState[] problems;
      int numberOfProblems;
      boolean changed;
      boolean visible;

      ProblemProcessor() {
         this.problems = new ProblemState[]{ReportManager.this.new ProblemState("login", "Disconnected", "Connection to server disrupted", ReportManager.ProblemCategory.network), ReportManager.this.new ProblemState("vm_test", "VM test   ", "A virtual machine detection test could not run", ReportManager.ProblemCategory.system), ReportManager.this.new ProblemState("vm_display", "VM Display", "Unusual screen resolution detected"), ReportManager.this.new ProblemState("vm_interfaces", "VM interfaces", "Standard virtual machine network interfaces detected"), ReportManager.this.new ProblemState("vm_running", "VM running", "A virtual machine was running", ReportManager.ProblemCategory.academic), ReportManager.this.new ProblemState("screen_recording_permissions", "Screen recording permission", "Screen recording permission not configured", ReportManager.ProblemCategory.student), ReportManager.this.new ProblemState("screenshot", "Screen shot", "Screen shots could not be saved", ReportManager.ProblemCategory.system), ReportManager.this.new ProblemState("webcam_image_content", "Web image is blank", "Webcam image has no content"), ReportManager.this.new ProblemState("webcam_not_authorized", "Webcam not authorized", "Webcam authorization required", ReportManager.ProblemCategory.student), ReportManager.this.new ProblemState("id_not_verified", "ID not provided", "Saving an ID image required", ReportManager.ProblemCategory.student), ReportManager.this.new ProblemState("excessive_monitors", "Too many monitors", "A maximum number of monitors has been exceeded", ReportManager.ProblemCategory.system), ReportManager.this.new ProblemState("deleted_archives", "Archives deleted", "Archives have been deleted", ReportManager.ProblemCategory.student), ReportManager.this.new ProblemState("deleted_screenshots", "Screen shots deleted", "Screen shots have been deleted", ReportManager.ProblemCategory.student), ReportManager.this.new ProblemState("archive", "Archive", "Archives could not be saved", ReportManager.ProblemCategory.system), ReportManager.this.new ProblemState("suspended", "Suspended?", "Machine possibly suspended", ReportManager.ProblemCategory.system), ReportManager.this.new ProblemState("sleeping", "Sleeping", "Machine was sleeping", ReportManager.ProblemCategory.system), ReportManager.this.new ProblemState("faces", "Face detection", "Faces not detected on web cam", ReportManager.ProblemCategory.student), ReportManager.this.new ProblemState("multiple_faces", "Multiple face detection", "More than 1 face detected on web cam", ReportManager.ProblemCategory.student), ReportManager.this.new ProblemState("screen_off", "Screen off", "Screen was off", ReportManager.ProblemCategory.system), ReportManager.this.new ProblemState("webcam_error", "Webcam error", "Browser cannot start a webcam"), ReportManager.this.new ProblemState("slow_screenshot_upload", "Slow screen upload", "Screen shot upload time excessive", ReportManager.ProblemCategory.network), ReportManager.this.new ProblemState("slow_archive_upload", "Slow archive upload", "Archive upload time excessive", ReportManager.ProblemCategory.network), ReportManager.this.new ProblemState("file_deletion", "Files deleted", "Files deleted by user", ReportManager.ProblemCategory.student), ReportManager.this.new ProblemState("network_configuration", "Network configuration", "IP address or DNS issues exist", ReportManager.ProblemCategory.network), ReportManager.this.new ProblemState("removeable_disk", "Removeable media", "Removeable or shared media detected"), ReportManager.this.new ProblemState("denied_processes", "Unauthorized processes", "Unauthorized processes running", ReportManager.ProblemCategory.academic), ReportManager.this.new ProblemState("denied_windows", "Unauthorized application", "Unauthorized application running", ReportManager.ProblemCategory.academic), ReportManager.this.new ProblemState("denied_pages", "Unauthorized web page access", "Unauthorized pages access", ReportManager.ProblemCategory.academic), ReportManager.this.new ProblemState("software_configuration", "Software access", "Software access or configuration issue"), ReportManager.this.new ProblemState("no_activity", "Insufficient exam activity", "No work appearing in exam folder", ReportManager.ProblemCategory.student), ReportManager.this.new ProblemState("password_error", "Correct password?", "Password is incorrect or missing", ReportManager.ProblemCategory.student), ReportManager.this.new ProblemState("administrator_mode", "Insufficient privilege", "Application requires \"Run as administrator\"", ReportManager.ProblemCategory.student), ReportManager.this.new ProblemState("vpn_detected", "VPN active", "VPN detected, network configuration affected", ReportManager.ProblemCategory.network), ReportManager.this.new ProblemState("unsanctioned_files", "Unsanctioned files", "Unsanctioned files created", ReportManager.ProblemCategory.student), ReportManager.this.new ProblemStateWithCondition("file_deletion_archives", "Files deleted", "Archives deleted from archives folder", ReportManager.ProblemCategory.system, new Condition() {
            public boolean isMet(String description) {
               return description.startsWith("ENTRY_DELETE") && description.endsWith("archives");
            }
         }), ReportManager.this.new ProblemStateWithCondition("file_deletion_tools", "Files deleted", "Tools deleted from tools folder", ReportManager.ProblemCategory.system, new Condition() {
            public boolean isMet(String description) {
               return description.startsWith("ENTRY_DELETE") && description.endsWith("tools");
            }
         }), ReportManager.this.new ProblemStateWithCondition("file_deletion_screens", "Files deleted", "Screen deleted from screens folder", ReportManager.ProblemCategory.system, new Condition() {
            public boolean isMet(String description) {
               return description.startsWith("ENTRY_DELETE") && description.endsWith("screens");
            }
         })};
         this.numberOfProblems = 0;
      }

      public ProblemState getProblem(ProblemIndex index) {
         if (index.ordinal() > this.problems.length - 1) {
            throw new IllegalArgumentException("Synchronization issue between ProblemState and ProblemIndex");
         } else {
            return this.problems[index.ordinal()];
         }
      }

      public String getProblemName(ProblemIndex index) {
         if (index.ordinal() > this.problems.length - 1) {
            throw new IllegalArgumentException("Synchronization issue between ProblemState and ProblemIndex");
         } else {
            return this.problems[index.ordinal()].getName();
         }
      }

      public boolean isChanged() {
         return this.changed;
      }

      public void resetChanged() {
         this.changed = false;
      }

      public void configure(Properties properties) {
         this.visible = Utils.getBooleanOrDefault(properties, "report.problem.display", true);

         ProblemState[] var5;
         for(ProblemState ps : var5 = this.problems) {
            ps.configure(properties);
         }

      }

      public void clear() {
         long time = System.currentTimeMillis();

         ProblemState[] var6;
         for(ProblemState ps : var6 = this.problems) {
            if (ps.isSet() && ps.updateState(ReportManager.ProblemStatus.clear, time, "Cleared: " + ps.description)) {
               this.changed = true;
            }
         }

      }

      public void process(Event e) {
         String event_description = (String)e.get("description");
         long time = (Long)e.get("time");
         Object[] args = e.get("args");
         ArrayList<ProblemState> psArray = this.problems(args);
         if (psArray.size() > 0) {
            ProblemStatus pstat = ReportManager.ProblemStatus.status(args);

            for(ProblemState ps : psArray) {
               if (ps.updateState(pstat, time, event_description, args)) {
                  this.changed = true;
               }
            }
         }

      }

      ArrayList problems(Object[] args) {
         ArrayList<ProblemState> ps = new ArrayList();

         for(Object arg : args) {
            ProblemState[] var10;
            for(ProblemState problem : var10 = this.problems) {
               if (arg.equals(problem.name)) {
                  ps.add(problem);
               }
            }
         }

         return ps;
      }

      public boolean hasProblemOccurred(String name, int threshold) {
         ProblemState[] var6;
         for(ProblemState problem : var6 = this.problems) {
            if (problem.name.equals(name) && problem.count >= threshold) {
               return true;
            }
         }

         return false;
      }

      public boolean hasProblemWithStatus(String name, ProblemStatus _status, int threshold) {
         ProblemState[] var7;
         for(ProblemState problem : var7 = this.problems) {
            if (problem.name.equals(name) && problem.count >= threshold && problem.status == _status) {
               return true;
            }
         }

         return false;
      }

      public int getNumberOfProblems() {
         this.numberOfProblems = 0;

         ProblemState[] var4;
         for(ProblemState problem : var4 = this.problems) {
            if (problem.occurred()) {
               ++this.numberOfProblems;
            }
         }

         return this.numberOfProblems;
      }

      public int getNumberOfProblems(ProblemStatus _status) {
         int numberOfProblemsWithStatus = 0;

         ProblemState[] var6;
         for(ProblemState problem : var6 = this.problems) {
            if (problem.occurred() && problem.status == _status) {
               ++numberOfProblemsWithStatus;
            }
         }

         return numberOfProblemsWithStatus;
      }

      public String toString() {
         if (!this.visible) {
            return "";
         } else {
            boolean aProblemOccurred = false;
            StringBuilder sb = new StringBuilder("\n<h2>Problems (");
            sb.append(this.getNumberOfProblems());
            sb.append("):</h2><table");
            if (ReportManager.this.tableClass != null) {
               sb.append(" class=\"");
               sb.append(ReportManager.this.tableClass);
               sb.append("\"");
            }

            sb.append(">\n<th id=\"problem\">Name</th>\t\t<th id=\"problem\">State</th><th id=\"problem\" class=\"w3-center\">Count</th>\t<th id=\"problem\">Duration</th><th id=\"problem\">Last time</th>\t\t<th id=\"problem\">Description</th>\n");

            ProblemState[] var6;
            for(ProblemState problem : var6 = this.problems) {
               if (problem.occurred()) {
                  aProblemOccurred = true;
                  sb.append(problem.toString());
                  sb.append("\n");
               }
            }

            sb.append("</table>");
            sb.append("<script>");
            sb.append(SystemWebResources.sorting("problem"));
            sb.append("</script>");
            if (aProblemOccurred) {
               return sb.toString();
            } else {
               return "";
            }
         }
      }

      public ObjectWithTimestamp getObservation(String item) {
         return null;
      }
   }

   public static enum ProblemStatus {
      set,
      clear,
      unknown,
      update;

      static ProblemStatus status(String arg) {
         if (arg.equals("set")) {
            return set;
         } else if (arg.equals("clear")) {
            return clear;
         } else if (arg.equals("unknown")) {
            return unknown;
         } else {
            return arg.equals("update") ? update : unknown;
         }
      }

      public static String set() {
         return "set";
      }

      public static String clear() {
         return "clear";
      }

      public static String unknown() {
         return "unknown";
      }

      public static String update() {
         return "update";
      }

      public String visual() {
         if (this != set && this != update) {
            if (this == clear) {
               return "✅";
            } else {
               return this == unknown ? "\ud83d\udfe6" : "❗";
            }
         } else {
            return "❌";
         }
      }

      static ProblemStatus status(Object[] args) {
         for(Object arg : args) {
            if (arg.equals("set")) {
               return set;
            }

            if (arg.equals("clear")) {
               return clear;
            }

            if (arg.equals("unknown")) {
               return unknown;
            }

            if (arg.equals("update")) {
               return update;
            }
         }

         return unknown;
      }

      boolean isClear() {
         return this == clear;
      }

      boolean isSet() {
         return this == set;
      }

      boolean isUnknown() {
         return this == unknown;
      }

      boolean isUpdate() {
         return this == update;
      }
   }

   public static enum ProblemCategory {
      academic,
      student,
      system,
      network,
      general,
      event;
   }

   private class ProblemState {
      final ProblemCategory category;
      final String displayName;
      final String description;
      final String name;
      final StringBuffer event_description;
      int count;
      long setTime;
      long totalTime;
      ProblemStatus status;
      String screenShotOnName;
      boolean screenShotOnCategory;
      String[] reportOnName;
      String[] reportOnCategory;
      boolean visible;

      ProblemState(String name, String displayName, String description) {
         this(name, displayName, description, ReportManager.ProblemCategory.general);
      }

      ProblemState(String name, String displayName, String description, ProblemCategory category) {
         this(name, displayName, description, category, (Processor)null);
      }

      ProblemState(String name, String displayName, String description, ProblemCategory category, Processor processor) {
         this.category = category;
         this.name = name;
         this.count = 0;
         this.status = ReportManager.ProblemStatus.unknown;
         this.setTime = 0L;
         this.totalTime = 0L;
         this.displayName = displayName;
         this.description = description;
         this.event_description = new StringBuffer();
      }

      public void configure(Properties properties) {
         this.visible = Utils.getBooleanOrDefault(properties, "report.problem." + this.name + ".display", true);
         this.screenShotOnName = Utils.getStringOrDefaultInSet(properties, "problem." + this.name + ".generate_screenshot", (String)null, new String[]{"true", "false"});
         this.screenShotOnCategory = Utils.getBooleanOrDefault(properties, "problem." + String.valueOf(this.category) + ".generate_screenshot", false);
         this.reportOnName = Utils.getStringsOrDefaultInSet(properties, "problem." + this.name + ".generate_report", (String[])null, ReportManager.REPORT_OPTIONS);
         this.reportOnCategory = Utils.getStringsOrDefaultInSet(properties, "problem." + String.valueOf(this.category) + ".generate_report", (String[])null, ReportManager.REPORT_OPTIONS);
      }

      public boolean isCategory(ProblemCategory category) {
         return this.category == category;
      }

      protected boolean isSet() {
         return this.status.isSet();
      }

      protected boolean isClear() {
         return this.status.isClear();
      }

      protected boolean isUnknown() {
         return this.status.isUnknown();
      }

      protected boolean isUpdate() {
         return this.status.isUpdate();
      }

      public String getName() {
         return this.name;
      }

      protected boolean updateState(ProblemStatus _status, long time, String event_description) {
         return this.updateState(_status, time, event_description, (Object[])null);
      }

      protected boolean updateState(ProblemStatus _status, long time, String event_description, Object[] args) {
         boolean notify = false;
         if (!_status.isSet() || !this.isClear() && !this.isUnknown()) {
            if (_status.isClear() && this.isSet()) {
               this.totalTime += time - this.setTime;
               notify = true;
            } else if (_status.isUnknown() && this.isUnknown()) {
               ++this.count;
               notify = true;
            } else if (_status.isUpdate() && this.isSet()) {
               ++this.count;
               notify = true;
               this.performAction(time, args);
            }
         } else {
            ++this.count;
            this.setTime = time;
            notify = true;
            this.performAction(time, args);
         }

         this.event_description.append(_status.visual());
         this.event_description.append(" ");
         this.event_description.append(ReportManager.this.sdf.format(new Date(time)));
         this.event_description.append(" ");
         this.event_description.append(event_description);
         this.event_description.append("\n");
         if (!_status.isUpdate()) {
            this.status = _status;
         }

         if (notify) {
            ReportManager.this.notifyProblem(this);
         }

         return notify;
      }

      private boolean occurred() {
         return this.count > 0;
      }

      protected void performAction(long time, Object[] args) {
         if (time - ReportManager.screenShotGlobalTime > ClientShared.MIN_INTERVAL_BETWEEN_SCREEN_SHOTS_IN_MSECS) {
            if (this.screenShotOnName != null && this.screenShotOnName.equalsIgnoreCase("true")) {
               this.screenShotAction(time, args);
            } else if (this.screenShotOnName == null && this.screenShotOnCategory) {
               this.screenShotAction(time, args);
            }

            ReportManager.screenShotGlobalTime = time;
         }

         if (this.reportOnName != null) {
            String[] var7;
            for(String reportToGenerate : var7 = this.reportOnName) {
               ReportManager.this.generateReport(reportToGenerate, ReportManager.this.invigilator.highPriorityLogArchiver, this.displayName + " problem detection action");
            }
         } else if (this.reportOnCategory != null) {
            String[] var11;
            for(String reportToGenerate : var11 = this.reportOnName) {
               ReportManager.this.generateReport(reportToGenerate, ReportManager.this.invigilator.highPriorityLogArchiver, this.category.toString() + " problem detection action");
            }
         }

      }

      private void screenShotAction(long time, Object[] args) {
         Temporal temporal_arg = null;
         if (args != null) {
            for(Object o : args) {
               if (o instanceof Temporal) {
                  temporal_arg = (Temporal)o;

                  try {
                     ReportManager.this.invigilator.annotateAndSaveImageBefore(temporal_arg.getTime());
                  } catch (IOException var10) {
                  }
               }
            }
         }

         if (temporal_arg == null) {
            ReportManager.this.invigilator.takeScreenShot(true);
         }

      }

      public String toString() {
         StringBuilder sb = new StringBuilder("");
         if (this.visible && this.occurred()) {
            sb.append("<tr class=\"problem\" id=\"");
            sb.append(this.name);
            sb.append("\"><td>");
            sb.append(this.displayName);
            sb.append("</td><td>");
            if (this.status.isSet()) {
               sb.append("<span class=\"w3-tag w3-red\">");
            } else if (this.status.isUnknown()) {
               sb.append("<span class=\"w3-tag w3-blue\">");
            } else if (this.status.isClear()) {
               sb.append("<span class=\"w3-tag w3-green\">");
            }

            sb.append(this.status);
            sb.append("</span>");
            sb.append("</td><td class=\"w3-center\">\t");
            sb.append(this.count);
            sb.append("</td><td>\t");
            if (this.status.isSet()) {
               sb.append(String.format("%.03f seconds", (double)(this.totalTime + System.currentTimeMillis() - this.setTime) / (double)1000.0F));
            } else if (this.status.isClear()) {
               sb.append(String.format("%.03f seconds", (double)this.totalTime / (double)1000.0F));
            } else if (this.status.isUnknown()) {
               if (this.totalTime > 0L) {
                  sb.append(String.format("%.03f seconds", (double)this.totalTime / (double)1000.0F));
               } else {
                  sb.append("-");
               }
            }

            sb.append("</td><td>");
            if (this.setTime > 0L) {
               sb.append((new Date(this.setTime)).toString());
            } else {
               sb.append("-");
            }

            sb.append("</td><td>");
            sb.append("<details><summary>");
            sb.append(this.description);
            sb.append("</summary>");
            if (this.status.isSet()) {
               sb.append("<span class=\"w3-tag w3-red\">");
            } else if (this.status.isUnknown()) {
               sb.append("<span class=\"w3-tag w3-blue\">");
            } else if (this.status.isClear()) {
               sb.append("<span class=\"w3-tag w3-green\">");
            }

            sb.append("<textarea wrap=\"hard\" readonly rows=\"4\" cols=\"40\">");
            sb.append(this.event_description.toString());
            sb.append("</textarea>");
            sb.append("</span>");
            sb.append("</details>");
            sb.append("</td></tr>");
         }

         return sb.toString();
      }

      public String toJSON() {
         StringBuilder sb = new StringBuilder("{ ");
         this.appendVar(sb, "name", this.name);
         sb.append(",");
         this.appendVar(sb, "category", this.category.toString());
         sb.append(",");
         this.appendVar(sb, "displayName", this.displayName);
         sb.append(",");
         this.appendVar(sb, "status", this.status.toString());
         sb.append(",");
         this.appendVar(sb, "count", (long)this.count);
         sb.append(",");
         if (this.status.isSet()) {
            this.appendVar(sb, "totalTime", this.totalTime + System.currentTimeMillis() - this.setTime);
         } else {
            this.appendVar(sb, "totalTime", this.totalTime);
         }

         sb.append(",");
         this.appendVar(sb, "setTime", this.setTime);
         sb.append(",");
         this.appendVar(sb, "description", this.description);
         sb.append(",");
         this.appendVar(sb, "event_description", this.event_description.toString());
         sb.append(" }");
         return sb.toString();
      }

      private void appendVar(StringBuilder sb, String variable, long value) {
         sb.append("\"");
         sb.append(variable);
         sb.append("\":");
         sb.append(value);
         sb.append("");
      }

      private void appendVar(StringBuilder sb, String variable, String value) {
         sb.append("\"");
         sb.append(variable);
         sb.append("\":\"");
         sb.append(value);
         sb.append("\"");
      }
   }

   private class ProblemStateWithCondition extends ProblemState {
      private Condition condition;

      ProblemStateWithCondition(String name, String displayName, String description, Condition condition) {
         this(name, displayName, description, ReportManager.ProblemCategory.general, condition);
      }

      ProblemStateWithCondition(String name, String displayName, String description, ProblemCategory category, Condition condition) {
         super(name, displayName, description, category);
         this.condition = condition;
      }

      protected boolean updateState(ProblemStatus _status, long time, String event_description) {
         return this.condition != null && this.condition.isMet(event_description) ? super.updateState(_status, time, event_description) : false;
      }
   }

   private class AnnotationProcessor implements Processor {
      private StringBuilder sb = new StringBuilder("");
      private boolean visible;
      private boolean changed;

      AnnotationProcessor() {
      }

      public boolean isChanged() {
         return this.changed;
      }

      public void resetChanged() {
         this.changed = false;
      }

      public void configure(Properties properties) {
         this.visible = Utils.getBooleanOrDefault(properties, "report.annotation.display", true);
      }

      public void process(Event e) {
         String annotation = e.get("description").toString();
         if (annotation != null && annotation.length() > 0) {
            this.sb.append(annotation);
            this.sb.append("\n");
            this.changed = true;
         }

      }

      public String toString() {
         if (!this.visible) {
            return "";
         } else {
            String annotation = this.sb.toString();
            if (annotation.length() > 0) {
               StringBuilder ssb = new StringBuilder("<h2>Notes:</h2><textarea class=\"annotation\" wrap=\"hard\" id=\"notes\" rows=\"5\" cols=\"80\">");
               ssb.append(annotation);
               ssb.append("</textarea><br/>");
               return ssb.toString();
            } else {
               return "";
            }
         }
      }

      public ObjectWithTimestamp getObservation(String item) {
         return null;
      }
   }

   private class EventProcessor implements Processor {
      private ConcurrentHashMap events = new ConcurrentHashMap();
      private boolean changed;
      private boolean visible;

      EventProcessor() {
      }

      public boolean isChanged() {
         return this.changed;
      }

      public void resetChanged() {
         this.changed = false;
      }

      public void configure(Properties properties) {
         this.visible = Utils.getBooleanOrDefault(properties, "report.event.display", true);
      }

      public void process(Event e) {
         long time = (Long)e.get("time");
         String event = (String)e.get("description");
         String state = (String)e.get("severity");
         ProblemState eventAsProblem = (ProblemState)this.events.get(event);
         if (eventAsProblem == null) {
            eventAsProblem = ReportManager.this.new ProblemState(event, event, event, ReportManager.ProblemCategory.event);
            this.events.put(event, eventAsProblem);
         }

         if (eventAsProblem.updateState(ReportManager.ProblemStatus.status(state), time, event)) {
            this.changed = true;
         }

      }

      public String toString() {
         if (this.getNumberOfEvents() != 0 && this.visible) {
            StringBuilder sb = new StringBuilder("\n<h2>Events (");
            sb.append(this.getNumberOfEvents());
            sb.append("):</h2><table");
            if (ReportManager.this.tableClass != null) {
               sb.append(" class=\"");
               sb.append(ReportManager.this.tableClass);
               sb.append("\"");
            }

            sb.append(">\n<th id=\"event\">Name</th>\t\t<th id=\"event\">State</th><th id = \"event\" class=\"w3-center\">Count</th>\t<th id=\"event\">Duration</th><th id=\"event\">Last time</th>\n");

            for(ProblemState event : this.events.values()) {
               sb.append("<tr class=\"event\" id=\"");
               sb.append(event.name);
               sb.append("\"><td>");
               sb.append(event.name);
               sb.append("</td><td>");
               if (event.status.isSet()) {
                  sb.append("<span class=\"w3-tag w3-red\">");
               } else if (event.status.isClear()) {
                  sb.append("<span class=\"w3-tag w3-green\">");
               }

               sb.append(event.status);
               sb.append("</span></td><td class=\"w3-center\">\t");
               sb.append(event.count);
               sb.append("</td><td>\t");
               if (event.status.isSet()) {
                  sb.append(String.format("%.03f seconds", (double)(event.totalTime + System.currentTimeMillis() - event.setTime) / (double)1000.0F));
               } else if (event.status.isClear()) {
                  if (event.totalTime > 0L) {
                     sb.append(String.format("%.03f seconds", (double)event.totalTime / (double)1000.0F));
                  } else {
                     sb.append("-");
                  }
               } else if (event.status.isUnknown()) {
                  if (event.totalTime > 0L) {
                     sb.append(String.format("%.03f seconds", (double)event.totalTime / (double)1000.0F));
                  } else {
                     sb.append("-");
                  }
               }

               sb.append("</td><td>");
               if (event.setTime > 0L) {
                  sb.append((new Date(event.setTime)).toString());
               } else {
                  sb.append("-");
               }

               sb.append("</td></tr>");
               sb.append("\n");
            }

            sb.append("</table>");
            sb.append("<script>");
            sb.append(SystemWebResources.sorting("event"));
            sb.append("</script>");
            return sb.toString();
         } else {
            return "";
         }
      }

      public int getNumberOfEvents() {
         return this.events.size();
      }

      public ObjectWithTimestamp getObservation(String item) {
         return new ObjectWithTimestamp((ProblemState)this.events.get(item));
      }
   }

   private class DefaultProcessor implements Processor {
      public boolean isChanged() {
         return false;
      }

      public void resetChanged() {
      }

      public void configure(Properties properties) {
      }

      public void process(Event e) {
      }

      public ObjectWithTimestamp getObservation(String item) {
         return null;
      }
   }

   public class SummaryReportServlet extends HttpServlet {
      private final ReportManager rm;

      public SummaryReportServlet(ReportManager rm) {
         this.rm = rm;
      }

      public String getMapping() {
         return "summary";
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         this.rm.invigilator.setLastServlet(this.getMapping());
         String meta = String.format("<title>%sCoMaS Summary: %s (%s)</title>", this.rm.invigilator.getTitle(), this.rm.invigilator.getName(), this.rm.invigilator.getID());
         response.addHeader("Access-Control-Allow-Origin", "*");
         response.setContentType("text/html");
         PrintWriter pw = response.getWriter();
         ServletProcessor sp = this.rm.invigilator.getServletProcessor();
         sp.updateLastAccessTime();
         pw.print(this.rm.createReport(meta, sp.checkForServletCode(), sp.refreshForServlet(), true, true, (String)null));
         response.setStatus(200);
      }
   }

   private interface Condition {
      boolean isMet(String var1);
   }

   private interface Processor {
      void process(Event var1);

      boolean isChanged();

      void resetChanged();

      void configure(Properties var1);

      ObjectWithTimestamp getObservation(String var1);
   }
}
