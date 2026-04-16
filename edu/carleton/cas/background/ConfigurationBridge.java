package edu.carleton.cas.background;

import java.util.Properties;

public final class ConfigurationBridge implements Configuration {
   private final Properties properties;

   public ConfigurationBridge(Properties properties) {
      this.properties = properties;
   }

   public String get(String key) {
      return this.properties.getProperty(key);
   }
}
