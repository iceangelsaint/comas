package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.modules.foundation.JarClassLoader;
import java.net.URL;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ServletProcessorClassLoader extends ClassLoader {
   private static final Level LOGGING_LEVEL;
   private static final HashSet hiddenClasses;
   private final ConcurrentHashMap classes = new ConcurrentHashMap();
   private final JarClassLoader classLoader;

   static {
      LOGGING_LEVEL = Level.INFO;
      hiddenClasses = new HashSet();
   }

   public ServletProcessorClassLoader(URL url, String token) {
      this.classLoader = new JarClassLoader(url, this.getClass().getClassLoader(), token);
   }

   protected Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
      for(String hiddenClassName : hiddenClasses) {
         if (name.startsWith(hiddenClassName)) {
            throw new ClassNotFoundException(String.format("Illegal class access: %s", name));
         }
      }

      Class<?> clazz;
      try {
         clazz = super.loadClass(name, resolve);
      } catch (ClassNotFoundException var5) {
         clazz = this.classLoader.loadClass(name);
         if (resolve) {
            this.resolveClass(clazz);
         }
      }

      return clazz;
   }

   protected Class findClass(String name) throws ClassNotFoundException {
      Class<?> clazz;
      if (this.classes.containsKey(name)) {
         Logger.log(LOGGING_LEVEL, "Loading: " + name, " -- cached");
         clazz = (Class)this.classes.get(name);
      } else {
         try {
            clazz = this.findSystemClass(name);
            Logger.log(LOGGING_LEVEL, "Loading: " + name, " -- local");
         } catch (ClassNotFoundException var4) {
            clazz = this.classLoader.loadClass(name);
            Logger.log(LOGGING_LEVEL, "Loading: " + name, " -- remote");
            this.classes.put(name, clazz);
         }
      }

      return clazz;
   }

   public void addHiddenClass(String name) {
      hiddenClasses.add(name);
   }

   public void clearHidden() {
      hiddenClasses.clear();
   }

   public void clearClasses() {
      this.classes.clear();
   }
}
