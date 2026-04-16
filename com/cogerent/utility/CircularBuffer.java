package com.cogerent.utility;

public class CircularBuffer {
   private Object[] buffer;
   private long[] timestamp;
   private boolean[] marked;
   private int head;
   private int tail;
   private int size;
   private int capacity;

   public CircularBuffer(int capacity) {
      this.capacity = capacity;
      this.buffer = new Object[capacity];
      this.timestamp = new long[capacity];
      this.marked = new boolean[capacity];
      this.head = 0;
      this.tail = 0;
      this.size = 0;
   }

   public Object before(long _timestamp) {
      for(int i = 0; i < this.size; ++i) {
         int j = (this.head + i) % this.capacity;
         if (this.timestamp[j] >= _timestamp) {
            if (i == 0) {
               return null;
            }

            if (j == 0) {
               j = this.capacity - 1;
            } else {
               j = (j - 1) % this.capacity;
            }

            this.marked[j] = true;
            return this.buffer[j];
         }
      }

      return null;
   }

   public void add(Object element) {
      this.marked[this.tail] = false;
      this.timestamp[this.tail] = System.currentTimeMillis();
      this.buffer[this.tail] = element;
      this.tail = (this.tail + 1) % this.capacity;
      if (this.size < this.capacity) {
         ++this.size;
      } else {
         this.head = (this.head + 1) % this.capacity;
      }

   }

   public Object remove() {
      if (this.size == 0) {
         return null;
      } else {
         T element = (T)this.buffer[this.head];
         this.buffer[this.head] = null;
         this.marked[this.head] = false;
         this.timestamp[this.head] = Long.MAX_VALUE;
         this.head = (this.head + 1) % this.capacity;
         --this.size;
         return element;
      }
   }

   public boolean isEmpty() {
      return this.size == 0;
   }

   public boolean isFull() {
      return this.size == this.capacity;
   }

   public int size() {
      return this.size;
   }
}
