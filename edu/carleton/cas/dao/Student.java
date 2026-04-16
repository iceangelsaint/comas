package edu.carleton.cas.dao;

import edu.carleton.cas.utility.ClientConfiguration;
import edu.carleton.cas.utility.Named;

public final class Student {
   public final String first;
   public final String last;
   public final String canonicalName;
   public final String id;

   public Student(String first, String last, String id) {
      this.first = first.toLowerCase().trim();
      this.last = last.toLowerCase().trim();
      this.canonicalName = Named.canonical(this.getName());
      this.id = id;
   }

   public Student(ClientConfiguration cc) {
      this(cc.getFirst(), cc.getLast(), cc.getID());
   }

   public String getName() {
      return this.first + " " + this.last;
   }

   public String toString() {
      String var10000 = this.getName();
      return var10000 + " (" + this.id + ")";
   }
}
