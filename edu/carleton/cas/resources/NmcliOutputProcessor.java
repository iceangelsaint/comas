package edu.carleton.cas.resources;

public class NmcliOutputProcessor implements OutputProcessor {
   String servers;
   boolean next = false;

   NmcliOutputProcessor() {
   }

   public void process(String line) {
      if (line.startsWith("DNS")) {
         this.next = true;
      } else if (this.next) {
         String[] tokens = line.split(":");
         if (tokens != null && tokens.length > 1) {
            this.servers = tokens[1].trim();
            this.next = false;
         }
      }

   }

   public String result() {
      return this.servers == null ? "" : this.servers;
   }

   public void asyncResult() {
   }
}
