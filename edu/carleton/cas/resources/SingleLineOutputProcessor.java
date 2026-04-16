package edu.carleton.cas.resources;

public class SingleLineOutputProcessor implements OutputProcessor {
   String result;

   public void process(String line) {
      this.result = line.trim();
   }

   public String result() {
      return this.result == null ? "" : this.result;
   }

   public void asyncResult() {
   }
}
