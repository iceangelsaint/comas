package edu.carleton.cas.utility;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Scanner;

public class WindowsAdmin {
   public static boolean isAdmin() {
      Scanner scanner = null;

      try {
         ProcessBuilder processBuilder = new ProcessBuilder(new String[]{"cmd.exe"});
         Process process = processBuilder.start();
         PrintStream printStream = new PrintStream(process.getOutputStream(), true);
         scanner = new Scanner(process.getInputStream());
         printStream.println("@echo off");
         printStream.println(">nul 2>&1 \"%SYSTEMROOT%\\system32\\cacls.exe\" \"%SYSTEMROOT%\\system32\\config\\system\"");
         printStream.println("echo %errorlevel%");
         boolean printedErrorlevel = false;

         while(true) {
            String nextLine = scanner.nextLine();
            if (printedErrorlevel) {
               int errorlevel = Integer.parseInt(nextLine);
               boolean var8 = errorlevel == 0;
               return var8;
            }

            if (nextLine.equals("echo %errorlevel%")) {
               printedErrorlevel = true;
            }
         }
      } catch (IOException var11) {
      } finally {
         if (scanner != null) {
            scanner.close();
         }

      }

      return false;
   }
}
