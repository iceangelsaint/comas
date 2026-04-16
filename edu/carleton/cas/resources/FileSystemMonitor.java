package edu.carleton.cas.resources;

import edu.carleton.cas.background.LogArchiver;
import edu.carleton.cas.background.timers.TimerService;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.jetty.embedded.ProgressIndicator;
import edu.carleton.cas.jetty.embedded.ProgressServlet;
import edu.carleton.cas.utility.CountDownLatchNotifier;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.Thread.State;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class FileSystemMonitor extends AbstractResourceMonitor implements Thread.UncaughtExceptionHandler {
   private static final FileFilter DIRECTORY_FILE_FILTER = new FileFilter() {
      public boolean accept(File file) {
         return file.isDirectory() && file.canRead();
      }
   };
   public static final int MAX_DEPTH = 6;
   private WatchService watchService;
   private TimerTask task;
   private long exam_directory_events = 0L;
   private int numberOfTimesEnded;
   protected Thread fileSystemWatcherThread;
   private LogArchiver logArchiver;
   private File[] examFiles;
   private File[] resourceFiles;
   private String studentName;
   private String course;
   private String activity;
   private String baseDirectory;
   private String examDirectory;
   private String resourcesDirectory;
   private String desktopDirectory;
   private String comasDirectory;
   private String courseDirectory;
   private String screensDirectory;
   private String archivesDirectory;
   private String toolsDirectory;
   private AtomicBoolean okay;
   private AtomicBoolean insufficientActivity;
   private ArrayList foldersForMonitoring;
   private HashSet dontCareFiles;
   private HashSet unsanctionedFiles;
   private int maxDepth;
   private int totalKeys;
   private boolean log;
   private CountDownLatchNotifier latch;
   private int progress;
   private String supportMessage;

   public FileSystemMonitor(LogArchiver logArchiver, String studentName, String course, String activity, ArrayList foldersForMonitoring, int maxDepth, boolean log, String supportMessage) {
      super("fileSystemMonitor");
      this.studentName = studentName;
      this.course = course;
      this.activity = activity;
      this.numberOfTimesEnded = 0;
      this.desktopDirectory = ClientShared.getDesktopDirectory();
      this.comasDirectory = ClientShared.DIR.replace(File.separator + File.separator, File.separator);
      this.courseDirectory = ClientShared.getCourseDirectory(course).replace(File.separator + File.separator, File.separator);
      this.baseDirectory = ClientShared.getBaseDirectory(course, activity).replace(File.separator + File.separator, File.separator);
      this.examDirectory = (this.baseDirectory + "exam").replace(File.separator + File.separator, File.separator);
      this.resourcesDirectory = (this.baseDirectory + "resources").replace(File.separator + File.separator, File.separator);
      this.screensDirectory = (this.baseDirectory + "screens").replace(File.separator + File.separator, File.separator);
      this.archivesDirectory = (this.baseDirectory + "archives").replace(File.separator + File.separator, File.separator);
      this.toolsDirectory = (this.baseDirectory + "tools").replace(File.separator + File.separator, File.separator);
      this.supportMessage = supportMessage;
      this.logArchiver = logArchiver;
      this.okay = new AtomicBoolean(true);
      this.insufficientActivity = new AtomicBoolean(false);
      this.foldersForMonitoring = foldersForMonitoring;
      this.maxDepth = maxDepth;
      this.totalKeys = 0;
      this.log = log;
      this.dontCareFiles = new HashSet();
      this.unsanctionedFiles = new HashSet();
   }

   public void restart() {
      if (this.numberOfTimesEnded > ClientShared.MAX_NUMBER_OF_FILE_WATCHING_FAILURES) {
         this.logArchiver.put(Level.WARNING, "File monitoring restart limit exceeded (" + ClientShared.MAX_NUMBER_OF_FILE_WATCHING_FAILURES + ")");
      } else {
         this.close();
         this.open();
      }
   }

   public synchronized void open() {
      try {
         this.watchService = FileSystems.getDefault().newWatchService();
         edu.carleton.cas.logging.Logger.log(Level.FINE, "", "Directory watching setup.");
      } catch (IOException var9) {
         edu.carleton.cas.logging.Logger.log(Level.SEVERE, "", "Could not start watch service.");
      }

      File examDirectoryFile = new File(this.baseDirectory, "exam");
      if (examDirectoryFile.exists()) {
         this.examFiles = examDirectoryFile.listFiles();
      } else {
         this.examFiles = null;
      }

      File resourcesDirectoryFile = new File(this.baseDirectory, "resources");
      if (resourcesDirectoryFile.exists()) {
         this.resourceFiles = resourcesDirectoryFile.listFiles();
      } else {
         this.resourceFiles = null;
      }

      File desktopDirectoryFile = new File(this.desktopDirectory);
      this.setupDirectoryMonitoring(desktopDirectoryFile.getParent(), desktopDirectoryFile.getName());
      this.setupDirectoryMonitoring(this.desktopDirectory, "CoMaS");
      this.setupDirectoryMonitoring(this.comasDirectory, this.course);
      this.setupDirectoryMonitoring(this.courseDirectory, this.activity);
      if (examDirectoryFile.exists()) {
         this.setupDirectoryMonitoring(this.baseDirectory, "exam");
      }

      this.setupDirectoryMonitoring(this.baseDirectory, "screens");
      this.setupDirectoryMonitoring(this.baseDirectory, "logs");
      this.setupDirectoryMonitoring(this.baseDirectory, "tools");
      if (this.resourceFiles != null) {
         this.setupDirectoryMonitoring(this.baseDirectory, "resources");
      }

      if (ClientShared.AUTO_ARCHIVE) {
         this.setupDirectoryMonitoring(this.baseDirectory, "archives");
      }

      this.runWatcherThread();
      if (ClientShared.FREQUENCY_TO_CHECK_EXAM_DIRECTORY > 0 && this.task == null) {
         this.task = new TimerTask() {
            public void run() {
               synchronized(FileSystemMonitor.this.insufficientActivity) {
                  try {
                     if (FileSystemMonitor.this.exam_directory_events < (long)ClientShared.MIN_EVENTS_IN_EXAM_DIRECTORY) {
                        FileSystemMonitor.this.insufficientActivity.set(true);
                        String description = "No activity in " + FileSystemMonitor.this.examDirectory + " for " + ClientShared.FREQUENCY_TO_CHECK_EXAM_DIRECTORY / '\uea60' + " mins.";
                        FileSystemMonitor.this.notifyListeners("PROBLEM", description);
                        FileSystemMonitor.this.logArchiver.put(edu.carleton.cas.logging.Level.LOGGED, description, new Object[]{"problem", "no_activity", "set"});
                     }
                  } catch (Exception var3) {
                  }

                  FileSystemMonitor.this.exam_directory_events = 0L;
               }
            }
         };

         try {
            TimerService.scheduleAtFixedRate(this.task, (long)ClientShared.FREQUENCY_TO_CHECK_EXAM_DIRECTORY, (long)ClientShared.FREQUENCY_TO_CHECK_EXAM_DIRECTORY);
         } catch (IllegalStateException var8) {
         }
      }

      if (this.foldersForMonitoring != null) {
         for(String folder : this.foldersForMonitoring) {
            File f = new File(folder);
            String p = f.getParent();
            this.progress = ProgressServlet.getSingleton().getProgress();
            if (!this.isSpecialDirectory(f.getName(), p)) {
               this.processSubdirectory(f.getName(), p, 1);
            }
         }
      }

   }

   private void runWatcherThread() {
      this.fileSystemWatcherThread = new Thread() {
         public void run() {
            boolean var10 = false;

            try {
               var10 = true;
               FileSystemMonitor.this.okay.set(true);

               while(true) {
                  if (!FileSystemMonitor.this.okay.get()) {
                     var10 = false;
                     break;
                  }

                  if (FileSystemMonitor.this.isStopped()) {
                     var10 = false;
                     break;
                  }

                  try {
                     WatchKey key = FileSystemMonitor.this.watchService.take();

                     for(WatchEvent evnt : key.pollEvents()) {
                        Level var10000 = Level.FINE;
                        String var10002 = String.valueOf(evnt.kind());
                        edu.carleton.cas.logging.Logger.log(var10000, "", var10002 + " " + String.valueOf(evnt.context()) + " on " + String.valueOf(key.watchable()));
                        if (evnt.kind() == StandardWatchEventKinds.OVERFLOW) {
                           FileSystemMonitor.this.processOverflowWatchEvent(key, evnt);
                        } else {
                           FileSystemMonitor.this.checkForExamFolderActivity(key, evnt);
                           if (evnt.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                              FileSystemMonitor.this.processDeleteWatchEvent(key, evnt);
                           } else if (evnt.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                              FileSystemMonitor.this.processCreateWatchEvent(key, evnt);
                           } else if (evnt.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                              FileSystemMonitor.this.processModifyWatchEvent(key, evnt);
                           }
                        }
                     }

                     if (!key.reset()) {
                        if (FileSystemMonitor.this.isLoggable(key.watchable().toString())) {
                           FileSystemMonitor.this.logArchiver.put(Level.SEVERE, "Watch service no longer has access to " + String.valueOf(key.watchable()));
                           FileSystemMonitor var18 = FileSystemMonitor.this;
                           String var19 = String.valueOf(key.watchable());
                           var18.notifyListeners("ALERT", "CoMaS directory access problem for \n" + var19 + ".\n\n" + FileSystemMonitor.this.supportMessage);
                        }

                        key.cancel();
                     }
                  } catch (InterruptedException var11) {
                  } catch (ClosedWatchServiceException var12) {
                     FileSystemMonitor.this.okay.set(false);
                  } catch (NullPointerException var13) {
                     FileSystemMonitor.this.okay.set(false);
                  }
               }
            } finally {
               if (var10) {
                  ++FileSystemMonitor.this.numberOfTimesEnded;
                  FileSystemMonitor.this.notifyListeners("CLOSE", "file system monitoring ended");
                  if (FileSystemMonitor.this.latch != null) {
                     FileSystemMonitor.this.latch.countDown("File monitor stopped: " + FileSystemMonitor.this.getTotalKeys());
                     FileSystemMonitor.this.latch = null;
                  }

               }
            }

            ++FileSystemMonitor.this.numberOfTimesEnded;
            FileSystemMonitor.this.notifyListeners("CLOSE", "file system monitoring ended");
            if (FileSystemMonitor.this.latch != null) {
               FileSystemMonitor.this.latch.countDown("File monitor stopped: " + FileSystemMonitor.this.getTotalKeys());
               FileSystemMonitor.this.latch = null;
            }

         }
      };
      this.fileSystemWatcherThread.setName("file system watcher");
      this.fileSystemWatcherThread.setUncaughtExceptionHandler(this);
      this.fileSystemWatcherThread.start();
   }

   public boolean isLoggable(String folder) {
      if (this.log) {
         return true;
      } else if (folder.equals(this.desktopDirectory)) {
         return true;
      } else if (folder.equals(this.comasDirectory)) {
         return true;
      } else if (folder.equals(this.courseDirectory)) {
         return true;
      } else if (folder.equals(this.baseDirectory)) {
         return true;
      } else if (folder.equals(this.examDirectory)) {
         return true;
      } else if (folder.equals(this.resourcesDirectory)) {
         return true;
      } else if (folder.equals(this.screensDirectory)) {
         return true;
      } else if (folder.equals(this.toolsDirectory)) {
         return true;
      } else if (folder.equals(this.archivesDirectory)) {
         return true;
      } else {
         return this.foldersForMonitoring.contains(folder);
      }
   }

   public int getTotalKeys() {
      return this.totalKeys;
   }

   public synchronized void stop(CountDownLatchNotifier latch) {
      if (this.task != null) {
         this.task.cancel();
      }

      this.latch = latch;
      this.close();
   }

   public synchronized void close() {
      if (this.watchService != null) {
         this.okay.set(false);
         if (this.fileSystemWatcherThread != null && (this.fileSystemWatcherThread.getState() == State.WAITING || this.fileSystemWatcherThread.getState() == State.TIMED_WAITING || this.fileSystemWatcherThread.getState() == State.BLOCKED)) {
            this.fileSystemWatcherThread.interrupt();
         }

         this.watchService = null;
      }

   }

   private String fileEvent(WatchKey key, WatchEvent event) {
      String var10000 = String.valueOf(key.watchable());
      return var10000 + File.separator + String.valueOf(event.context());
   }

   private void checkForExamFolderActivity(WatchKey key, WatchEvent event) {
      if (key.watchable().toString().equals(this.examDirectory) && !((Path)event.context()).toString().equals(".comas")) {
         synchronized(this.insufficientActivity) {
            ++this.exam_directory_events;
            if (this.insufficientActivity.get() && this.exam_directory_events >= (long)ClientShared.MIN_EVENTS_IN_EXAM_DIRECTORY) {
               this.logArchiver.put(edu.carleton.cas.logging.Level.LOGGED, "Activity detected in exam folder for " + String.valueOf(event.context()), new Object[]{"problem", "no_activity", "clear"});
               this.insufficientActivity.set(false);
            }
         }
      }

   }

   private void processOverflowWatchEvent(WatchKey key, WatchEvent event) {
      LogArchiver var10000 = this.logArchiver;
      Level var10001 = edu.carleton.cas.logging.Level.WARNING;
      String var10002 = String.valueOf(event.kind());
      var10000.put(var10001, var10002 + " of " + event.count() + " events on " + String.valueOf(key.watchable()));
   }

   private void processDeleteWatchEvent(WatchKey key, WatchEvent event) {
      Path filename = (Path)event.context();
      String watchedFolder = key.watchable().toString();
      if (this.specialFile(filename, watchedFolder)) {
         this.notifyListeners("ALERT", "Deleted a CoMaS exam file: " + String.valueOf(filename.getFileName()));
      } else if (this.fileWeCareAbout(filename)) {
         String problem;
         if (watchedFolder.endsWith("screens")) {
            problem = "file_deletion_screens";
         } else if (watchedFolder.endsWith("archives")) {
            problem = "file_deletion_archives";
         } else if (watchedFolder.endsWith("tools")) {
            problem = "file_deletion_tools";
         } else {
            problem = "file_deletion";
         }

         this.logArchiver.put(edu.carleton.cas.logging.Level.LOGGED, String.valueOf(event.kind()) + " " + String.valueOf(filename.getFileName()) + " from " + String.valueOf(key.watchable()), new Object[]{"problem", problem, "unknown"});
      }

   }

   private void processCreateWatchEvent(WatchKey key, WatchEvent event) {
      Path filename = (Path)event.context();
      String watchedFolder = key.watchable().toString();
      if (watchedFolder.equals(this.resourcesDirectory)) {
         LogArchiver var10000 = this.logArchiver;
         Level var10001 = edu.carleton.cas.logging.Level.LOGGED;
         String var10002 = String.valueOf(event.kind());
         var10000.put(var10001, var10002 + " " + String.valueOf(filename.getFileName()) + " in " + String.valueOf(key.watchable()));
         (new File(watchedFolder, filename.getFileName().toString())).delete();
      } else if (watchedFolder.equals(this.screensDirectory)) {
         String name = filename.toString();
         if (!name.startsWith(this.studentName) || !name.endsWith(".jpg")) {
            this.processUnsanctionedCreationOfFileOrFolder(filename, key);
         }
      } else if (watchedFolder.equals(this.archivesDirectory)) {
         String name = filename.toString();
         if (!name.startsWith(this.studentName) || !name.endsWith(".zip")) {
            this.processUnsanctionedCreationOfFileOrFolder(filename, key);
         }
      } else if (watchedFolder.equals(this.toolsDirectory)) {
         String name = filename.toString();
         if (!name.endsWith(".html")) {
            this.processUnsanctionedCreationOfFileOrFolder(filename, key);
         }
      } else {
         String subdirectory = filename.getFileName().toString();
         if (this.isSpecialDirectory(subdirectory, watchedFolder)) {
            this.processSubdirectory(subdirectory, watchedFolder, 1);
         } else {
            this.processUnsanctionedCreationOfFileOrFolder(filename, key);
         }
      }

   }

   private void processModifyWatchEvent(WatchKey key, WatchEvent event) {
   }

   public String[] unsanctionedFiles() {
      synchronized(this.unsanctionedFiles) {
         return this.unsanctionedFiles.isEmpty() ? new String[0] : (String[])this.unsanctionedFiles.toArray(new String[this.unsanctionedFiles.size()]);
      }
   }

   private void processUnsanctionedCreationOfFileOrFolder(Path filename, WatchKey key) {
      if (AbstractFileTask.isFileOfInterest(filename.getFileName().toString())) {
         String var10000 = String.valueOf(key.watchable());
         String nameOfFile = var10000 + File.separator + String.valueOf(filename.getFileName());
         synchronized(this.unsanctionedFiles) {
            this.unsanctionedFiles.add(nameOfFile);
         }

         this.logArchiver.put(edu.carleton.cas.logging.Level.LOGGED, String.valueOf(StandardWatchEventKinds.ENTRY_CREATE) + " " + String.valueOf(filename.getFileName()) + " in " + String.valueOf(key.watchable()), new Object[]{"problem", "unsanctioned_files", "unknown", "file:" + nameOfFile});
      }

   }

   private WatchKey setupDirectoryMonitoring(String base, String dir) {
      return this.setupDirectoryMonitoring(base, dir, true);
   }

   private WatchKey setupDirectoryMonitoring(String base, String dir, boolean logException) {
      try {
         Path path = FileSystems.getDefault().getPath(base, dir);
         WatchKey key = path.register(this.watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
         edu.carleton.cas.logging.Logger.log(Level.FINE, "", "Monitoring " + base + File.separator + dir);
         ++this.totalKeys;
         return key;
      } catch (ClosedWatchServiceException | SecurityException | IOException e) {
         if (logException) {
            this.logArchiver.put(Level.SEVERE, "Could not monitor " + base + File.separator + dir + ":" + ((Exception)e).getMessage());
         }

         return null;
      }
   }

   private boolean specialFile(Path file, String dir) {
      if (dir.equals(this.examDirectory) && this.examFiles != null) {
         String name = file.getFileName().toString();

         File[] var7;
         for(File afile : var7 = this.examFiles) {
            if (afile.getName().equals(name)) {
               return true;
            }
         }
      }

      if (dir.equals(this.resourcesDirectory) && this.resourceFiles != null) {
         String name = file.getFileName().toString();

         File[] var12;
         for(File afile : var12 = this.resourceFiles) {
            if (afile.getName().equals(name)) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean isStopped() {
      return this.watchService == null;
   }

   public void addDontCareFile(File f) {
      synchronized(this.dontCareFiles) {
         this.dontCareFiles.add(f.getName());
      }
   }

   public boolean fileWeCareAbout(Path name) {
      synchronized(this.dontCareFiles) {
         if (name.endsWith("arc.zip")) {
            return false;
         } else {
            return this.dontCareFiles.contains(name.toString()) ? false : AbstractFileTask.isFileOfInterest(name.toString());
         }
      }
   }

   private boolean isSpecialDirectory(String file, String dir) {
      if (this.desktopDirectory.startsWith(dir) && file.equals("CoMaS")) {
         return true;
      } else if (this.comasDirectory.startsWith(dir) && file.equals(this.course)) {
         return true;
      } else if (this.courseDirectory.startsWith(dir) && file.equals(this.activity)) {
         return true;
      } else {
         if (this.baseDirectory.startsWith(dir)) {
            if (file.equals("archives")) {
               return true;
            }

            if (file.equals("exam")) {
               return true;
            }

            if (file.equals("resources")) {
               return true;
            }

            if (file.equals("logs")) {
               return true;
            }

            if (file.equals("tools")) {
               return true;
            }

            if (file.equals("screens")) {
               return true;
            }
         }

         return false;
      }
   }

   private void processSubdirectory(String subdir, String dir, int depth) {
      if (depth <= this.maxDepth) {
         ProgressIndicator pi = ProgressServlet.getSingleton();
         WatchKey newKey = this.setupDirectoryMonitoring(dir, subdir, depth == 1);
         File newWatchedSubdirectory = new File(dir, subdir);
         File[] otherDirectories = newWatchedSubdirectory.listFiles(DIRECTORY_FILE_FILTER);
         if (otherDirectories != null) {
            for(File otherDirectory : otherDirectories) {
               this.processSubdirectory(otherDirectory.getName(), newWatchedSubdirectory.getAbsolutePath(), depth + 1);
            }
         }

         if (depth == 1) {
            if (newKey != null) {
               LogArchiver var10000 = this.logArchiver;
               Level var10001 = Level.INFO;
               String var10002 = String.valueOf(newKey.watchable());
               var10000.put(var10001, "Watch service can now access " + var10002 + " (" + this.getTotalKeys() + " folders)");
            } else {
               this.logArchiver.put(Level.INFO, "Watch service monitoring for " + subdir + " of " + dir + " failed");
            }
         }

         int newProgress = this.progress + (this.maxDepth - depth) * 10 / this.maxDepth;
         if (pi.getProgress() < newProgress) {
            pi.setProgress(newProgress);
         }

      }
   }

   public void uncaughtException(Thread t, Throwable e) {
      if (t == this.fileSystemWatcherThread) {
         LogArchiver var10000 = this.logArchiver;
         Level var10001 = Level.WARNING;
         String var10002 = t.getName();
         var10000.put(var10001, "Watch service " + var10002 + " ended. Cause: " + String.valueOf(e));
         if (this.numberOfTimesEnded < ClientShared.MAX_FAILURES) {
            this.runWatcherThread();
         }
      } else {
         LogArchiver var3 = this.logArchiver;
         Level var4 = edu.carleton.cas.logging.Level.NOTED;
         String var5 = t.getName();
         var3.put(var4, "Abnormal " + var5 + " termination. Cause: " + String.valueOf(e));
      }

   }
}
