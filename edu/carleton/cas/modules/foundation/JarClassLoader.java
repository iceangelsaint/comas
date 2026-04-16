package edu.carleton.cas.modules.foundation;

import edu.carleton.cas.utility.CodeVerifier;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes.Name;

public class JarClassLoader extends ClassLoader {
   public static final String KEY_LOGGER = "JarClassLoader.logger";
   public static final String KEY_LOGGER_LEVEL = "JarClassLoader.logger.level";
   public static final String KEY_LOGGER_AREA = "JarClassLoader.logger.area";
   public static final String TMP_SUB_DIRECTORY = "JarClassLoader";
   private File dirTemp;
   private PrintStream logger;
   private List lstJarFile;
   private Set hsDeleteOnExit;
   private Map hmClass;
   private LogLevel logLevel;
   private Set hsLogArea;
   private boolean bLogConsole;
   static int ______INIT;
   static int ______SHUTDOWN;
   static int ______ACCESS;
   static int ______OVERRIDE;
   static int ______HELPERS;

   public JarClassLoader(URL urlToBeLoaded, ClassLoader parent, String token) {
      super(parent);
      this.initLogger();
      this.hmClass = new HashMap();
      this.lstJarFile = new ArrayList();
      this.hsDeleteOnExit = new HashSet();
      String sUrlTopJar = null;
      ProtectionDomain pdTop = this.getClass().getProtectionDomain();
      CodeSource cs = pdTop.getCodeSource();
      URL urlTopJar = cs.getLocation();
      urlTopJar = urlToBeLoaded;
      String protocol = urlToBeLoaded.getProtocol();
      JarFileInfo jarFileInfo = null;
      if ("http".equals(protocol) || "https".equals(protocol)) {
         try {
            urlTopJar = new URL("jar:" + String.valueOf(urlTopJar) + "!/");
            JarURLConnection jarCon = (JarURLConnection)urlTopJar.openConnection();
            if (token != null) {
               jarCon.setRequestProperty("Cookie", "token=" + token);
            }

            JarFile jarFile = jarCon.getJarFile();
            jarFileInfo = new JarFileInfo(jarFile, jarFile.getName(), (JarFileInfo)null, pdTop, (File)null);
            this.logInfo(JarClassLoader.LogArea.JAR, "Loading from top JAR: '%s' PROTOCOL: '%s'", urlTopJar, protocol);
         } catch (Exception e) {
            this.logError(JarClassLoader.LogArea.JAR, "Failure to load HTTP JAR: %s %s", urlToBeLoaded, e.toString());
            return;
         }
      }

      if ("file".equals(protocol)) {
         sUrlTopJar = URLDecoder.decode(urlTopJar.getFile(), StandardCharsets.UTF_8);
         File fileJar = new File(sUrlTopJar);
         if (fileJar.isDirectory()) {
            this.logInfo(JarClassLoader.LogArea.JAR, "Loading from exploded directory: %s", sUrlTopJar);
            return;
         }

         try {
            jarFileInfo = new JarFileInfo(new JarFile(fileJar), fileJar.getName(), (JarFileInfo)null, pdTop, (File)null);
            this.logInfo(JarClassLoader.LogArea.JAR, "Loading from top JAR: '%s' PROTOCOL: '%s'", sUrlTopJar, protocol);
         } catch (IOException e) {
            this.logError(JarClassLoader.LogArea.JAR, "Not a JAR: %s %s", sUrlTopJar, e.toString());
            return;
         }
      }

      try {
         if (jarFileInfo == null) {
            throw new IOException(String.format("Unknown protocol %s", protocol));
         }

         this.loadJar(jarFileInfo);
      } catch (IOException e) {
         this.logError(JarClassLoader.LogArea.JAR, "Not valid URL: %s %s", urlTopJar, e.toString());
         return;
      }

      this.checkShading();
      Runtime.getRuntime().addShutdownHook(new Thread() {
         public void run() {
            JarClassLoader.this.shutdown();
         }
      });
   }

   private void initLogger() {
      this.bLogConsole = true;
      this.logger = System.out;
      this.logLevel = JarClassLoader.LogLevel.ERROR;
      this.hsLogArea = new HashSet();
      this.hsLogArea.add(JarClassLoader.LogArea.CONFIG);
      String sLogger = System.getProperty("JarClassLoader.logger");
      if (sLogger != null) {
         try {
            this.logger = new PrintStream(sLogger);
            this.bLogConsole = false;
         } catch (FileNotFoundException var10) {
            this.logError(JarClassLoader.LogArea.CONFIG, "Cannot create log file %s.", sLogger);
         }
      }

      String sLogLevel = System.getProperty("JarClassLoader.logger.level");
      if (sLogLevel != null) {
         try {
            this.logLevel = JarClassLoader.LogLevel.valueOf(sLogLevel);
         } catch (Exception var9) {
            this.logError(JarClassLoader.LogArea.CONFIG, "Not valid parameter in %s=%s", "JarClassLoader.logger.level", sLogLevel);
         }
      }

      String sLogArea = System.getProperty("JarClassLoader.logger.area");
      if (sLogArea != null) {
         String[] tokenAll = sLogArea.split(",");

         try {
            for(String t : tokenAll) {
               this.hsLogArea.add(JarClassLoader.LogArea.valueOf(t));
            }
         } catch (Exception var11) {
            this.logError(JarClassLoader.LogArea.CONFIG, "Not valid parameter in %s=%s", "JarClassLoader.logger.area", sLogArea);
         }
      }

      if (this.hsLogArea.size() == 1 && this.hsLogArea.contains(JarClassLoader.LogArea.CONFIG)) {
         LogArea[] var15;
         for(LogArea la : var15 = JarClassLoader.LogArea.values()) {
            this.hsLogArea.add(la);
         }
      }

   }

   private File createTempFile(JarEntryInfo inf) throws JarClassLoaderException {
      if (this.dirTemp == null) {
         File dir = new File(System.getProperty("java.io.tmpdir"), "JarClassLoader");
         if (!dir.exists()) {
            dir.mkdir();
         }

         this.chmod777(dir);
         if (!dir.exists() || !dir.isDirectory()) {
            throw new JarClassLoaderException("Cannot create temp directory " + dir.getAbsolutePath());
         }

         this.dirTemp = dir;
      }

      File fileTmp = null;

      try {
         fileTmp = File.createTempFile(inf.getName() + ".", (String)null, this.dirTemp);
         fileTmp.deleteOnExit();
         this.chmod777(fileTmp);
         byte[] a_by = inf.getJarBytes();
         BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(fileTmp));
         os.write(a_by);
         os.close();
         return fileTmp;
      } catch (IOException e) {
         throw new JarClassLoaderException(String.format("Cannot create temp file '%s' for %s", fileTmp, inf.jarEntry), e);
      }
   }

   private void loadJar(JarFileInfo jarFileInfo) throws IOException {
      this.lstJarFile.add(jarFileInfo);

      try {
         Enumeration<JarEntry> en = jarFileInfo.jarFile.entries();
         String EXT_JAR = ".jar";

         while(en.hasMoreElements()) {
            JarEntry je = (JarEntry)en.nextElement();
            if (!je.isDirectory()) {
               String s = je.getName().toLowerCase();
               if (s.lastIndexOf(".jar") == s.length() - ".jar".length()) {
                  JarEntryInfo inf = new JarEntryInfo(jarFileInfo, je);
                  File fileTemp = this.createTempFile(inf);
                  this.logInfo(JarClassLoader.LogArea.JAR, "Loading inner JAR %s from temp file %s", inf.jarEntry, this.getFilename4Log(fileTemp));
                  URL url = fileTemp.toURI().toURL();
                  ProtectionDomain pdParent = jarFileInfo.pd;
                  CodeSource csParent = pdParent.getCodeSource();
                  Certificate[] certParent = csParent.getCertificates();
                  CodeSource csChild = certParent == null ? new CodeSource(url, csParent.getCodeSigners()) : new CodeSource(url, certParent);
                  ProtectionDomain pdChild = new ProtectionDomain(csChild, pdParent.getPermissions(), pdParent.getClassLoader(), pdParent.getPrincipals());
                  this.loadJar(new JarFileInfo(new JarFile(fileTemp), inf.getName(), jarFileInfo, pdChild, fileTemp));
               }
            }
         }

      } catch (JarClassLoaderException e) {
         throw new RuntimeException("ERROR on loading inner JAR: " + e.getMessageAll());
      }
   }

   private JarEntryInfo findJarEntry(String sName) {
      for(JarFileInfo jarFileInfo : this.lstJarFile) {
         JarFile jarFile = jarFileInfo.jarFile;
         JarEntry jarEntry = jarFile.getJarEntry(sName);
         if (jarEntry != null) {
            return new JarEntryInfo(jarFileInfo, jarEntry);
         }
      }

      return null;
   }

   private List findJarEntries(String sName) {
      List<JarEntryInfo> lst = new ArrayList();

      for(JarFileInfo jarFileInfo : this.lstJarFile) {
         JarFile jarFile = jarFileInfo.jarFile;
         JarEntry jarEntry = jarFile.getJarEntry(sName);
         if (jarEntry != null) {
            lst.add(new JarEntryInfo(jarFileInfo, jarEntry));
         }
      }

      return lst;
   }

   private JarEntryInfo findJarNativeEntry(String sLib) {
      String sName = System.mapLibraryName(sLib);

      for(JarFileInfo jarFileInfo : this.lstJarFile) {
         JarFile jarFile = jarFileInfo.jarFile;
         Enumeration<JarEntry> en = jarFile.entries();

         while(en.hasMoreElements()) {
            JarEntry je = (JarEntry)en.nextElement();
            if (!je.isDirectory()) {
               String sEntry = je.getName();
               String[] token = sEntry.split("/");
               if (token.length > 0 && token[token.length - 1].equals(sName)) {
                  this.logInfo(JarClassLoader.LogArea.NATIVE, "Loading native library '%s' found as '%s' in JAR %s", sLib, sEntry, jarFileInfo.simpleName);
                  return new JarEntryInfo(jarFileInfo, je);
               }
            }
         }
      }

      return null;
   }

   private Class findJarClass(String sClassName) throws JarClassLoaderException {
      Class<?> c = (Class)this.hmClass.get(sClassName);
      if (c != null) {
         return c;
      } else {
         String sName = sClassName.replace('.', '/') + ".class";
         JarEntryInfo inf = this.findJarEntry(sName);
         String jarSimpleName = null;
         if (inf != null) {
            jarSimpleName = inf.jarFileInfo.simpleName;
            this.definePackage(sClassName, inf);
            byte[] a_by = inf.getJarBytes();

            try {
               c = this.defineClass(sClassName, a_by, 0, a_by.length, inf.jarFileInfo.pd);
            } catch (ClassFormatError e) {
               throw new JarClassLoaderException((String)null, e);
            }
         }

         if (c == null) {
            throw new JarClassLoaderException(sClassName);
         } else {
            this.hmClass.put(sClassName, c);
            this.logInfo(JarClassLoader.LogArea.CLASS, "Loaded %s by %s from JAR %s", sClassName, this.getClass().getName(), jarSimpleName);
            return c;
         }
      }
   }

   private void checkShading() {
      if (this.logLevel.ordinal() >= JarClassLoader.LogLevel.WARN.ordinal()) {
         Map<String, JarFileInfo> hm = new HashMap();

         for(JarFileInfo jarFileInfo : this.lstJarFile) {
            JarFile jarFile = jarFileInfo.jarFile;
            Enumeration<JarEntry> en = jarFile.entries();

            while(en.hasMoreElements()) {
               JarEntry je = (JarEntry)en.nextElement();
               if (!je.isDirectory()) {
                  String sEntry = je.getName();
                  if (!"META-INF/MANIFEST.MF".equals(sEntry)) {
                     JarFileInfo jar = (JarFileInfo)hm.get(sEntry);
                     if (jar == null) {
                        hm.put(sEntry, jarFileInfo);
                     } else {
                        this.logWarn(JarClassLoader.LogArea.JAR, "ENTRY %s IN %s SHADES %s", sEntry, jar.simpleName, jarFileInfo.simpleName);
                     }
                  }
               }
            }
         }

      }
   }

   private void shutdown() {
      for(JarFileInfo jarFileInfo : this.lstJarFile) {
         try {
            jarFileInfo.jarFile.close();
         } catch (IOException var4) {
         }

         File file = jarFileInfo.fileDeleteOnExit;
         if (file != null && !file.delete()) {
            this.hsDeleteOnExit.add(file);
         }
      }

      String var10002 = System.getProperty("user.home");
      File fileCfg = new File(var10002 + File.separator + ".JarClassLoader");
      this.deleteOldTemp(fileCfg);
      this.persistNewTemp(fileCfg);
   }

   private void deleteOldTemp(File fileCfg) {
      BufferedReader reader = null;

      try {
         int count = 0;
         reader = new BufferedReader(new FileReader(fileCfg));

         String sLine;
         while((sLine = reader.readLine()) != null) {
            File file = new File(sLine);
            if (file.exists()) {
               if (file.delete()) {
                  ++count;
               } else {
                  this.hsDeleteOnExit.add(file);
               }
            }
         }

         this.logDebug(JarClassLoader.LogArea.CONFIG, "Deleted %d old temp files listed in %s", count, fileCfg.getAbsolutePath());
      } catch (IOException var14) {
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (IOException var13) {
            }
         }

      }

   }

   private void persistNewTemp(File fileCfg) {
      if (this.hsDeleteOnExit.size() == 0) {
         this.logDebug(JarClassLoader.LogArea.CONFIG, "No temp file names to persist on exit.");
         fileCfg.delete();
      } else {
         this.logDebug(JarClassLoader.LogArea.CONFIG, "Persisting %d temp file names into %s", this.hsDeleteOnExit.size(), fileCfg.getAbsolutePath());
         BufferedWriter writer = null;

         try {
            writer = new BufferedWriter(new FileWriter(fileCfg));

            for(File file : this.hsDeleteOnExit) {
               if (!file.delete()) {
                  String f = file.getCanonicalPath();
                  writer.write(f);
                  writer.newLine();
                  this.logWarn(JarClassLoader.LogArea.JAR, "JVM failed to release %s", f);
               }
            }
         } catch (IOException var14) {
         } finally {
            if (writer != null) {
               try {
                  writer.close();
               } catch (IOException var13) {
               }
            }

         }

      }
   }

   public boolean isLaunchedFromJar() {
      return this.lstJarFile.size() > 0;
   }

   public String getManifestMainClass() {
      Attributes attr = null;
      if (this.isLaunchedFromJar()) {
         try {
            Manifest m = ((JarFileInfo)this.lstJarFile.get(0)).jarFile.getManifest();
            attr = m.getMainAttributes();
         } catch (IOException var3) {
         }
      }

      return attr == null ? null : attr.getValue(Name.MAIN_CLASS);
   }

   public void invokeMain(String sClass, String[] args) throws Throwable {
      this.invokeMain(sClass, "main", args);
   }

   public void invokeMain(String sClass, String methodName, String[] args) throws Throwable {
      Class<?> clazz = this.loadClass(sClass);
      CodeVerifier.verify(clazz);
      this.logInfo(JarClassLoader.LogArea.CONFIG, "Launch: %s.main(); Loader: %s", sClass, clazz.getClassLoader());
      Class[] parameterTypes = new Class[]{String[].class};
      Method method = clazz.getMethod(methodName, parameterTypes);
      boolean bValidModifiers = false;
      boolean bValidVoid = false;
      if (method != null) {
         method.setAccessible(true);
         int nModifiers = method.getModifiers();
         bValidModifiers = Modifier.isPublic(nModifiers) && Modifier.isStatic(nModifiers);
         Class<?> clazzRet = method.getReturnType();
         bValidVoid = clazzRet == Void.TYPE;
      }

      if (method != null && bValidModifiers && bValidVoid) {
         try {
            method.invoke((Object)null, args);
         } catch (InvocationTargetException e) {
            throw e.getTargetException();
         }
      } else {
         throw new NoSuchMethodException("The main(String[] args) method in class \"" + sClass + "\" not found.");
      }
   }

   protected synchronized Class loadClass(String sClassName, boolean bResolve) throws ClassNotFoundException {
      this.logDebug(JarClassLoader.LogArea.CLASS, "LOADING %s (resolve=%b)", sClassName, bResolve);
      Thread.currentThread().setContextClassLoader(this);
      Class<?> c = null;

      Class var6;
      try {
         if (!this.getClass().getName().equals(sClassName)) {
            if (this.isLaunchedFromJar()) {
               try {
                  c = this.findJarClass(sClassName);
                  var6 = c;
                  return var6;
               } catch (JarClassLoaderException e) {
                  if (e.getCause() == null) {
                     this.logDebug(JarClassLoader.LogArea.CLASS, "Not found %s in JAR by %s: %s", sClassName, this.getClass().getName(), e.getMessage());
                  } else {
                     this.logDebug(JarClassLoader.LogArea.CLASS, "Error loading %s in JAR by %s: %s", sClassName, this.getClass().getName(), e.getCause());
                  }
               }
            }

            try {
               ClassLoader cl = this.getParent();
               c = cl.loadClass(sClassName);
               this.logInfo(JarClassLoader.LogArea.CLASS, "Loaded %s by %s", sClassName, cl.getClass().getName());
               var6 = c;
               return var6;
            } catch (ClassNotFoundException var10) {
               throw new ClassNotFoundException("Failure to load: " + sClassName);
            }
         }

         var6 = JarClassLoader.class;
      } finally {
         if (c != null && bResolve) {
            this.resolveClass(c);
         }

      }

      return var6;
   }

   protected URL findResource(String sName) {
      this.logDebug(JarClassLoader.LogArea.RESOURCE, "findResource: %s", sName);
      if (this.isLaunchedFromJar()) {
         JarEntryInfo inf = this.findJarEntry(this.normalizeResourceName(sName));
         if (inf != null) {
            URL url = inf.getURL();
            this.logInfo(JarClassLoader.LogArea.RESOURCE, "found resource: %s", url);
            return url;
         } else {
            this.logInfo(JarClassLoader.LogArea.RESOURCE, "not found resource: %s", sName);
            return null;
         }
      } else {
         return super.findResource(sName);
      }
   }

   public Enumeration findResources(String sName) throws IOException {
      this.logDebug(JarClassLoader.LogArea.RESOURCE, "getResources: %s", sName);
      if (this.isLaunchedFromJar()) {
         List<JarEntryInfo> lstJarEntry = this.findJarEntries(this.normalizeResourceName(sName));
         List<URL> lstURL = new ArrayList();

         for(JarEntryInfo inf : lstJarEntry) {
            URL url = inf.getURL();
            if (url != null) {
               lstURL.add(url);
            }
         }

         return Collections.enumeration(lstURL);
      } else {
         return super.findResources(sName);
      }
   }

   protected String findLibrary(String sLib) {
      this.logDebug(JarClassLoader.LogArea.NATIVE, "findLibrary: %s", sLib);
      if (!this.isLaunchedFromJar()) {
         return super.findLibrary(sLib);
      } else {
         JarEntryInfo inf = this.findJarNativeEntry(sLib);
         if (inf != null) {
            try {
               File file = this.createTempFile(inf);
               this.logDebug(JarClassLoader.LogArea.NATIVE, "Loading native library %s from temp file %s", inf.jarEntry, this.getFilename4Log(file));
               this.hsDeleteOnExit.add(file);
               return file.getAbsolutePath();
            } catch (JarClassLoaderException e) {
               this.logError(JarClassLoader.LogArea.NATIVE, "Failure to load native library %s: %s", sLib, e.toString());
            }
         }

         return null;
      }
   }

   private void definePackage(String sClassName, JarEntryInfo inf) throws IllegalArgumentException {
      int pos = sClassName.lastIndexOf(46);
      String sPackageName = pos > 0 ? sClassName.substring(0, pos) : "";
      if (this.getDefinedPackage(sPackageName) == null) {
         JarFileInfo jfi = inf.jarFileInfo;
         this.definePackage(sPackageName, jfi.getSpecificationTitle(), jfi.getSpecificationVersion(), jfi.getSpecificationVendor(), jfi.getImplementationTitle(), jfi.getImplementationVersion(), jfi.getImplementationVendor(), jfi.getSealURL());
      }

   }

   private String normalizeResourceName(String sName) {
      return sName.replace('\\', '/');
   }

   private void chmod777(File file) {
      file.setReadable(true, false);
      file.setWritable(true, false);
      file.setExecutable(true, false);
   }

   private String getFilename4Log(File file) {
      if (this.logger != null) {
         try {
            return file.getCanonicalPath();
         } catch (IOException var3) {
            return file.getAbsolutePath();
         }
      } else {
         return null;
      }
   }

   private void logDebug(LogArea area, String sMsg, Object... obj) {
      this.log(JarClassLoader.LogLevel.DEBUG, area, sMsg, obj);
   }

   private void logInfo(LogArea area, String sMsg, Object... obj) {
      this.log(JarClassLoader.LogLevel.INFO, area, sMsg, obj);
   }

   private void logWarn(LogArea area, String sMsg, Object... obj) {
      this.log(JarClassLoader.LogLevel.WARN, area, sMsg, obj);
   }

   private void logError(LogArea area, String sMsg, Object... obj) {
      this.log(JarClassLoader.LogLevel.ERROR, area, sMsg, obj);
   }

   private void log(LogLevel level, LogArea area, String sMsg, Object... obj) {
      if (level.ordinal() <= this.logLevel.ordinal() && (this.hsLogArea.contains(JarClassLoader.LogArea.ALL) || this.hsLogArea.contains(area))) {
         this.logger.printf("JarClassLoader-" + String.valueOf(level) + ": " + sMsg + "\n", obj);
      }

      if (!this.bLogConsole && level == JarClassLoader.LogLevel.ERROR) {
         System.out.printf("JarClassLoader-" + String.valueOf(level) + ": " + sMsg + "\n", obj);
      }

   }

   public static enum LogLevel {
      ERROR,
      WARN,
      INFO,
      DEBUG;
   }

   public static enum LogArea {
      ALL,
      CONFIG,
      JAR,
      CLASS,
      RESOURCE,
      NATIVE;
   }

   private static class JarFileInfo {
      JarFile jarFile;
      String simpleName;
      File fileDeleteOnExit;
      Manifest mf;
      ProtectionDomain pd;

      JarFileInfo(JarFile jarFile, String simpleName, JarFileInfo jarFileParent, ProtectionDomain pd, File fileDeleteOnExit) {
         String var10001 = jarFileParent == null ? "" : jarFileParent.simpleName + "!";
         this.simpleName = var10001 + simpleName;
         this.jarFile = jarFile;
         this.pd = pd;
         this.fileDeleteOnExit = fileDeleteOnExit;

         try {
            this.mf = jarFile.getManifest();
         } catch (IOException var7) {
         }

         if (this.mf == null) {
            this.mf = new Manifest();
         }

      }

      String getSpecificationTitle() {
         return this.mf.getMainAttributes().getValue(Name.SPECIFICATION_TITLE);
      }

      String getSpecificationVersion() {
         return this.mf.getMainAttributes().getValue(Name.SPECIFICATION_VERSION);
      }

      String getSpecificationVendor() {
         return this.mf.getMainAttributes().getValue(Name.SPECIFICATION_VENDOR);
      }

      String getImplementationTitle() {
         return this.mf.getMainAttributes().getValue(Name.IMPLEMENTATION_TITLE);
      }

      String getImplementationVersion() {
         return this.mf.getMainAttributes().getValue(Name.IMPLEMENTATION_VERSION);
      }

      String getImplementationVendor() {
         return this.mf.getMainAttributes().getValue(Name.IMPLEMENTATION_VENDOR);
      }

      URL getSealURL() {
         String seal = this.mf.getMainAttributes().getValue(Name.SEALED);
         if (seal != null) {
            try {
               return new URL(seal);
            } catch (MalformedURLException var3) {
            }
         }

         return null;
      }
   }

   private static class JarEntryInfo {
      JarFileInfo jarFileInfo;
      JarEntry jarEntry;

      JarEntryInfo(JarFileInfo jarFileInfo, JarEntry jarEntry) {
         this.jarFileInfo = jarFileInfo;
         this.jarEntry = jarEntry;
      }

      URL getURL() {
         try {
            String var10002 = this.jarFileInfo.jarFile.getName();
            return new URL("jar:file:" + var10002 + "!/" + String.valueOf(this.jarEntry));
         } catch (MalformedURLException var2) {
            return null;
         }
      }

      String getName() {
         return this.jarEntry.getName().replace('/', '_');
      }

      public String toString() {
         String var10000 = this.jarFileInfo.jarFile.getName();
         return "JAR: " + var10000 + " ENTRY: " + String.valueOf(this.jarEntry);
      }

      byte[] getJarBytes() throws JarClassLoaderException {
         DataInputStream dis = null;
         byte[] a_by = null;

         try {
            long lSize = this.jarEntry.getSize();
            if (lSize <= 0L || lSize >= 2147483647L) {
               throw new JarClassLoaderException("Invalid size " + lSize + " for entry " + String.valueOf(this.jarEntry));
            }

            a_by = new byte[(int)lSize];
            InputStream is = this.jarFileInfo.jarFile.getInputStream(this.jarEntry);
            dis = new DataInputStream(is);
            dis.readFully(a_by);
         } catch (IOException e) {
            throw new JarClassLoaderException((String)null, e);
         } finally {
            if (dis != null) {
               try {
                  dis.close();
               } catch (IOException var12) {
               }
            }

         }

         return a_by;
      }
   }

   private static class JarClassLoaderException extends Exception {
      JarClassLoaderException(String sMsg) {
         super(sMsg);
      }

      JarClassLoaderException(String sMsg, Throwable eCause) {
         super(sMsg, eCause);
      }

      String getMessageAll() {
         StringBuilder sb = new StringBuilder();

         for(Throwable e = this; e != null; e = e.getCause()) {
            if (sb.length() > 0) {
               sb.append(" / ");
            }

            String sMsg = e.getMessage();
            if (sMsg == null || sMsg.length() == 0) {
               sMsg = e.getClass().getSimpleName();
            }

            sb.append(sMsg);
         }

         return sb.toString();
      }
   }
}
