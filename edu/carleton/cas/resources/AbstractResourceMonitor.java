package edu.carleton.cas.resources;

import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class AbstractResourceMonitor implements Resource {
   protected final CopyOnWriteArraySet listeners = new CopyOnWriteArraySet();
   protected final String type;
   protected final HashSet cache;

   protected AbstractResourceMonitor(String type) {
      this.type = type;
      this.cache = new HashSet();
   }

   public void clear() {
      this.cache.clear();
   }

   public void notifyListenersIfNotCached(String type, String description) {
      if (!this.cache.contains(description)) {
         this.notifyListeners(type, description);
         this.cache.add(description);
      }

   }

   public void notifyListeners(String type, String description) {
      for(ResourceListener l : this.listeners) {
         l.resourceEvent(this, type, description);
      }

   }

   public void addListener(ResourceListener listener) {
      this.listeners.add(listener);
   }

   public void removeListener(ResourceListener listener) {
      this.listeners.remove(listener);
   }

   public String getResourceType() {
      return this.type;
   }
}
