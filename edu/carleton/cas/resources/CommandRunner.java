package edu.carleton.cas.resources;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public class CommandRunner implements Runnable {
   String[] command;
   OutputProcessor p;
   Process process;
   File folder;

   public CommandRunner(String[] command) {
      this(command, new NullOutputProcessor());
   }

   public CommandRunner(String[] command, OutputProcessor p) {
      this(command, p, (File)null);
   }

   public CommandRunner(String[] command, OutputProcessor p, File folder) {
      this.command = command;
      this.p = p;
      this.process = null;
      this.folder = folder;
   }

   public void runWithFileRedirection() {
      this.process = null;
      FileProcessor fp = (FileProcessor)this.p;

      try {
         ProcessBuilder builder = new ProcessBuilder(this.command);
         if (this.folder != null) {
            builder.directory(this.folder);
         }

         builder.redirectErrorStream(true);
         builder.redirectInput(fp.getInputFile());
         builder.redirectOutput(fp.getOutputFile());
         this.process = builder.start();
      } catch (Exception e) {
         if (this.p != null && this.p instanceof ExceptionProcessor) {
            ExceptionProcessor ep = (ExceptionProcessor)this.p;
            ep.onException(e);
         }
      } finally {
         this.runFinalization();
      }

   }

   public void runWithOutputRedirection() {
      this.process = null;

      try {
         ProcessBuilder builder = new ProcessBuilder(this.command);
         builder.redirectErrorStream(true);
         if (this.folder != null) {
            builder.directory(this.folder);
         }

         this.process = builder.start();
         InputStream stdout = this.process.getInputStream();
         BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));

         String line;
         while((line = reader.readLine()) != null) {
            if (this.p != null) {
               this.p.process(line);
            }
         }
      } catch (Exception e) {
         if (this.p != null && this.p instanceof ExceptionProcessor) {
            ExceptionProcessor ep = (ExceptionProcessor)this.p;
            ep.onException(e);
         }
      } finally {
         this.runFinalization();
      }

   }

   private void runFinalization() {
      if (this.process != null) {
         try {
            this.process.waitFor();
         } catch (InterruptedException var2) {
         }

         this.process = null;
      }

      if (this.p != null) {
         this.p.asyncResult();
      }

   }

   public void run() {
      if (this.p instanceof FileProcessor) {
         this.runWithFileRedirection();
      } else {
         this.runWithOutputRedirection();
      }

   }

   public void close() {
      if (this.process != null && this.process.isAlive()) {
         this.process.destroyForcibly();
      }

   }
}
