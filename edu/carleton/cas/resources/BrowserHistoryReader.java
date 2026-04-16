package edu.carleton.cas.resources;

import com.cogerent.utility.Temporal;
import edu.carleton.cas.constants.ClientShared;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class BrowserHistoryReader {
   public static boolean DEBUG = false;
   public static List EMPTY = new ArrayList();
   private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
   private long start;

   public BrowserHistoryReader(long start) {
      this.start = start;
   }

   public void setStart(long start) {
      this.start = start;
   }

   public long getStart() {
      return this.start;
   }

   public List getAllHistory() throws Exception {
      if (ClientShared.isMacOS()) {
         return this.getAllHistoryForMacOS(this.start);
      } else if (ClientShared.isWindowsOS()) {
         return this.getAllHistoryForWindows(this.start);
      } else {
         return ClientShared.isLinuxOS() ? this.getAllHistoryForLinux(this.start) : EMPTY;
      }
   }

   public List getAllHistoryForWindows(long start) throws Exception {
      String home = System.getProperty("user.home");
      String chrome = String.format("\\AppData\\Local\\%s\\%s\\User Data\\Default\\History", "Google", "Chrome");
      List<HistoryEntry> all = new ArrayList();
      all.addAll(this.readChromeHistory("Google Chrome", home + chrome, start));
      String edge = String.format("\\AppData\\Local\\%s\\%s\\User Data\\Default\\History", "Microsoft", "Edge");
      all.addAll(this.readChromeHistory("Microsoft Edge", home + edge, start));
      String brave = String.format("\\AppData\\Local\\%s\\%s\\User Data\\Default\\History", "BraveSoftware", "Brave-Browser");
      all.addAll(this.readChromeHistory("Brave", home + brave, start));
      String vivaldi = String.format("\\AppData\\Local\\%s\\User Data\\Default\\History", "Vivaldi");
      all.addAll(this.readChromeHistory("Vivaldi", home + vivaldi, start));
      String opera = String.format("\\AppData\\Roaming\\%s\\%s\\Default\\History", "Opera Software", "Opera Stable");
      all.addAll(this.readChromeHistory("Opera", home + opera, start));
      String firefox = String.format("\\AppData\\Roaming\\%s\\%s\\Profiles", "Mozilla", "Firefox");
      all.addAll(this.readFirefoxHistory(start, home + firefox));
      return all;
   }

   public List getAllHistoryForMacOS(long start) throws Exception {
      String home = System.getProperty("user.home");
      List<HistoryEntry> all = new ArrayList();
      all.addAll(this.readChromeHistory("Google Chrome", home + "/Library/Application Support/Google/Chrome/Default/History", start));
      all.addAll(this.readChromeHistory("Microsoft Edge", home + "/Library/Application Support/Microsoft Edge/Default/History", start));
      all.addAll(this.readChromeHistory("Brave", home + "/Library/Application Support/BraveSoftware/Brave-Browser/Default/History", start));
      all.addAll(this.readChromeHistory("Vivaldi", home + "/Library/Application Support/Vivaldi/Default/History", start));
      all.addAll(this.readChromeHistory("Opera", home + "/Library/Application Support/com.operasoftware.Opera/Default/History", start));
      all.addAll(this.readFirefoxHistory(start, home + "/Library/Application Support/Firefox/Profiles"));
      all.addAll(this.readSafariHistory(start));
      return all;
   }

   public List getAllHistoryForLinux(long start) throws Exception {
      String var10000 = System.getProperty("user.home");
      String home = var10000 + File.separator;
      List<HistoryEntry> all = new ArrayList();
      all.addAll(this.readChromeHistory("Google Chrome", home + ".config/google-chrome/Default/History", start));
      all.addAll(this.readChromeHistory("Microsoft Edge", home + ".config/microsoft-edge/Default/History", start));
      all.addAll(this.readChromeHistory("Brave", home + ".config/BraveSoftware/Brave-Browser/Default/History", start));
      all.addAll(this.readChromeHistory("Vivaldi", home + ".config/vivaldi/Default/History", start));
      all.addAll(this.readChromeHistory("Opera", home + ".config/opera-stable/History", start));
      all.addAll(this.readFirefoxHistory(start, home + ".mozilla/firefox"));
      all.addAll(this.readFirefoxHistory(start, home + "snap/firefox/common/.mozilla/firefox"));
      return all;
   }

   private List readChromeHistory(String browserName, String dbPath, long start) {
      List<HistoryEntry> list = new ArrayList();
      String copyDb = null;

      try {
         copyDb = this.copyDb(browserName, dbPath);
      } catch (IOException e) {
         if (DEBUG) {
            System.err.printf("Could not copy %s History: %s\n", browserName, e.getMessage());
         }

         return list;
      }

      String url = "jdbc:sqlite:" + copyDb;
      Connection conn = null;

      try {
         conn = DriverManager.getConnection(url);
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT url, title, last_visit_time FROM urls ORDER BY last_visit_time DESC");
         Date startTime = new Date(start);

         while(rs.next()) {
            String title = rs.getString("title");
            String link = rs.getString("url");
            long lastVisit = rs.getLong("last_visit_time");
            Date visitTime = this.chromeTimestampToDate(lastVisit);
            if (visitTime.after(startTime)) {
               list.add(new HistoryEntry(browserName, title, link, visitTime));
            }
         }
      } catch (Exception e) {
         if (DEBUG) {
            System.err.println("Error reading " + browserName + " history: " + e.getMessage());
         }
      } finally {
         if (conn != null) {
            try {
               conn.close();
            } catch (SQLException var25) {
            }
         }

         if (copyDb != null) {
            (new File(copyDb)).delete();
         }

      }

      return list;
   }

   private String copyDb(String browserName, String dbPath) throws IOException {
      Path source = FileSystems.getDefault().getPath(dbPath);
      Path target = FileSystems.getDefault().getPath(System.getProperty("user.home"), "History.db");
      new File(dbPath);
      File dbPathCopy = Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING).toFile();
      return dbPathCopy.getAbsolutePath();
   }

   private List readSafariHistory(long start) {
      List<HistoryEntry> list = new ArrayList();
      String copyDb = null;

      try {
         String dbPath = System.getProperty("user.home") + "/Library/Safari/History.db";
         copyDb = this.copyDb("Safari", dbPath);
      } catch (IOException e) {
         if (DEBUG) {
            System.err.println("Could not copy Safari History.db: " + e.getMessage());
         }

         return list;
      }

      String url = "jdbc:sqlite:" + copyDb;
      Date startTime = new Date(start);
      Connection conn = null;

      try {
         conn = DriverManager.getConnection(url);
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT history_items.url, history_visits.title, history_visits.visit_time FROM history_items JOIN history_visits ON history_items.id = history_visits.history_item ORDER BY visit_time DESC");

         while(rs.next()) {
            String title = rs.getString("title");
            String link = rs.getString("url");
            double visitTimeSeconds = rs.getDouble("visit_time");
            Date visitTime = this.safariTimestampToDate(visitTimeSeconds);
            if (visitTime.after(startTime)) {
               list.add(new HistoryEntry("Safari", title, link, visitTime));
            }
         }
      } catch (Exception e) {
         if (DEBUG) {
            System.err.println("Error reading Safari history: " + e.getMessage());
         }
      } finally {
         if (conn != null) {
            try {
               conn.close();
            } catch (SQLException var23) {
            }
         }

         if (copyDb != null) {
            (new File(copyDb)).delete();
         }

      }

      return list;
   }

   private List readFirefoxHistory(long start, String appDir) {
      List<HistoryEntry> list = new ArrayList();
      File profilesDir = new File(appDir);
      if (!profilesDir.exists()) {
         return list;
      } else {
         File[] profileDirs = profilesDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
               return name.endsWith(".default") || name.endsWith(".default-release");
            }
         });
         if (profileDirs == null) {
            return list;
         } else {
            Date startTime = new Date(start);

            for(File profile : profileDirs) {
               File dbPath = new File(profile, "places.sqlite");
               String copyDb = null;

               String url;
               try {
                  copyDb = this.copyDb("Firefox", dbPath.getAbsolutePath());
                  url = "jdbc:sqlite:" + copyDb;
               } catch (IOException e1) {
                  if (DEBUG) {
                     System.err.printf("Could not copy %s History: %s\n", "Firefox", e1.getMessage());
                  }
                  continue;
               }

               Connection conn = null;

               try {
                  conn = DriverManager.getConnection(url);
                  Statement stmt = conn.createStatement();
                  ResultSet rs = stmt.executeQuery("SELECT moz_places.url, moz_places.title, moz_historyvisits.visit_date FROM moz_places JOIN moz_historyvisits ON moz_places.id = moz_historyvisits.place_id ORDER BY visit_date DESC");

                  while(rs.next()) {
                     String title = rs.getString("title");
                     String link = rs.getString("url");
                     long visitTimeMicros = rs.getLong("visit_date");
                     Date visitTime = new Date(visitTimeMicros / 1000L);
                     if (visitTime.after(startTime)) {
                        list.add(new HistoryEntry("Firefox", title, link, visitTime));
                     }
                  }
               } catch (Exception e) {
                  if (DEBUG) {
                     PrintStream var10000 = System.err;
                     String var10001 = profile.getName();
                     var10000.println("Error reading Firefox history from profile " + var10001 + ": " + e.getMessage());
                  }
               } finally {
                  if (conn != null) {
                     try {
                        conn.close();
                     } catch (SQLException var31) {
                     }
                  }

                  if (copyDb != null) {
                     (new File(copyDb)).delete();
                  }

               }
            }

            return list;
         }
      }
   }

   private Date chromeTimestampToDate(long timestamp) {
      long epochMillis = timestamp / 1000L - 11644473600000L;
      return new Date(epochMillis);
   }

   private Date safariTimestampToDate(double timestamp) {
      long epochMillis = (long)((timestamp + (double)9.783072E8F) * (double)1000.0F);
      return new Date(epochMillis);
   }

   public static void main(String[] args) throws Exception {
      DEBUG = true;
      BrowserHistoryReader bhr = new BrowserHistoryReader(0L);
      Timer timer = new Timer();
      timer.scheduleAtFixedRate(new BrowserHistory(bhr), 0L, 60000L);
   }

   public class HistoryEntry implements Comparable, Temporal {
      private final String browser;
      private final String title;
      private final String url;
      private final Date visitTime;
      private boolean allowed;

      public HistoryEntry(String browser, String title, String url, Date visitTime) {
         this(browser, title, url, visitTime, false);
      }

      public HistoryEntry(String browser, String title, String url, Date visitTime, boolean allowed) {
         this.browser = browser;
         this.title = title;
         this.url = url;
         this.visitTime = visitTime;
         this.allowed = allowed;
      }

      public String getBrowser() {
         return this.browser;
      }

      public String getTitle() {
         return this.title;
      }

      public String getUrl() {
         return this.url;
      }

      public Date getVisitTime() {
         return this.visitTime;
      }

      public boolean isAllowed() {
         return this.allowed;
      }

      public void setAllowed(boolean allowed) {
         this.allowed = allowed;
      }

      public int compareTo(HistoryEntry o) {
         if (o.getVisitTime().after(this.visitTime)) {
            return 1;
         } else {
            return o.getVisitTime().before(this.visitTime) ? -1 : 0;
         }
      }

      public long getTime() {
         return this.visitTime.getTime();
      }

      public String toString() {
         return this.url;
      }
   }

   public static class BrowserHistory extends TimerTask {
      private BrowserHistoryReader bhr;

      BrowserHistory(BrowserHistoryReader bhr) {
         this.bhr = bhr;
         bhr.setStart(System.currentTimeMillis());
      }

      public void run() {
         System.out.printf("====History at %s from %s====\n", BrowserHistoryReader.sdf.format(new Date(System.currentTimeMillis())), BrowserHistoryReader.sdf.format(new Date(this.bhr.getStart())));

         try {
            for(HistoryEntry e : this.bhr.getAllHistory()) {
               System.out.printf("[%s] %s (%s) @ %s%n", e.getBrowser(), e.getTitle(), e.getUrl(), BrowserHistoryReader.sdf.format(e.getVisitTime()));
            }
         } catch (Exception var3) {
         }

      }
   }
}
