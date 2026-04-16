package edu.carleton.cas.resources;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

public class PasswordOutputProcessor implements FileProcessor, Closeable {
   public static final String OK = "Okay";
   public static final String NOT_OK = "Not okay";
   File inputFile;
   File outputFile;

   public PasswordOutputProcessor(String password) throws IOException {
      this.inputFile = this.createTempInputFile(password);
      this.outputFile = File.createTempFile("pop", ".txt");
   }

   public PasswordOutputProcessor(File inputFile) throws IOException {
      this(inputFile, File.createTempFile("pop", ".txt"));
   }

   public PasswordOutputProcessor(File inputFile, File outputFile) {
      this.inputFile = inputFile;
      this.outputFile = outputFile;
   }

   private File createTempInputFile(String password) throws IOException {
      File file = File.createTempFile("pip", ".txt");
      PrintWriter pw = new PrintWriter(file);
      pw.println(password);
      pw.close();
      return file;
   }

   public void process(String line) {
   }

   public void asyncResult() {
   }

   public String result() {
      BufferedReader reader = null;
      String rtn = "Okay";

      try {
         reader = new BufferedReader(new FileReader(this.outputFile));

         String line;
         while((line = reader.readLine()) != null) {
            if (line.contains("Sorry")) {
               rtn = "Not okay";
            }
         }
      } catch (Exception var13) {
         rtn = "Not okay";
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (IOException var12) {
            }
         }

      }

      return rtn;
   }

   public boolean isOkay() {
      return this.result() == "Okay";
   }

   public File getInputFile() {
      return this.inputFile;
   }

   public File getOutputFile() {
      return this.outputFile;
   }

   public void close() throws IOException {
      if (this.inputFile != null) {
         this.inputFile.delete();
         this.inputFile = null;
      }

      if (this.outputFile != null) {
         this.outputFile.delete();
         this.outputFile = null;
      }

   }
}
