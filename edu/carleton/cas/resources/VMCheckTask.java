package edu.carleton.cas.resources;

import edu.carleton.cas.constants.ClientShared;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class VMCheckTask extends AbstractTask {
   private final String os = ClientShared.getOSString();
   private boolean detailTestHasBeenRun;
   private boolean detailTestResult;
   private Pattern[] falsePositivePatterns;

   public VMCheckTask(Logger logger, ResourceMonitor monitor) {
      super(logger, monitor);
      if (!this.os.equals("windows")) {
         this.detailTestHasBeenRun = true;
         this.detailTestResult = true;
      } else {
         this.detailTestHasBeenRun = false;
      }

      this.init();
   }

   public void init() {
      ArrayList<Pattern> fpps = new ArrayList();
      if (this.os.equals("windows")) {
         fpps.add(Pattern.compile("^[Mm]icrosoft [Cc]orporation$"));
         fpps.add(Pattern.compile("^System Model:.*Microsoft.*Surface.*Edition$"));
      }

      int i = 1;
      String base = "vm." + this.os + ".false_positive.pattern.";

      for(String pattern = this.monitor.getProperty(base + i); pattern != null; pattern = this.monitor.getProperty(base + i)) {
         try {
            Pattern fpp = Pattern.compile(pattern.trim());
            fpps.add(fpp);
         } catch (PatternSyntaxException e) {
            String var10002 = this.os;
            this.monitor.notifyListeners("exception", var10002 + " false positive pattern: " + String.valueOf(e));
         }

         ++i;
      }

      this.falsePositivePatterns = (Pattern[])fpps.toArray(new Pattern[fpps.size()]);
   }

   public boolean isIllegal(String line) {
      if (line == null) {
         return false;
      } else {
         String toCheck = line.toLowerCase();
         int i = 1;
         String base = "vm.vendor.";

         for(String vendor = this.monitor.getProperty(base + i); vendor != null; vendor = this.monitor.getProperty(base + i)) {
            if (toCheck.contains(vendor.trim())) {
               return true;
            }

            ++i;
         }

         return false;
      }
   }

   public void run() {
      try {
         if (this.os.equals("unknown")) {
            this.monitor.notifyListeners("exception", "Unknown operating system detected");
         } else {
            int i = 1;
            String base = "vm." + this.os + ".";

            for(String cmd = this.monitor.getProperty(base + i); cmd != null; cmd = this.monitor.getProperty(base + i)) {
               edu.carleton.cas.logging.Logger.log(Level.FINE, "VM test: ", cmd);
               this.runTest(cmd.trim(), i);
               ++i;
            }
         }
      } finally {
         this.monitor.close();
      }

   }

   private void runTest(String cmd, int testNumber) {
      try {
         this.process = Runtime.getRuntime().exec(cmd);
         InputStream stdout = this.process.getInputStream();
         BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));

         String line;
         while((line = reader.readLine()) != null) {
            if (this.isIllegal(line) && !this.isFalsePositive(line) && this.detailedTestToAvoidFalsePositives()) {
               this.monitor.notifyListeners("vm", line);
            }
         }
      } catch (Exception var9) {
         this.monitor.notifyListeners("exception", this.os + " test: " + testNumber);
      } finally {
         this.close();
      }

   }

   private boolean isFalsePositive(String line) {
      Pattern[] var5;
      for(Pattern pattern : var5 = this.falsePositivePatterns) {
         Matcher matcher = pattern.matcher(line);
         if (matcher.matches()) {
            this.detailTestHasBeenRun = true;
            this.detailTestResult = false;
            return true;
         }
      }

      return false;
   }

   private boolean detailedTestToAvoidFalsePositives() {
      if (this.detailTestHasBeenRun) {
         return this.detailTestResult;
      } else {
         Process p = null;

         label104: {
            try {
               p = Runtime.getRuntime().exec("cmd /c wmic csproduct get vendor");
               InputStream stdout = p.getInputStream();
               BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));

               String line;
               do {
                  if ((line = reader.readLine()) == null) {
                     this.detailTestHasBeenRun = true;
                     break label104;
                  }
               } while(!line.trim().toLowerCase().equals("microsoft corporation"));

               this.detailTestHasBeenRun = true;
               this.detailTestResult = false;
            } catch (IOException var8) {
               this.monitor.notifyListeners("exception", this.os + " detailed test");
               break label104;
            } finally {
               if (p != null && p.isAlive()) {
                  p.destroyForcibly();
               }

            }

            return false;
         }

         this.detailTestResult = true;
         return true;
      }
   }
}
