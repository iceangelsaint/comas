package edu.carleton.cas.resources;

public class IpV6ROutputProcessor implements OutputProcessor {
   String gateway;

   IpV6ROutputProcessor() {
   }

   public void process(String line) {
      if (line.contains(" via ")) {
         String[] tokens = line.split(" ");
         if (tokens.length > 3) {
            this.gateway = tokens[2].trim();
         }
      }

   }

   public String result() {
      return this.gateway == null ? "" : this.gateway;
   }

   public void asyncResult() {
   }
}
