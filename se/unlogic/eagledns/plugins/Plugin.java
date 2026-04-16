package se.unlogic.eagledns.plugins;

import se.unlogic.eagledns.SystemInterface;

public interface Plugin {
   void init(String var1) throws Exception;

   void setSystemInterface(SystemInterface var1);

   void shutdown() throws Exception;
}
