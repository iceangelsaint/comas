package edu.carleton.cas.resources;

import edu.carleton.cas.background.LogArchiver;
import edu.carleton.cas.constants.ClientShared;
import java.util.logging.Level;

public class Terminal extends Thread {
   private static final String[][] startCmd = new String[][]{{"open", "-a", "Terminal"}, {"rundll32", "url.dll,FileProtocolHandler", "cmd.exe"}, {"x-terminal-emulator"}};
   private String[] cmd;
   private CommandRunner runner;
   private LogArchiver logger;

   public Terminal() {
      this((LogArchiver)null, new NullOutputProcessor());
   }

   public Terminal(LogArchiver logger, OutputProcessor op) {
      if (ClientShared.isMacOS()) {
         this.cmd = startCmd[0];
      } else if (ClientShared.isWindowsOS()) {
         this.cmd = startCmd[1];
      } else {
         this.cmd = startCmd[2];
      }

      this.logger = logger;
      this.runner = new CommandRunner(this.cmd, op);
      this.setName("terminal");
   }

   public void run() {
      edu.carleton.cas.logging.Logger.output(toString(this.cmd));

      try {
         this.runner.run();
      } catch (Exception e) {
         if (this.logger != null) {
            LogArchiver var7 = this.logger;
            Level var8 = edu.carleton.cas.logging.Level.DIAGNOSTIC;
            String var10002 = toString(this.cmd);
            var7.put(var8, "Failed to run " + var10002 + ": " + String.valueOf(e));
         } else {
            Level var10000 = Level.INFO;
            String var10001 = toString(this.cmd);
            edu.carleton.cas.logging.Logger.debug(var10000, "Failed to run " + var10001 + ": " + String.valueOf(e));
         }
      } finally {
         this.close();
      }

   }

   public void close() {
      if (this.runner != null) {
         this.runner.close();
         this.runner = null;
      }

   }

   private static String toString(String[] args) {
      StringBuffer sb = new StringBuffer();

      for(String arg : args) {
         sb.append(arg);
         sb.append(' ');
      }

      return sb.toString();
   }

   public static void main(String[] args) {
      Terminal fe = new Terminal();
      fe.start();

      try {
         Thread.sleep(5000L);
      } catch (InterruptedException var6) {
      } finally {
         fe.close();
      }

   }
}
