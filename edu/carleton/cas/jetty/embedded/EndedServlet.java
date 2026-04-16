package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.resources.SystemWebResources;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EndedServlet extends EmbeddedServlet {
   private static EndedServlet singleton;
   private String message;

   public EndedServlet(Invigilator invigilator) {
      super(invigilator);
      this.setMessage((String)null);
      if (singleton == null) {
         singleton = this;
      }

   }

   public static String getMapping() {
      return "ended";
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      String myMapping = "/" + getMapping();
      sp.addServlet(this, myMapping);
      ServletRouter sr = sp.getRouter();
      sr.addRule(myMapping, InvigilatorState.ended);
      sr.addRedirect(InvigilatorState.ended, myMapping);
   }

   public static EndedServlet getSingleton() {
      return singleton;
   }

   public void setMessage(String message) {
      if (message != null) {
         this.message = message;
      } else {
         this.message = this.invigilator.resolveVariablesInMessage(ClientShared.END_MESSAGE);
         if (this.message == null || this.message.length() == 0) {
            this.message = "Your session has ended. Please close all browser tabs.";
         }
      }

   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         this.getForm(request, response, "");
      }

   }

   private void getForm(HttpServletRequest request, HttpServletResponse response, String extraHTML) throws IOException {
      response.addHeader("Access-Control-Allow-Origin", "*");
      response.setContentType("text/html");
      PrintWriter wr = response.getWriter();
      wr.print("<!DOCTYPE html>\n<html lang=\"en\">\n <head>\n  <title>");
      wr.print(this.invigilator.getTitle());
      wr.print("CoMaS Ended</title>\n");
      wr.println(SystemWebResources.getStylesheet());
      wr.println(SystemWebResources.getIcon());
      wr.println("</head>\n<body>\n<div class=\"w3-container w3-center\">");
      wr.print("<h1><img alt=\"CoMaS logo\" src=\"");
      wr.print(ClientShared.BASE_CMS);
      wr.print(SystemWebResources.getAppImage().substring(1));
      wr.print("\"></h1>\n");
      wr.print("  <input type=\"hidden\" id=\"token\" name=\"token\" value=\"");
      wr.print(this.token);
      wr.print("\" />\n");
      wr.print("<div><h2>");
      wr.print(this.message);
      wr.print("</h2><h2>");
      wr.print("Your session lasted ");
      String sessionTime = Utils.convertMsecsToHoursMinutesSeconds(this.invigilator.elapsedTimeInMillis(), false, true);
      wr.print(sessionTime);
      wr.print("</h2></div></div>\n");
      if (extraHTML != null && extraHTML.length() > 0) {
         wr.println("<script>document.body.onload = function() {");
         wr.println(extraHTML);
         wr.println("};</script>");
      }

      wr.println(this.invigilator.getServletProcessor().footerForServlet(false, true));
      wr.print("</body></html> ");
   }
}
