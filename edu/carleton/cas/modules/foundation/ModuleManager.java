package edu.carleton.cas.modules.foundation;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.modules.CommunicatingModule;
import edu.carleton.cas.modules.Module;
import edu.carleton.cas.modules.ModuleConfigurationFactory;
import edu.carleton.cas.modules.ModuleManagerInterface;
import edu.carleton.cas.modules.exceptions.ModuleException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModuleManager implements ModuleManagerInterface {
   private static ModuleManager instance = null;
   private final ConcurrentHashMap modules;
   private final ModuleClassLoader loader;
   private final ModuleConfigurationImplementation configuration;
   private final ThreadPoolExecutor tpe;
   private boolean actionInBackground;
   private JarClassLoader[] classLoaders;
   private String token;

   public ModuleManager() {
      this(new URL[0]);
   }

   public ModuleManager(URL[] urls) {
      this.configuration = new ModuleConfigurationImplementation();
      this.loader = new ModuleClassLoader(urls, Module.class.getClassLoader());
      this.modules = new ConcurrentHashMap();
      this.tpe = new ThreadPoolExecutor(1, 1, 1L, TimeUnit.HOURS, new LinkedBlockingQueue());
      this.actionInBackground = false;
   }

   public static ModuleManager create() {
      if (instance == null) {
         instance = new ModuleManager();
         ModuleConfigurationFactory.setDefault(instance.configuration);
      }

      return instance;
   }

   public ClassLoader getLoader() {
      return this.loader;
   }

   public void setToken(String token) {
      this.token = token;
   }

   public String getToken() {
      return this.token;
   }

   public void stop() {
      Enumeration<ModuleContainer> emc = this.modules.elements();

      while(emc.hasMoreElements()) {
         ModuleContainer mc = (ModuleContainer)emc.nextElement();

         try {
            this.execute(mc, ModuleAction.stop);
            Logger.log(Level.CONFIG, String.format("Stopped module %s using %s", mc.getName(), mc.getModule().getClass()), "");
         } catch (Exception e) {
            Logger.log(Level.WARNING, String.format("Exception while stopping module %s using %s ", mc.getName(), mc.getModule().getClass()), e.toString());
         }
      }

      try {
         this.loader.close();
      } catch (IOException var4) {
      }

      this.tpe.shutdownNow();
   }

   public ModuleContainer load(String name, String className, JarClassLoader classLoader) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
      if (this.modules.contains(name)) {
         Logger.log(Level.WARNING, String.format("Module named %s is already defined. It cannot be instantiated for %s", name, className), "");
         throw new InstantiationException(String.format("Module named %s already defined", name));
      } else {
         ModuleContainer module = null;
         Class<?> clazz = this.loader.findClass(className);
         if (clazz != null) {
            module = this.instantiate(name, clazz);
            this.modules.put(name, module);
            return module;
         } else {
            ClassNotFoundException cnfe = new ClassNotFoundException("Unable to load " + className);
            Logger.log(Level.WARNING, String.format("Unable to load %s using %s: ", name, className), cnfe.getClass().getSimpleName());
            throw cnfe;
         }
      }
   }

   public boolean unload(ModuleContainer module) {
      try {
         this.execute(module, ModuleAction.stop);
         this.modules.remove(module.getName());
         return true;
      } catch (Exception e) {
         Logger.log(Level.WARNING, String.format("Exception while unloading module %s using %s: ", module.getName(), module.getModule().getClass()), e.toString());
         return false;
      }
   }

   public boolean unload(String name) {
      ModuleContainer mc = (ModuleContainer)this.modules.get(name);
      return mc != null ? this.unload(mc) : false;
   }

   private ModuleContainer instantiate(String name, Class clazz) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
      ModuleContainer module = new ModuleContainer(name, (Module)clazz.getDeclaredConstructor().newInstance(), this);
      this.execute(module, ModuleAction.init);
      return module;
   }

   public void addURL(URL url) {
      this.loader.addURL(url);
   }

   public void addSharedProperty(String name, Object value) {
      if (name != null && value != null) {
         this.configuration.setProperty(name, value);
      }
   }

   public void execute(ModuleContainer moduleContainer, ModuleAction action) {
      if (this.actionInBackground) {
         this.tpe.execute(new ModuleContainerProcessor(moduleContainer, action));
      } else {
         try {
            moduleContainer.execute(action);
         } catch (Exception e) {
            Logger.log(Level.FINE, String.format("%s module %s action exception: ", moduleContainer.getName(), action), e);
         }
      }

   }

   public void configure(Properties properties) throws MalformedURLException, ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
      Logger.log(Level.INFO, "Loading modules", "");
      String number = properties.getProperty("module.pool.size", "1");

      try {
         int actualNumber = Integer.parseInt(number.trim());
         if (actualNumber < 1) {
            actualNumber = 1;
         }

         this.tpe.setMaximumPoolSize(actualNumber);
      } catch (NumberFormatException var15) {
         Logger.log(Level.WARNING, String.format("module.pool.size has an illegal number format: %s", number), "");
      }

      number = properties.getProperty("module.pool.core", "1");

      try {
         int actualNumber = Integer.parseInt(number.trim());
         if (actualNumber > this.tpe.getMaximumPoolSize()) {
            actualNumber = this.tpe.getMaximumPoolSize();
         }

         if (actualNumber < 1) {
            actualNumber = 1;
         }

         this.tpe.setCorePoolSize(actualNumber);
      } catch (NumberFormatException var14) {
         Logger.log(Level.WARNING, String.format("module.pool.core has an illegal number format: %s", number), "");
      }

      number = properties.getProperty("module.pool.timeout", "60");

      try {
         int actualNumber = Integer.parseInt(number.trim());
         if (actualNumber < 0) {
            actualNumber = 60;
         }

         this.tpe.setKeepAliveTime((long)actualNumber, TimeUnit.MINUTES);
      } catch (NumberFormatException var13) {
         Logger.log(Level.WARNING, String.format("module.pool.timeout has an illegal number format: %s", number), "");
      }

      number = properties.getProperty("module.background", "false").trim();
      if (number.equalsIgnoreCase("true")) {
         this.actionInBackground = true;
      } else if (number.equalsIgnoreCase("false")) {
         this.actionInBackground = false;
      } else {
         Logger.log(Level.WARNING, String.format("module.background has an illegal boolean format: %s", number), "");
      }

      for(Map.Entry entry : properties.entrySet()) {
         this.configuration.setProperty((String)entry.getKey(), entry.getValue());
      }

      this.configuration.setProperty("manager", new ModuleManagerBridge(this));
      int i = 1;

      for(String urlProp = properties.getProperty("module.load.url." + i); urlProp != null; urlProp = properties.getProperty("module.load.url." + i)) {
         urlProp = urlProp.trim();
         if (urlProp.startsWith("/")) {
            urlProp = ClientShared.service(ClientShared.PROTOCOL, ClientShared.CMS_HOST, ClientShared.PORT, urlProp);
         }

         try {
            this.addURL(new URL(urlProp));
         } catch (MalformedURLException urlException) {
            Logger.log(Level.WARNING, String.format("module.load.url.%d has an illegal URL format: %s", i, urlProp), "");
            throw urlException;
         }

         ++i;
      }

      URL[] urls = this.loader.getURLs();
      this.classLoaders = new JarClassLoader[urls.length];

      for(int index = 0; index < urls.length; ++index) {
         this.classLoaders[index] = new JarClassLoader(urls[index], this.loader, this.token);
      }

      i = 1;
      String hiddenClassProp = properties.getProperty("module.load.hidden." + i);

      for(Pattern p = Pattern.compile("([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*"); hiddenClassProp != null; hiddenClassProp = properties.getProperty("module.load.hidden." + i)) {
         hiddenClassProp = hiddenClassProp.trim();
         Matcher m = p.matcher(hiddenClassProp);
         if (m.matches()) {
            this.loader.addHiddenClass(hiddenClassProp);
         } else {
            Logger.log(Level.WARNING, String.format("module.load.hidden.%d has an illegal class name format: %s", i, hiddenClassProp), "");
         }

         ++i;
      }

      i = 1;

      for(String module = properties.getProperty("module.load." + i); module != null; module = properties.getProperty("module.load." + i)) {
         module = module.trim();
         String[] definition = module.split("[:,]");
         ModuleContainer mc;
         if (definition != null && definition.length == 1) {
            mc = this.load(definition[0].trim(), definition[0].trim(), this.classLoaders[i - 1]);
         } else {
            if (definition == null || definition.length != 2) {
               Logger.log(Level.WARNING, String.format("module.load.%d has an illegal specification format: %s", i, module), "");
               throw new InstantiationException("Module properties are incorrectly defined: " + module);
            }

            mc = this.load(definition[0].trim(), definition[1].trim(), this.classLoaders[i - 1]);
         }

         if (mc != null) {
            this.execute(mc, ModuleAction.start);
            Logger.log(Level.INFO, String.format("Started module %s using %s", mc.getName(), mc.getModule().getClass()), "");
         }

         ++i;
      }

   }

   public Module find(String name) {
      ModuleContainer mc = (ModuleContainer)this.modules.get(name);
      return mc != null ? mc.getModule() : null;
   }

   public void send(String from, String to, String message) throws ModuleException {
      ModuleContainer mc = (ModuleContainer)this.modules.get(to);
      if (mc != null) {
         Class<?> clazz = mc.getModule().getClass();
         if (CommunicatingModule.class.isAssignableFrom(clazz)) {
            CommunicatingModule cm = (CommunicatingModule)mc.getModule();

            try {
               this.tpe.execute(new MessageProcessor(cm, from, message));
            } catch (Exception e) {
               String msg = String.format("Send from %s to %s error: ", from, to);
               Logger.log(Level.WARNING, msg, e);
               throw new ModuleException(msg, e);
            }
         } else {
            Logger.log(Level.WARNING, to, " is not a communicating module");
            throw new ModuleException(to + " is not a communicating module", new ClassCastException(clazz.getSimpleName()));
         }
      } else {
         Logger.log(Level.FINE, to, " not found");
         throw new ModuleException(to + " not found", new NullPointerException());
      }
   }

   private class MessageProcessor implements Runnable {
      private CommunicatingModule module;
      private String from;
      private String message;

      MessageProcessor(CommunicatingModule module, String from, String message) {
         this.module = module;
         this.from = from;
         this.message = message;
      }

      public void run() {
         try {
            this.module.receive(this.from, this.message);
         } catch (Exception e) {
            Logger.log(Level.FINE, String.format("%s %s: ", this.from, this.message), e);
         }

      }
   }

   private class ModuleContainerProcessor implements Runnable {
      private ModuleContainer moduleContainer;
      private ModuleAction action;

      ModuleContainerProcessor(ModuleContainer moduleContainer, ModuleAction action) {
         this.moduleContainer = moduleContainer;
         this.action = action;
      }

      public void run() {
         try {
            this.moduleContainer.execute(this.action);
         } catch (Exception e) {
            Logger.log(Level.FINE, String.format("%s module %s action exception: ", this.moduleContainer.getName(), this.action), e);
         }

      }
   }
}
