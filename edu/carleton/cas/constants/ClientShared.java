package edu.carleton.cas.constants;

import edu.carleton.cas.file.Utils;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.resources.ServerConfiguration;
import edu.carleton.cas.utility.WindowsRegistry;
import java.io.File;
import java.io.FileFilter;
import java.util.Properties;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientShared {
   public static final String ACTUAL_VERSION_OF_CLIENT = "0.8.75";
   public static final String LOCATION_STATE = "LOCATION";
   public static final String START_STATE = "START";
   public static final String END_STATE = "END";
   public static final String DURATION_STATE = "DURATION";
   public static final String STOP_STATE = "stop";
   public static final String ID_STATE = "ID";
   public static final String PASSWORD_STATE = "PASSWORD";
   public static final String PASSCODE_STATE = "PASSCODE";
   public static final String EMAIL_STATE = "EMAIL";
   public static final String COURSE_STATE = "COURSE";
   public static final String ACTIVITY_STATE = "ACTIVITY";
   public static final String SESSION_STATE = "SESSION";
   public static final String ACCESS_TIME = "ACCESS";
   public static final String CURRENT_TIME = "CURRENT_TIME";
   public static final String ACCESS_TOKEN = "TOKEN";
   public static final String _USEFUL_PROPERTY = "Cookie";
   public static final String STUDENT_DOT_DIRECTORY_DOT = "student.directory.";
   public static final String _MSECS = "_MSECS";
   public static final String START_STATE_MSECS = "START_MSECS";
   public static final String END_STATE_MSECS = "END_MSECS";
   public static final String SESSION_DOT_INITIALIZED = "session.initialized";
   public static final String SESSION_DOT_ENDED = "session.ended";
   public static final String SESSION_DOT_STORE_DOT = "session.store.";
   public static final String TRUE = "true";
   public static final String FALSE = "false";
   public static final String YES = "yes";
   public static final String NO = "no";
   public static final String OS_NAME = "os.name";
   public static final String USER_NAME = "user.name";
   public static final String HOME_DIR = "user.home";
   public static final String TEMP_DIR = "java.io.tmpdir";
   public static final String CURRENT_WORKING_DIRECTORY = "user.dir";
   public static String STUDENT_FIRST_NAME;
   public static String STUDENT_LAST_NAME;
   public static String STUDENT_ID;
   public static String STUDENT_COURSE;
   public static String STUDENT_ACTIVITY;
   public static final String DOT_COMAS = ".comas";
   public static final String DOT_ZIP = ".zip";
   public static final String EXAM = "exam";
   public static final String EXAM_DOT_ZIP = "exam.zip";
   public static final String TOOLS = "tools";
   public static final String TOOLS_DOT_ZIP = "tools.zip";
   public static final String RESOURCES = "resources";
   public static final String RESOURCES_DOT_ZIP = "resources.zip";
   public static final String DOT_HOST = ".hostname";
   public static final String DOT_DIRECTORY = ".directory";
   public static final String DOT_EXAM = ".exam";
   public static final String DOT_UPLOAD = ".upload";
   public static final String DOT_LOG = ".log";
   public static final String DOT_VIDEO = ".video";
   public static final String DOT_CMS = ".cms";
   public static final String DOT_WEBSOCKET = ".websocket";
   public static final String DOT_PROTOCOL = ".protocol";
   public static final String DOT_PORT = ".port";
   public static final String COURSES = "courses";
   public static boolean LOOK_FOR_SERVICES = false;
   public static final String BONJOUR_EXAM = "EXAM";
   public static final String BONJOUR_FILE_UPLOAD = "EFUP";
   public static final String BONJOUR_DIRECTORY = "EDIR";
   public static final String BONJOUR_LOG = "ELOG";
   public static final String BONJOUR_VIDEO = "EVID";
   public static final String BONJOUR_CMS = "ECMS";
   public static final String FIND_SERVICES = "find_services";
   public static String SERVER_CHOSEN = null;
   public static String COMPANY_DOMAIN = "www.cogerent.com";
   public static String COMPANY_HOST = "comas.cogerent.com";
   public static String DIRECTORY_HOST;
   public static String EXAM_HOST;
   public static String UPLOAD_HOST;
   public static String LOG_HOST;
   public static String MARKING_HOST;
   public static String VIDEO_HOST;
   public static String CMS_HOST;
   public static String WEBSOCKET_HOST;
   public static String COMAS_DOT;
   public static String COMAS_DOT_INI;
   public static String COMAS_DOT_XML;
   public static String COMAS_DOT_JAR_FORMAT;
   public static String THIS_COMAS_JAR_FILE;
   public static String COMAS_JAR_PATTERN;
   public static String EXAM_CONFIGURATION_FILE;
   public static String LOCAL_EXAM_CONFIGURATION_FILE;
   public static final String HTTPS = "https";
   public static final String HTTP = "http";
   public static final String WS = "ws";
   public static final String WSS = "wss";
   public static String PROTOCOL;
   public static String WS_PROTOCOL;
   public static final String HTTP_PORT = "8080";
   public static final String HTTPS_PORT = "8443";
   public static String PORT;
   public static final int DEFAULT_CONNECTION_TIMEOUT_IN_MSECS = 5000;
   public static int CONNECTION_TIMEOUT_IN_MSECS;
   public static final String OK = "{\"OK\"}";
   public static final String STOPPING = "{\"STOPPING\"}";
   public static final String STOPPED = "{\"STOPPED\"}";
   public static final String MONITOR = "{\"MONITOR\"}";
   public static final String DOES_NOT_EXIST = "{\"DOES NOT EXIST\"}";
   public static final String IS_RESTRICTED = "{\"IS RESTRICTED\"}";
   public static final String ILLEGAL_VERSION = "{\"ILLEGAL VERSION\"}";
   public static final String EMPTY = "{}";
   public static String LOG_PATH;
   public static String VIDEO_PATH;
   public static String UPLOAD_PATH;
   public static String REGISTER_PATH;
   public static final String BASE_REGISTRATION_SERVICE = "/CoMaS-Directory/rest/directory/";
   public static final String BASE_UPLOAD_SERVICE = "/CoMaS-FileUpload/rest/file/";
   public static final String BASE_LOG_SERVICE = "/CoMaS-Log/rest/logger/";
   public static final String BASE_VIDEO_SERVICE = "/CoMaS-Video/rest/logger/";
   public static final String BASE_CMS_SERVICE = "/CMS/rest/";
   public static final String BASE_EXAM_SERVICE = "/Exam/rest/exam/";
   public static final String BASE_WEBSOCKET_SERVICE = "/WebSocket/";
   public static final String REGISTRATION_SERVICE;
   public static final String UPLOAD_SERVICE;
   public static final String LOG_SERVICE;
   public static final String VIDEO_SERVICE;
   public static final String CMS_SERVICE = "/CMS/rest/";
   public static final String EXAM_SERVICE = "/Exam/rest/exam/";
   public static final String WEBSOCKET_SERVICE = "/WebSocket/";
   public static final String COMMAND_AND_CONTROL_SERVICE = "/WebSocket/channel/";
   public static final String CLIENT_LOGIN_DOT_INI = "client.ini";
   public static final String CLIENT_LOGIN_CONFIGURATION_FILE = "/CMS/rest/exam/client.ini";
   public static String CLIENT_LOGIN_CONFIGURATION_URL;
   public static final String SYSTEM_LOGIN_DOT_INI = "login.ini";
   public static final String SYSTEM_LOGIN_CONFIGURATION_FILE = "/CMS/rest/exam/login.ini";
   public static String SYSTEM_LOGIN_CONFIGURATION_URL;
   public static String BASE_LOGIN;
   public static String BASE_LOG;
   public static String BASE_UPLOAD;
   public static String BASE_VIDEO;
   public static String BASE_CMS;
   public static String BASE_EXAM;
   public static final String HANDLE_FILE = "handle.exe";
   public static final String HANDLE_PROCESS = "handle64";
   public static final String PSSUSPEND = "pssuspend.exe";
   public static final String PSKILL = "pskill.exe";
   public static final String PSSUSPEND64 = "pssuspend64";
   public static final String PSKILL64 = "pskill64";
   public static final String SLASH_EXAM = "/exam/";
   public static String LOGIN_URL;
   public static String TOOLS_ZIP;
   public static String RESOURCES_ZIP;
   public static String EXAM_URL;
   public static String LOG_URL;
   public static String VIDEO_URL;
   public static String CMS_URL;
   public static String HANDLE_EXE;
   public static final String DESKTOP = "Desktop";
   public static final String DESKTOP_DIR = "CoMaS";
   public static final String EXAM_DIR = "exam";
   public static final String LOGS_DIR = "logs";
   public static final String SCREENS_DIR = "screens";
   public static final String RESOURCES_DIR = "resources";
   public static final String TOOLS_DIR = "tools";
   public static final String ARCHIVES_DIR = "archives";
   public static final String DOWNLOADS = "Downloads";
   public static final String HOME;
   public static final String TEMP;
   public static String DIR;
   public static String DIR_DRIVE;
   public static final String COMAS_DIRECTORY;
   public static final String ZIP;
   public static String DOWNLOADS_DIR;
   public static String LOG_DIR;
   public static final String HIVE = "HKCU";
   public static final String FOLDERS_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders";
   public static final String REGISTRY_LOCATION = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders";
   public static final String DESKTOP_REGISTRY_KEY = "Desktop";
   public static final String PERSONAL_REGISTRY_KEY = "Personal";
   public static final String WINDOWS_USERPROFILE = "USERPROFILE";
   public static final boolean USING_WINDOWS_REGISTRY = true;
   public static final String STUDENT_NOTES_FILE_NAME = "STUDENT_NOTES_FILE_NAME";
   public static final String STUDENT_NOTES_FILE_CHECKSUM = "STUDENT_NOTES_FILE_CHECKSUM";
   public static final String LOG_SEVERE = "SEVERE";
   public static final String LOG_MINOR = "MINOR";
   public static final String LOG_WARNING = "WARNING";
   public static final String LOG_TEST = "TEST";
   public static final String LOG_INFO = "INFO";
   public static final String LOG_ALERT = "ALERT";
   public static final String LOG_PROBLEM = "PROBLEM";
   public static final String LOG_CLOSE = "CLOSE";
   public static final String LOG_FILES_IN_USE = "FILES_IN_USE";
   public static int LOG_GENERATION_FREQUENCY;
   public static final String LOG_FILE_BASE = "comas-system";
   public static final String LOG_FILE_LAUNCHER_BASE = "comas-base";
   public static final String LOG_FILE_ENDING = "-log.html";
   public static final String LOG_FILE_ENDING_CSV = "-log.csv";
   public static final String LOG_FILE_NAME = "comas-system-log.html";
   public static final String LOG_FILE_NAME_CSV = "comas-system-log.csv";
   public static final String LOG_FILE_LAUNCHER_NAME = "comas-base-log.html";
   public static final String LOG_FILE_LAUNCHER_NAME_CSV = "comas-base-log.csv";
   public static final String LOG_FILE_VIEWER_NAME = "logs.html";
   public static final String SESSION_FILE_NAME = ".es";
   public static final String KEY_DIRECTORY_HOST = "directory_host";
   public static final String KEY_LOG_HOST = "log_host";
   public static final String KEY_UPLOAD_HOST = "upload_host";
   public static final String KEY_VIDEO_HOST = "video_host";
   public static final String KEY_CMS_HOST = "cms_host";
   public static final String KEY_PROTOCOL = "protocol";
   public static final String KEY_WS_PROTOCOL = "ws_protocol";
   public static final String KEY_PORT = "port";
   public static final String KEY_WEBSERVER = "webserver";
   public static final String KEY_COURSE = "course";
   public static final String KEY_ACTIVITY = "activity";
   public static boolean AUTO_ARCHIVE;
   public static int AUTO_ARCHIVE_FREQUENCY;
   public static int ABSOLUTE_MAX_INTERVAL;
   public static int ABSOLUTE_MIN_INTERVAL;
   public static int MIN_AUTHENTICATION_INTERVAL;
   public static int MAX_INTERVAL;
   public static int MIN_INTERVAL;
   public static int MAX_FAILURES;
   public static int MAX_SESSION_FAILURES;
   public static int ALERT_FAILURE_FREQUENCY;
   public static int MAX_MSECS_TO_WAIT_TO_END;
   public static int UPLOAD_THRESHOLD_IN_MSECS;
   public static int ARCHIVE_UPLOAD_THRESHOLD_IN_MSECS;
   public static int MIN_MSECS_BETWEEN_USER_UPLOADS;
   public static int MSECS_BETWEEN_KEEP_ALIVE_CHECK;
   public static int DISAPPEARING_ALERT_TIMEOUT;
   public static long MAX_TIME_TO_WAIT_TO_COORDINATE_UI;
   public static int THIRTY_SECONDS_IN_MSECS;
   public static long MIN_INTERVAL_BETWEEN_SCREEN_SHOTS_IN_MSECS;
   public static int RETRY_TIME;
   public static int LEASE_TIME;
   public static int FREQUENCY_TO_CHECK_EXAM_DIRECTORY;
   public static int MIN_EVENTS_IN_EXAM_DIRECTORY;
   public static int MAX_NUMBER_OF_FILE_WATCHING_FAILURES;
   public static final String INSANE = "Insane";
   public static final String LOGIN = "Login";
   public static final String LOGGING_IN = "Logging in";
   public static final String LOGIN_AWAKE = "Login Awake";
   public static final String LOGIN_SLEEPING = "Login Sleeping";
   public static final String LOGIN_SCREEN_OFF = "Login Screen off";
   public static final String LOGIN_SCREEN_ON = "Login Screen on";
   public static final String LOGGED_OUT = "Logged out";
   public static final String TERMINATED = "Terminated";
   public static final String UNKNOWN = "Unknown";
   public static final String ISSUE = "Issue:";
   public static final String PROBLEM_WEBCAM = "Issue:Webcam";
   public static final String PROBLEM_SCREENSHOTS = "Issue:Screen";
   public static final String PROBLEM_ARCHIVES = "Issue:Archive";
   public static final String PROBLEM_LOGS = "Issue:Log";
   public static final String PROBLEM_FILES = "Issue:Files";
   public static final String NO_DOWNLOAD_REQUIRED = "NO_DOWNLOAD_REQUIRED";
   public static Level LOGGING_LEVEL;
   public static final String VIDEO = "video";
   public static final String UPLOAD = "upload";
   public static final String TEST = "test";
   public static final String HOST = "host";
   public static float IMAGE_COMPRESSION;
   public static final String IMAGE_FORMAT = "jpg";
   public static final String DOT_JPG = ".jpg";
   public static String VERSION;
   public static String PASSKEY_DIRECTORY;
   public static String PASSKEY_LOG;
   public static String PASSKEY_FILE_UPLOAD;
   public static String PASSKEY_VIDEO;
   public static String PASSKEY_EXAM;
   public static Properties CONFIGS;
   public static final String DEFAULT_HOST_KEY = "hostname";
   public static final String DEFAULT_PROTOCOL_KEY = "protocol";
   public static final String DEFAULT_PORT_KEY = "port";
   public static String DEFAULT_HOST;
   public static String BACKUP_HOST;
   public static int FAILURES_UNTIL_MOVE_TO_BACKUP;
   public static ServerConfiguration BACKUP_SERVERS;
   public static ServerConfiguration PRIMARY_SERVERS;
   public static boolean USE_ACTIVITY_CODES;
   public static boolean USE_STUDENT_CODES;
   public static boolean USE_WEB_CAM;
   public static boolean USE_WEB_CAM_ON_SCREEN_SHOT;
   public static boolean USE_SCREEN_SHOTS;
   public static boolean NETWORK_MONITORING;
   public static boolean FILE_MONITORING;
   public static boolean PROCESS_MONITORING;
   public static boolean WINDOW_MONITORING;
   public static boolean BLUETOOTH_MONITORING;
   public static boolean AUDIO_MONITORING;
   public static boolean VIDEO_MONITORING;
   public static boolean SCREEN_SHOT_QR_CODE_REQUIRED;
   public static boolean SCREEN_SHOT_TIMESTAMP_REQUIRED;
   public static float SCREEN_SHOT_TIMESTAMP_HEIGHT;
   public static float SCREEN_SHOT_TIMESTAMP_WIDTH;
   public static int MAX_SUPPORTED_JAVA_VERSION;
   public static int MIN_SUPPORTED_JAVA_VERSION;
   public static int MIN_DRIVE_SPACE_THRESHOLD_PERCENTAGE;
   public static int MIN_DRIVE_SPACE_THRESHOLD_MB;
   public static String SUPPORT_MESSAGE;
   public static String STARTUP_MESSAGE;
   public static String END_MESSAGE;
   public static boolean VERIFY_CODE_SIGNATURE;
   public static boolean CODE_MUST_BE_SIGNED;
   public static String PUBLIC_KEY;
   public static String HASH_OF_APPLICATION_JAR;
   public static float VM_SCREEN_RESOLUTION_PERCENTAGE;
   private static final OS _OS;

   static {
      DIRECTORY_HOST = COMPANY_HOST;
      EXAM_HOST = COMPANY_HOST;
      UPLOAD_HOST = COMPANY_HOST;
      LOG_HOST = COMPANY_HOST;
      MARKING_HOST = COMPANY_HOST;
      VIDEO_HOST = COMPANY_HOST;
      CMS_HOST = COMPANY_HOST;
      WEBSOCKET_HOST = COMPANY_HOST;
      COMAS_DOT = "comas.";
      COMAS_DOT_INI = COMAS_DOT + "ini";
      COMAS_DOT_XML = COMAS_DOT + ".xml";
      COMAS_DOT_JAR_FORMAT = "CoMaS-%s.jar";
      THIS_COMAS_JAR_FILE = String.format(COMAS_DOT_JAR_FORMAT, "0.8.75");
      COMAS_JAR_PATTERN = "^CoMaS-[0-9]\\.[0-9]{1,2}\\.[0-9]{1,2}\\.jar$";
      EXAM_CONFIGURATION_FILE = "https://comas.cogerent.com:8443/CMS/rest/exam/exam.ini";
      LOCAL_EXAM_CONFIGURATION_FILE = "http://192.168.87.10:8080/CMS/rest/exam/exam.ini";
      PROTOCOL = "https";
      WS_PROTOCOL = "wss";
      PORT = "8443";
      CONNECTION_TIMEOUT_IN_MSECS = 5000;
      LOG_PATH = "log";
      VIDEO_PATH = "video";
      UPLOAD_PATH = "upload";
      REGISTER_PATH = "register";
      REGISTRATION_SERVICE = "/CoMaS-Directory/rest/directory/" + REGISTER_PATH;
      UPLOAD_SERVICE = "/CoMaS-FileUpload/rest/file/" + UPLOAD_PATH;
      LOG_SERVICE = "/CoMaS-Log/rest/logger/" + LOG_PATH;
      VIDEO_SERVICE = "/CoMaS-Video/rest/logger/" + VIDEO_PATH;
      BASE_LOGIN = PROTOCOL + "://" + DIRECTORY_HOST + ":" + PORT + "/CoMaS-Directory/rest/directory/";
      BASE_LOG = PROTOCOL + "://" + LOG_HOST + ":" + PORT + "/CoMaS-Log/rest/logger/";
      BASE_UPLOAD = PROTOCOL + "://" + UPLOAD_HOST + ":" + PORT + "/CoMaS-FileUpload/rest/file/";
      BASE_VIDEO = PROTOCOL + "://" + VIDEO_HOST + ":" + PORT + "/CoMaS-Video/rest/logger/";
      BASE_CMS = PROTOCOL + "://" + CMS_HOST + ":" + PORT + "/CMS/rest/";
      BASE_EXAM = PROTOCOL + "://" + CMS_HOST + ":" + PORT + "/Exam/rest/exam/";
      LOGIN_URL = PROTOCOL + "://" + DIRECTORY_HOST + ":" + PORT + REGISTRATION_SERVICE;
      TOOLS_ZIP = BASE_CMS + "/exam/tools.zip";
      RESOURCES_ZIP = BASE_CMS + "/exam/resources.zip";
      EXAM_URL = PROTOCOL + "://" + UPLOAD_HOST + ":" + PORT + UPLOAD_SERVICE;
      LOG_URL = PROTOCOL + "://" + LOG_HOST + ":" + PORT + LOG_SERVICE;
      VIDEO_URL = PROTOCOL + "://" + VIDEO_HOST + ":" + PORT + VIDEO_SERVICE;
      CMS_URL = PROTOCOL + "://" + CMS_HOST + ":" + PORT + "/CMS/rest/";
      HANDLE_EXE = PROTOCOL + "://" + EXAM_HOST + "/exam/handle.exe";
      HOME = System.getProperty("user.home");
      TEMP = System.getProperty("java.io.tmpdir");
      String var10000 = getDesktopDirectory();
      DIR = var10000 + File.separator + "CoMaS";
      DIR_DRIVE = getDesktopDirectoryDrive();
      COMAS_DIRECTORY = DIR;
      ZIP = HOME;
      var10000 = getDownloadsDirectory();
      DOWNLOADS_DIR = var10000 + File.separator;
      LOG_DIR = HOME;
      LOG_GENERATION_FREQUENCY = 10;
      AUTO_ARCHIVE = true;
      AUTO_ARCHIVE_FREQUENCY = 20;
      ABSOLUTE_MAX_INTERVAL = 60;
      ABSOLUTE_MIN_INTERVAL = 1;
      MIN_AUTHENTICATION_INTERVAL = 60;
      MAX_INTERVAL = 60;
      MIN_INTERVAL = 10;
      MAX_FAILURES = 100;
      MAX_SESSION_FAILURES = 10000;
      ALERT_FAILURE_FREQUENCY = 20;
      MAX_MSECS_TO_WAIT_TO_END = 60000;
      UPLOAD_THRESHOLD_IN_MSECS = 60000;
      ARCHIVE_UPLOAD_THRESHOLD_IN_MSECS = 60000;
      MIN_MSECS_BETWEEN_USER_UPLOADS = 300000;
      MSECS_BETWEEN_KEEP_ALIVE_CHECK = 300000;
      DISAPPEARING_ALERT_TIMEOUT = 10000;
      MAX_TIME_TO_WAIT_TO_COORDINATE_UI = 10000L;
      THIRTY_SECONDS_IN_MSECS = 30000;
      MIN_INTERVAL_BETWEEN_SCREEN_SHOTS_IN_MSECS = 1000L;
      RETRY_TIME = 20000;
      LEASE_TIME = 300;
      FREQUENCY_TO_CHECK_EXAM_DIRECTORY = 15;
      MIN_EVENTS_IN_EXAM_DIRECTORY = 0;
      MAX_NUMBER_OF_FILE_WATCHING_FAILURES = 10;
      LOGGING_LEVEL = Level.INFO;
      IMAGE_COMPRESSION = 0.2F;
      VERSION = "0.7.15";
      PASSKEY_DIRECTORY = "SimpleDirectoryV2";
      PASSKEY_LOG = "LoggerServiceV2";
      PASSKEY_FILE_UPLOAD = "UploadFileServiceV2";
      PASSKEY_VIDEO = "VideoServiceV2";
      PASSKEY_EXAM = "ExamServiceV2";
      CONFIGS = new Properties();
      DEFAULT_HOST = COMPANY_HOST;
      USE_ACTIVITY_CODES = false;
      USE_STUDENT_CODES = false;
      USE_WEB_CAM = true;
      USE_WEB_CAM_ON_SCREEN_SHOT = false;
      USE_SCREEN_SHOTS = true;
      NETWORK_MONITORING = true;
      FILE_MONITORING = true;
      PROCESS_MONITORING = false;
      WINDOW_MONITORING = false;
      BLUETOOTH_MONITORING = true;
      AUDIO_MONITORING = false;
      VIDEO_MONITORING = false;
      SCREEN_SHOT_QR_CODE_REQUIRED = true;
      SCREEN_SHOT_TIMESTAMP_REQUIRED = true;
      SCREEN_SHOT_TIMESTAMP_HEIGHT = 0.0F;
      SCREEN_SHOT_TIMESTAMP_WIDTH = 0.5F;
      MAX_SUPPORTED_JAVA_VERSION = 25;
      MIN_SUPPORTED_JAVA_VERSION = 8;
      MIN_DRIVE_SPACE_THRESHOLD_PERCENTAGE = 5;
      MIN_DRIVE_SPACE_THRESHOLD_MB = 2000;
      SUPPORT_MESSAGE = "";
      STARTUP_MESSAGE = "";
      END_MESSAGE = "";
      VERIFY_CODE_SIGNATURE = false;
      CODE_MUST_BE_SIGNED = false;
      VM_SCREEN_RESOLUTION_PERCENTAGE = 0.04F;
      _OS = getOS();
   }

   public static String examDotIni(String host) {
      if (host.startsWith("https://")) {
         return String.format("%s:%s/CMS/rest/exam/exam.ini", host, "8443");
      } else if (host.startsWith("http://")) {
         return String.format("%s:%s/CMS/rest/exam/exam.ini", host, "8080");
      } else {
         String[] tokens = host.split(":");
         if (tokens != null && tokens.length > 1) {
            host = tokens[0].trim();
            PORT = tokens[1].trim();
         }

         return String.format("%s://%s:%s/CMS/rest/exam/exam.ini", PROTOCOL, host, PORT);
      }
   }

   public static String getDesktopDirectory() {
      String desktopDirectory = null;
      if (isWindowsOS()) {
         desktopDirectory = WindowsRegistry.readRegistry("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders", "Desktop");
         if (desktopDirectory == null) {
            desktopDirectory = System.getenv("USERPROFILE");
            if (desktopDirectory != null) {
               desktopDirectory = desktopDirectory + File.separator + "Desktop";
            }
         }
      }

      return isWriteableDirectory(desktopDirectory) ? desktopDirectory : HOME + File.separator + "Desktop";
   }

   public static String getDesktopDirectoryDrive() {
      String dir = getDesktopDirectory();
      String os = System.getProperty("os.name").toLowerCase();
      return os.startsWith("win") ? dir.charAt(0) + ":" : "";
   }

   public static String getDownloadsDirectory() {
      String dir = HOME + File.separator + "Downloads";
      if (isWriteableDirectory(dir)) {
         return dir;
      } else {
         String downloadsDirectory = null;
         String os = System.getProperty("os.name").toLowerCase();
         if (os.startsWith("win")) {
            downloadsDirectory = WindowsRegistry.readRegistry("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders", "Personal");
            if (isWriteableDirectory(downloadsDirectory)) {
               return downloadsDirectory;
            }

            downloadsDirectory = System.getenv("USERPROFILE");
            if (downloadsDirectory != null) {
               downloadsDirectory = downloadsDirectory + File.separator + "Downloads";
            }

            if (isWriteableDirectory(downloadsDirectory)) {
               return downloadsDirectory;
            }
         }

         return DIR;
      }
   }

   public static boolean isWriteableDirectory(String dir) {
      if (dir != null) {
         File directoryFile = new File(dir);
         if (directoryFile.exists() && directoryFile.isDirectory() && directoryFile.canWrite()) {
            return true;
         }
      }

      return false;
   }

   public static String service(String protocol, String host, String port, String service) {
      return protocol + "://" + host + ":" + port + service;
   }

   public static String getCourseDirectory(String course) {
      return DIR.charAt(DIR.length() - 1) == File.separatorChar ? DIR + course : DIR + File.separator + course;
   }

   public static String getActivityDirectory(String course, String activity) {
      String var10000 = getCourseDirectory(course);
      return var10000 + File.separator + activity;
   }

   public static String getBaseDirectory(String course, String activity) {
      return DIR + File.separator + course + File.separator + activity + File.separator;
   }

   public static String getExamDirectory(String course, String activity) {
      String var10000 = getBaseDirectory(course, activity);
      return var10000 + "exam" + File.separator;
   }

   public static String getLogsDirectory(String course, String activity) {
      String var10000 = getBaseDirectory(course, activity);
      return var10000 + "logs" + File.separator;
   }

   public static String getResourcesDirectory(String course, String activity) {
      String var10000 = getBaseDirectory(course, activity);
      return var10000 + "resources" + File.separator;
   }

   public static String getScreensDirectory(String course, String activity) {
      String var10000 = getBaseDirectory(course, activity);
      return var10000 + "screens" + File.separator;
   }

   public static String getArchivesDirectory(String course, String activity) {
      String var10000 = getBaseDirectory(course, activity);
      return var10000 + "archives" + File.separator;
   }

   public static String getToolsDirectory(String course, String activity) {
      String var10000 = getBaseDirectory(course, activity);
      return var10000 + "tools" + File.separator;
   }

   public static void setupPrimaryServer(String course) {
      if (course != null && CONFIGS != null) {
         String defaultHost = CONFIGS.getProperty(course + ".hostname");
         if (defaultHost == null) {
            defaultHost = CONFIGS.getProperty("hostname");
         }

         if (defaultHost != null) {
            if (PRIMARY_SERVERS == null) {
               PRIMARY_SERVERS = new ServerConfiguration(defaultHost);
               PRIMARY_SERVERS.process();
            }

            String actualHost = PRIMARY_SERVERS.next(true);
            if (actualHost != null) {
               CONFIGS.setProperty(course + ".hostname", actualHost);
            }
         }
      }

   }

   public static void setupURLs(String course) {
      Properties configs;
      if (!CONFIGS.isEmpty()) {
         configs = CONFIGS;
      } else {
         Logger.output("Accessing " + EXAM_CONFIGURATION_FILE);
         configs = Utils.getProperties(EXAM_CONFIGURATION_FILE);
         if (configs.isEmpty()) {
            configs = Utils.getProperties(LOCAL_EXAM_CONFIGURATION_FILE);
         }
      }

      String defaultGlobalHost = configs.getProperty("hostname", DIRECTORY_HOST).trim();
      String defaultGlobalProtocol = configs.getProperty("protocol", PROTOCOL).trim();
      String defaultGlobalPort = configs.getProperty("port", PORT).trim();
      if (course != null) {
         String defaultHost = configs.getProperty(course + ".hostname", defaultGlobalHost).trim();
         DEFAULT_HOST = defaultHost;
         DIRECTORY_HOST = configs.getProperty(course + ".directory", defaultHost).trim();
         EXAM_HOST = configs.getProperty(course + ".exam", defaultHost).trim();
         UPLOAD_HOST = configs.getProperty(course + ".upload", defaultHost).trim();
         LOG_HOST = configs.getProperty(course + ".log", defaultHost).trim();
         VIDEO_HOST = configs.getProperty(course + ".video", defaultHost).trim();
         CMS_HOST = configs.getProperty(course + ".cms", defaultHost).trim();
         WEBSOCKET_HOST = configs.getProperty(course + ".websocket", defaultHost).trim();
         PROTOCOL = configs.getProperty(course + ".protocol", defaultGlobalProtocol).trim();
         if (PROTOCOL.equals("https")) {
            WS_PROTOCOL = "wss";
         } else {
            WS_PROTOCOL = "ws";
         }

         PORT = configs.getProperty(course + ".port", defaultGlobalPort).trim();
         BACKUP_HOST = configs.getProperty(course + ".backup");
         if (BACKUP_HOST == null) {
            BACKUP_HOST = configs.getProperty("backup.hostname");
         }

         if (BACKUP_HOST != null) {
            if (BACKUP_SERVERS == null) {
               BACKUP_SERVERS = new ServerConfiguration(BACKUP_HOST);
               BACKUP_SERVERS.process();
               String reuseFlag = configs.getProperty(course + ".backup.reuse");
               if (reuseFlag == null) {
                  reuseFlag = configs.getProperty("backup.hostname.reuse", "false");
               }

               boolean canReuseFlag = Boolean.parseBoolean(reuseFlag.trim());
               if (canReuseFlag) {
                  BACKUP_SERVERS.canReuse();
               }
            }

            FAILURES_UNTIL_MOVE_TO_BACKUP = Utils.getIntegerOrDefaultInRange(configs, "backup.failures", 10, 1, Integer.MAX_VALUE);
         } else {
            FAILURES_UNTIL_MOVE_TO_BACKUP = Integer.MAX_VALUE;
         }
      }

      updateURLs();
   }

   public static void updateURLs() {
      LOGIN_URL = service(PROTOCOL, DIRECTORY_HOST, PORT, REGISTRATION_SERVICE);
      EXAM_URL = service(PROTOCOL, UPLOAD_HOST, PORT, UPLOAD_SERVICE);
      LOG_URL = service(PROTOCOL, LOG_HOST, PORT, LOG_SERVICE);
      VIDEO_URL = service(PROTOCOL, VIDEO_HOST, PORT, VIDEO_SERVICE);
      CMS_URL = service(PROTOCOL, CMS_HOST, PORT, "/CMS/rest/");
      BASE_LOGIN = service(PROTOCOL, DIRECTORY_HOST, PORT, "/CoMaS-Directory/rest/directory/");
      BASE_LOG = service(PROTOCOL, LOG_HOST, PORT, "/CoMaS-Log/rest/logger/");
      BASE_UPLOAD = service(PROTOCOL, UPLOAD_HOST, PORT, "/CoMaS-FileUpload/rest/file/");
      BASE_VIDEO = service(PROTOCOL, VIDEO_HOST, PORT, "/CoMaS-Video/rest/logger/");
      BASE_CMS = service(PROTOCOL, CMS_HOST, PORT, "/CMS/rest/");
      BASE_EXAM = service(PROTOCOL, CMS_HOST, PORT, "/Exam/rest/exam/");
      TOOLS_ZIP = BASE_CMS + "exam/tools.zip";
      RESOURCES_ZIP = BASE_CMS + "exam/resources.zip";
      CLIENT_LOGIN_CONFIGURATION_URL = BASE_CMS + "exam/client.ini";
      SYSTEM_LOGIN_CONFIGURATION_URL = BASE_CMS + "exam/login.ini";
      HANDLE_EXE = BASE_CMS + "exam/handle.exe";
   }

   public static Properties getClientLoginProperties(String course, String token) {
      Properties configs = Utils.getProperties(CLIENT_LOGIN_CONFIGURATION_URL, "Cookie", "token=" + token);
      if (configs == null) {
         configs = new Properties();
      } else {
         updateSessionParameters(configs, true);
      }

      return configs;
   }

   public static void updateSessionParameters(Properties configs, boolean all) {
      String os = getOSString();
      END_MESSAGE = Utils.getStringOrDefault(configs, "application.end." + os + ".message", "");
      if (END_MESSAGE.length() == 0) {
         END_MESSAGE = Utils.getStringOrDefault(configs, "application.end.message", "");
      }

      if (!isMacOS()) {
         configs.remove("application.end.macOS.message");
      }

      if (!isWindowsOS()) {
         configs.remove("application.end.windows.message");
      }

      if (!isLinuxOS()) {
         configs.remove("application.end.linux.message");
      }

      SUPPORT_MESSAGE = Utils.getStringOrDefault(configs, "application.support." + os + ".message", "");
      if (SUPPORT_MESSAGE.length() == 0) {
         SUPPORT_MESSAGE = Utils.getStringOrDefault(configs, "application.support.message", "Please contact support.");
      }

      if (!isMacOS()) {
         configs.remove("application.support.macOS.message");
      }

      if (!isWindowsOS()) {
         configs.remove("application.support.windows.message");
      }

      if (!isLinuxOS()) {
         configs.remove("application.support.linux.message");
      }

      DISAPPEARING_ALERT_TIMEOUT = Utils.getIntegerOrDefaultInRange(configs, "session.alert.timeout", 10, 0, 28800) * 1000;
      STARTUP_MESSAGE = Utils.getStringOrDefault(configs, "application.startup." + os + ".message", "");
      if (STARTUP_MESSAGE.length() == 0) {
         STARTUP_MESSAGE = Utils.getStringOrDefault(configs, "application.startup.message", "");
      }

      if (!isMacOS()) {
         configs.remove("application.startup.macOS.message");
      }

      if (!isWindowsOS()) {
         configs.remove("application.startup.windows.message");
      }

      if (!isLinuxOS()) {
         configs.remove("application.startup.linux.message");
      }

      MIN_DRIVE_SPACE_THRESHOLD_PERCENTAGE = Utils.getIntegerOrDefaultInRange(configs, "min_free_drive_space_percentage", 5, 0, 100);
      MIN_DRIVE_SPACE_THRESHOLD_MB = Utils.getIntegerOrDefaultInRange(configs, "min_free_drive_space_mb", 2000, 0, Integer.MAX_VALUE);
      MAX_SUPPORTED_JAVA_VERSION = Utils.getIntegerOrDefaultInRange(configs, "max_supported_java_version", 50, 8, 100);
      MIN_SUPPORTED_JAVA_VERSION = Utils.getIntegerOrDefaultInRange(configs, "min_supported_java_version", 8, 8, MAX_SUPPORTED_JAVA_VERSION);
      AUTO_ARCHIVE = Utils.getBooleanOrDefault(configs, "auto_archive", true);
      AUTO_ARCHIVE_FREQUENCY = Utils.getIntegerOrDefaultInRange(configs, "auto_archive_frequency", 20, AUTO_ARCHIVE ? 1 : 0, 100);
      if (!AUTO_ARCHIVE) {
         AUTO_ARCHIVE_FREQUENCY = Integer.MAX_VALUE;
      }

      LOG_GENERATION_FREQUENCY = Utils.getIntegerOrDefaultInRange(configs, "log_generation_frequency", 10, 1, Integer.MAX_VALUE);
      String logging_level;
      if (configs.contains("logging_level")) {
         logging_level = configs.getProperty("logging_level");
      } else {
         logging_level = Utils.getStringOrDefault(configs, "logs.level", "INFO");
      }

      try {
         LOGGING_LEVEL = edu.carleton.cas.logging.Level.parse(logging_level);
      } catch (IllegalArgumentException var5) {
         LOGGING_LEVEL = Level.INFO;
      }

      VM_SCREEN_RESOLUTION_PERCENTAGE = Utils.getFloatOrDefaultInRange(configs, "vm_screen_resolution_percentage", 0.04F, 0.0F, 100.0F);
      CONNECTION_TIMEOUT_IN_MSECS = Utils.getIntegerOrDefaultInRange(configs, "connection_timeout", 5000, 1000, 60000);
      VERSION = Utils.getStringOrDefault(configs, "version", VERSION);
      MAX_INTERVAL = Utils.getIntegerOrDefaultInRange(configs, "max_interval", Math.min(60, ABSOLUTE_MAX_INTERVAL), ABSOLUTE_MIN_INTERVAL, ABSOLUTE_MAX_INTERVAL);
      MIN_INTERVAL = Utils.getIntegerOrDefaultInRange(configs, "min_interval", Math.min(10, MAX_INTERVAL), ABSOLUTE_MIN_INTERVAL, MAX_INTERVAL);
      LEASE_TIME = Utils.getIntegerOrDefaultInRange(configs, "lease_time", 300, 300, 3600);
      MIN_AUTHENTICATION_INTERVAL = Utils.getIntegerOrDefaultInRange(configs, "min_authentication_interval", Math.round((float)LEASE_TIME * 0.8F), 0, LEASE_TIME);
      MAX_FAILURES = Utils.getIntegerOrDefaultInRange(configs, "max_failures", 100, 1, Integer.MAX_VALUE);
      MAX_MSECS_TO_WAIT_TO_END = Utils.getIntegerOrDefaultInRange(configs, "max_msecs_to_wait_to_end", 60000, 1, MAX_MSECS_TO_WAIT_TO_END * 10);
      UPLOAD_THRESHOLD_IN_MSECS = Utils.getIntegerOrDefaultInRange(configs, "upload_threshold_in_msecs", 60000, 1, Integer.MAX_VALUE);
      ARCHIVE_UPLOAD_THRESHOLD_IN_MSECS = Utils.getIntegerOrDefaultInRange(configs, "archive_upload_threshold_in_msecs", 60000, 1, Integer.MAX_VALUE);
      MIN_MSECS_BETWEEN_USER_UPLOADS = Utils.getIntegerOrDefaultInRange(configs, "min_msecs_between_user_uploads", 300000, 60000, 21600000);
      MSECS_BETWEEN_KEEP_ALIVE_CHECK = Utils.getIntegerOrDefaultInRange(configs, "msecs_between_keep_alive_checks", 300000, 60000, 1800000);
      MIN_INTERVAL_BETWEEN_SCREEN_SHOTS_IN_MSECS = (long)Utils.getIntegerOrDefaultInRange(configs, "min_msecs_between_screenshots", 1000, 1000, 60000);
      RETRY_TIME = Utils.getIntegerOrDefaultInRange(configs, "retry_time", 20000, 1000, 60000);
      FREQUENCY_TO_CHECK_EXAM_DIRECTORY = Utils.getIntegerOrDefaultInRange(configs, "frequency_to_check_exam_directory", 15, 1, 1000) * 60 * 1000;
      MIN_EVENTS_IN_EXAM_DIRECTORY = Utils.getIntegerOrDefaultInRange(configs, "min_events_in_exam_directory", 0, 0, Integer.MAX_VALUE);
      IMAGE_COMPRESSION = Utils.getFloatOrDefaultInRange(configs, "image_compression", 0.2F, 0.0F, 1.0F);
      USE_ACTIVITY_CODES = Utils.getBooleanOrDefault(configs, "use_activity_codes", true);
      USE_STUDENT_CODES = Utils.getBooleanOrDefault(configs, "use_student_codes", false);
      if (USE_ACTIVITY_CODES) {
         USE_STUDENT_CODES = false;
      }

      USE_WEB_CAM = Utils.getBooleanOrDefault(configs, "webcam.enabled", true);
      USE_WEB_CAM_ON_SCREEN_SHOT = Utils.getBooleanOrDefault(configs, "webcam.on_screen_shot", false);
      if (USE_WEB_CAM_ON_SCREEN_SHOT) {
         USE_WEB_CAM = false;
      }

      USE_SCREEN_SHOTS = Utils.getBooleanOrDefault(configs, "monitoring.screenshots.required", true);
      if (!USE_SCREEN_SHOTS) {
         USE_WEB_CAM_ON_SCREEN_SHOT = false;
      }

      SCREEN_SHOT_QR_CODE_REQUIRED = Utils.getBooleanOrDefault(configs, "monitoring.screenshots.qr_code", false);
      SCREEN_SHOT_TIMESTAMP_REQUIRED = Utils.getBooleanOrDefault(configs, "monitoring.screenshots.timestamp", false);
      SCREEN_SHOT_TIMESTAMP_HEIGHT = (float)Utils.getIntegerOrDefaultInRange(configs, "screenshot.timestamp.height", 0, 0, 100) / 100.0F;
      SCREEN_SHOT_TIMESTAMP_WIDTH = (float)Utils.getIntegerOrDefaultInRange(configs, "screenshot.timestamp.width", 50, 0, 100) / 100.0F;
      NETWORK_MONITORING = Utils.getBooleanOrDefault(configs, "monitoring.network.required", true);
      FILE_MONITORING = Utils.getBooleanOrDefault(configs, "monitoring.file.required", true);
      PROCESS_MONITORING = Utils.getBooleanOrDefault(configs, "monitoring.processes.required", false);
      WINDOW_MONITORING = Utils.getBooleanOrDefault(configs, "monitoring.windows.required", false);
      BLUETOOTH_MONITORING = Utils.getBooleanOrDefault(configs, "monitoring.bluetooth.required", false);
      AUDIO_MONITORING = Utils.getBooleanOrDefault(configs, "monitoring.audio.required", false);
      VIDEO_MONITORING = Utils.getBooleanOrDefault(configs, "monitoring.video.required", false);
      if (all) {
         PASSKEY_DIRECTORY = Utils.getStringOrDefault(configs, "passkey.directory", "SimpleDirectoryV2");
         PASSKEY_LOG = Utils.getStringOrDefault(configs, "passkey.log", "LoggerServiceV2");
         PASSKEY_VIDEO = Utils.getStringOrDefault(configs, "passkey.video", "VideoServiceV2");
         PASSKEY_FILE_UPLOAD = Utils.getStringOrDefault(configs, "passkey.file_upload", "UploadFileServiceV2");
         PASSKEY_EXAM = Utils.getStringOrDefault(configs, "passkey.exam", "ExamServiceV2");
      }

   }

   public static String getOSString() {
      if (isMacOS()) {
         return "macOS";
      } else if (isWindowsOS()) {
         return "windows";
      } else {
         return isLinuxOS() ? "linux" : "unknown";
      }
   }

   public static String getOSDisplayString() {
      if (isMacOS()) {
         return "macOS";
      } else if (isWindowsOS()) {
         return "Windows";
      } else {
         return isLinuxOS() ? "Linux" : "unknown";
      }
   }

   public static OS getOS() {
      if (_OS != null) {
         return _OS;
      } else {
         String os = System.getProperty("os.name").toLowerCase();
         if (os.startsWith("mac os x")) {
            return ClientShared.OS.macOS;
         } else if (os.indexOf("win") > -1) {
            return ClientShared.OS.windows;
         } else {
            return os.indexOf("nix") < 0 && os.indexOf("nux") < 0 ? ClientShared.OS.unknown : ClientShared.OS.linux;
         }
      }
   }

   public static boolean isMacOS() {
      return getOS() == ClientShared.OS.macOS;
   }

   public static boolean isWindowsOS() {
      return getOS() == ClientShared.OS.windows;
   }

   public static boolean isLinuxOS() {
      return getOS() == ClientShared.OS.linux;
   }

   public static File[] identifyOldVersions() {
      try {
         File comasDir = new File(DIR);
         final Pattern p = Pattern.compile(COMAS_JAR_PATTERN);
         File[] oldVersions = comasDir.listFiles(new FileFilter() {
            public boolean accept(File f) {
               if (f.getName().equals(ClientShared.THIS_COMAS_JAR_FILE)) {
                  return false;
               } else {
                  Matcher m = p.matcher(f.getName());
                  return m.matches();
               }
            }
         });
         return oldVersions;
      } catch (Exception var3) {
         return null;
      }
   }

   public static void deleteOldVersions() {
      File[] oldVersions = identifyOldVersions();
      if (oldVersions != null) {
         for(File ov : oldVersions) {
            ov.delete();
         }
      }

   }

   public static void processCommandLineArgs(String[] args) {
      System.setProperty("java.locale.providers", "COMPAT,CLDR");

      for(int i = 0; i < args.length; ++i) {
         if (args[i].equals("-find")) {
            LOOK_FOR_SERVICES = true;
         } else if (args[i].equals("-comas")) {
            if (i + 1 < args.length) {
               DIR = args[i + 1].trim();
               Logger.output("CoMaS DIRECTORY = " + DIR);
            }
         } else if (args[i].equals("-downloads")) {
            if (i + 1 < args.length) {
               DOWNLOADS_DIR = args[i + 1].trim();
               Logger.output("Downloads DIRECTORY = " + DOWNLOADS_DIR);
            }
         } else if (args[i].equals("-logging")) {
            if (i + 1 < args.length) {
               try {
                  LOGGING_LEVEL = Level.parse(args[i + 1].trim());
               } catch (Exception var3) {
                  LOGGING_LEVEL = Level.INFO;
               }

               Logger.output("Logging level = " + String.valueOf(LOGGING_LEVEL));
            }
         } else if (args[i].equals("-course")) {
            if (i + 1 < args.length) {
               STUDENT_COURSE = args[i + 1].trim();
               Logger.output("Course = " + STUDENT_COURSE);
            }
         } else if (args[i].equals("-activity")) {
            if (i + 1 < args.length) {
               STUDENT_ACTIVITY = args[i + 1].trim();
               Logger.output("Activity = " + STUDENT_ACTIVITY);
            }
         } else if (args[i].equals("-name")) {
            if (i + 2 < args.length) {
               STUDENT_FIRST_NAME = args[i + 1].trim().toLowerCase();
               STUDENT_LAST_NAME = args[i + 2].trim().toLowerCase();
               Logger.output("Name = " + STUDENT_FIRST_NAME + " " + STUDENT_LAST_NAME);
            }
         } else if (args[i].equals("-first")) {
            if (i + 1 < args.length) {
               STUDENT_FIRST_NAME = args[i + 1].trim().toLowerCase();
               Logger.output("First = " + STUDENT_FIRST_NAME);
            }
         } else if (args[i].equals("-last")) {
            if (i + 1 < args.length) {
               STUDENT_LAST_NAME = args[i + 1].trim().toLowerCase();
               Logger.output("Last = " + STUDENT_LAST_NAME);
            }
         } else if (args[i].equals("-id")) {
            if (i + 1 < args.length) {
               STUDENT_ID = args[i + 1].trim();
               Logger.output("ID = " + STUDENT_ID);
            }
         } else if (args[i].equals("-server") && i + 1 < args.length) {
            SERVER_CHOSEN = args[i + 1].trim();
            Logger.output("Server = " + SERVER_CHOSEN);
         }
      }

   }

   public static String getJavaVersion() {
      String javaVersion = System.getProperty("java.version");
      if (javaVersion == null) {
         javaVersion = System.getProperty("java.runtime.version");
      }

      return javaVersion != null ? javaVersion : "unknown";
   }

   public static enum OS {
      unknown,
      windows,
      macOS,
      linux;

      public static OS parse(String os) {
         os = os.toLowerCase().trim();
         if (os.equals("windows")) {
            return windows;
         } else if (os.equals("linux")) {
            return linux;
         } else {
            return os.equals("macos") ? macOS : unknown;
         }
      }

      public boolean isUnknown() {
         return this == unknown;
      }

      public boolean isMacOS() {
         return this == macOS;
      }

      public boolean isWindows() {
         return this == windows;
      }

      public boolean isLinux() {
         return this == linux;
      }

      public static boolean isSameOS(OS os) {
         return ClientShared._OS == os || os == unknown;
      }
   }
}
