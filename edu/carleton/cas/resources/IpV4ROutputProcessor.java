package edu.carleton.cas.resources;

import java.util.Scanner;

public class IpV4ROutputProcessor implements OutputProcessor {
   String gateway;

   IpV4ROutputProcessor() {
   }

   public void process(String line) {
      if (line.startsWith("default")) {
         Scanner s = new Scanner(line);
         s.next();
         s.next();
         this.gateway = s.next();
         s.close();
      }

   }

   public String result() {
      return this.gateway;
   }

   public void asyncResult() {
   }
}
