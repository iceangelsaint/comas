package edu.carleton.cas.utility;

import edu.carleton.cas.logging.Logger;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;

public class HTMLFileViewGenerator {
   public static void create(String title, String fileToView, String htmlFile) {
      String newline = System.getProperty("line.separator");
      File file = new File(htmlFile);
      FileWriter fw = null;
      BufferedWriter bw = null;

      try {
         fw = new FileWriter(file);
         bw = new BufferedWriter(fw);
         bw.write("<html lang=\"en\"><head><title>\n");
         bw.write(title);
         bw.write("</title>\n");
         bw.write("<meta charset=\"utf-8\">\n");
         bw.write("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
         bw.write("</head>\n<body>\n");
         bw.write("<span>\n</span>\n");
         bw.write("<script>\n");
         bw.write("var redirectURL = ");
         bw.write("\"file:///");
         bw.write(convert(fileToView));
         bw.write("\";\n");
         bw.write("var el = document.createElement('script');\n");
         bw.write("function redirect() {\n");
         bw.write("   document.location.reload();\n");
         bw.write("}\n");
         bw.write("function redirectPage() {\n   el.id = redirectURL;\n   el.onerror = function(){if (el.onerror) displayError(this.id);};\n   el.onload = function() {window.location = redirectURL;};\n   el.src = redirectURL;\n   document.body.appendChild(el);\n}\n");
         bw.write("function displayError(e) {\n   document.getElementsByTagName('span')[0].innerHTML += e+\" is unavailable currently.\";\n}\n");
         bw.write("setInterval(redirect, 5000);\n");
         bw.write("redirectPage();\n");
         bw.write(newline);
         bw.write("</script>");
         bw.write(newline);
         bw.write("</body>");
         bw.write(newline);
         bw.write("</html>");
      } catch (IOException var20) {
         Logger.log(Level.WARNING, "", "Could not create " + fileToView + " viewer");
      } finally {
         try {
            if (bw != null) {
               bw.close();
            }
         } catch (IOException var19) {
         }

         try {
            if (fw != null) {
               fw.close();
            }
         } catch (IOException var18) {
         }

      }

   }

   public static void createUsingLocalHost(String title, String fileToView, String htmlFile) {
      String newline = System.getProperty("line.separator");
      File file = new File(htmlFile);
      FileWriter fw = null;
      BufferedWriter bw = null;

      try {
         fw = new FileWriter(file);
         bw = new BufferedWriter(fw);
         bw.write("<html lang=\"en\"><head><title>\n");
         bw.write(title);
         bw.write("</title>\n");
         bw.write("<meta charset=\"utf-8\">\n");
         bw.write("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
         bw.write("</head>\n<body>\n");
         bw.write("<span>\n</span>\n");
         bw.write("<script>\n");
         bw.write("var redirectURL = ");
         bw.write("\"http://localhost:8888/log");
         bw.write("\";\n");
         bw.write("var el = document.createElement('script');\n");
         bw.write("function redirect() {\n");
         bw.write("   document.location.reload();\n");
         bw.write("}\n");
         bw.write("function redirectPage() {\n   el.id = redirectURL;\n   el.onerror = function(){if (el.onerror) displayError(this.id);};\n   el.onload = function() {window.location = redirectURL;};\n   el.src = redirectURL;\n   document.body.appendChild(el);\n}\n");
         bw.write("function displayError(e) {\n   document.getElementsByTagName('span')[0].innerHTML += e+\" is unavailable currently.\";\n}\n");
         bw.write("setInterval(redirect, 5000);\n");
         bw.write("redirectPage();\n");
         bw.write(newline);
         bw.write("</script>");
         bw.write(newline);
         bw.write("</body>");
         bw.write(newline);
         bw.write("</html>");
      } catch (IOException var20) {
         Logger.log(Level.WARNING, "", "Could not create " + fileToView + " viewer");
      } finally {
         try {
            if (bw != null) {
               bw.close();
            }
         } catch (IOException var19) {
         }

         try {
            if (fw != null) {
               fw.close();
            }
         } catch (IOException var18) {
         }

      }

   }

   private static String convert(String name) {
      StringBuffer b = new StringBuffer();

      for(int i = 0; i < name.length(); ++i) {
         char c = name.charAt(i);
         if (c == '\\') {
            b.append("\\\\");
         } else {
            b.append(c);
         }
      }

      return b.toString();
   }

   public static void main(String[] args) {
      if (args.length < 2) {
         System.err.println("No file-to-view and html file names provided");
         System.exit(-1);
      }

      create("CoMaS Test", args[0], args[1]);
   }
}
