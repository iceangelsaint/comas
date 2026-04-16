package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.resources.SystemWebResources;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class QuitServlet extends EmbeddedServlet {
   private static QuitServlet singleton;
   private String message;

   public QuitServlet(Invigilator invigilator) {
      this(invigilator, (String)null);
   }

   public QuitServlet(Invigilator invigilator, String message) {
      super(invigilator);
      this.setMessage(message);
      if (singleton == null) {
         singleton = this;
      }

   }

   public void setMessage(String message) {
      if (message != null && message.length() > 0) {
         this.message = message;
      } else {
         this.message = "Session is now terminating. Please close all applications and save files";
      }

   }

   public static String getMapping() {
      return "quit";
   }

   public static QuitServlet getSingleton() {
      return singleton;
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      sp.updateLastAccessTime();
      String sessionTime = Utils.convertMsecsToHoursMinutesSeconds(this.invigilator.elapsedTimeInMillis(), false, true);
      response.addHeader("Access-Control-Allow-Origin", "*");
      response.setContentType("text/html");
      PrintWriter pw = response.getWriter();
      pw.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\"><title>");
      pw.print(this.invigilator.getTitle());
      pw.print("CoMaS Quit: ");
      pw.print(this.invigilator.getName());
      pw.print(" (");
      pw.print(this.invigilator.getID());
      pw.print(")</title>");
      pw.println(SystemWebResources.getStylesheet());
      pw.println(SystemWebResources.getIcon());
      pw.print("</head><body>");
      String codeOnExit = "var msg = document.getElementById(\"status-message\");\nif (msg !== null) msg.innerHTML=\"Session has ended. Session lasted " + sessionTime + "\";";
      pw.print(sp.checkForServletCode(10000, false, "", codeOnExit));
      pw.print("<div class=\"w3-panel\" style=\"margin:auto;text-align:center\"><br/>");
      pw.print("<img alt=\"CoMaS logo\" src=\"");
      pw.print(SystemWebResources.getAppImage());
      pw.print("\"><br/>\n");
      if (!this.invigilator.isConnected()) {
         pw.print("<h2>You are not currently connected. Please check your network connection.</h2>");
      }

      String[] processes = this.invigilator.checkProcessesAccessingFolderOfInterest();
      if (processes != null && processes.length > 0) {
         pw.print("<h2>Please close all applications accessing exam files as some files may still be open. Applications:</h2>");

         for(String process : processes) {
            pw.print("<h3>");
            pw.print(process);
            pw.print("</h3>");
         }
      }

      pw.print("<h2 id=\"status-message\">");
      pw.print(this.message);
      pw.print("</h2>");
      pw.print(sp.getServletButton(this.invigilator.getLastServlet(), "Cancel", 'a'));
      pw.print("&nbsp;&nbsp;&nbsp;&nbsp;");
      pw.print(sp.getServletButton("end", "Confirm", 'o'));
      pw.print(sp.footerForServlet());
      pw.println("</div>");
      pw.println("<script>");
      pw.println(this.invigilator.getServletProcessor().pingForServlet());
      pw.println("</script>");
      pw.println("</body></html>");
      response.setStatus(200);
   }

   public static String getQuitButton() {
      return singleton.invigilator.getServletProcessor().getServletButton("quit", "Quit");
   }
}
