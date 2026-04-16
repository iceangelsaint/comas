package edu.carleton.cas.resources;

public interface Resource {
   void addListener(ResourceListener var1);

   void removeListener(ResourceListener var1);

   void open();

   void close();

   void clear();

   String getResourceType();

   void notifyListeners(String var1, String var2);

   void notifyListenersIfNotCached(String var1, String var2);
}
