package edu.carleton.cas.resources;

public class AllLinesOutputProcessor implements OutputProcessor {
   StringBuffer sb = new StringBuffer();

   public void process(String line) {
      this.sb.append(line);
      this.sb.append("\n");
   }

   public String result() {
      return this.sb.length() == 0 ? "" : this.sb.toString();
   }

   public void asyncResult() {
   }
}
