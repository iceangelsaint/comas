package edu.carleton.cas.resources;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LineContainingPatternProcessor implements OutputProcessor {
   StringBuffer sb = new StringBuffer();
   Pattern regex;

   public LineContainingPatternProcessor(String regex) {
      this.regex = Pattern.compile(regex);
   }

   public void process(String line) {
      Matcher m = this.regex.matcher(line);
      if (m.matches()) {
         this.sb.append(line);
         this.sb.append("\n");
      }

   }

   public String result() {
      return this.sb.length() == 0 ? "" : this.sb.toString();
   }

   public void asyncResult() {
   }
}
