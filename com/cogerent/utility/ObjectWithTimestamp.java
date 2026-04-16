package com.cogerent.utility;

public class ObjectWithTimestamp {
   public Object object;
   public long timestamp;
   public int count;

   public ObjectWithTimestamp(Object object) {
      this(object, System.currentTimeMillis());
   }

   public ObjectWithTimestamp(Object object, long timestamp) {
      this(object, timestamp, 1);
   }

   public ObjectWithTimestamp(Object object, long timestamp, int count) {
      this.object = object;
      this.timestamp = timestamp;
      this.count = count;
   }

   public boolean isValid() {
      return this.object != null && this.timestamp > 0L;
   }
}
