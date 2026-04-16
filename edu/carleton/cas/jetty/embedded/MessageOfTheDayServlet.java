package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.ui.WebAppDialogCoordinator;
import edu.carleton.cas.utility.ClientConfiguration;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletHolder;

@MultipartConfig
public class MessageOfTheDayServlet extends EmbeddedServlet {
   private static MessageOfTheDayServlet singleton;
   private String message;
   private boolean hasRunInitializationCode = false;

   public MessageOfTheDayServlet(Invigilator invigilator) {
      super(invigilator);
      this.message = invigilator.resolveVariablesInMessage(ClientShared.STARTUP_MESSAGE);
      if (singleton == null) {
         singleton = this;
      }

   }

   public static String getMapping() {
      return "motd";
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      String myMapping = "/" + getMapping();
      ServletHolder sh = sp.addServlet(this, myMapping);
      ServletRouter sr = sp.getRouter();
      sr.addRule(myMapping, InvigilatorState.running);
      sh.getRegistration().setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir"), this.maxFileSize, this.maxRequestSize, this.fileSizeThreshold));
   }

   public static MessageOfTheDayServlet getSingleton() {
      return singleton;
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         if (!this.isRequired()) {
            this.readyToStartSession();
            response.sendRedirect(SystemWebResources.getHomePage());
            return;
         }

         this.message = this.invigilator.resolveVariablesInMessage(ClientShared.STARTUP_MESSAGE);
         ClientConfiguration cc = this.invigilator.getClientConfiguration();
         cc.setReadMOTD(System.currentTimeMillis());
         cc.save();
         if (this.invigilator.isDone() && this.invigilator.isInInvigilatorState(InvigilatorState.ending)) {
            this.invigilator.setInvigilatorState(InvigilatorState.ended);
            response.sendRedirect(SystemWebResources.getLocalResource("endedLandingPage", "/ended"));
         } else {
            this.getForm(request, response, "");
         }

         this.invigilator.setLastServlet(getMapping());
      }

   }

   protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      PrintWriter pw = response.getWriter();
      String tokenFromForm = request.getParameter("token");
      if (tokenFromForm != null && tokenFromForm.equals(this.token)) {
         if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
            String agreedToContinue = request.getParameter("continue");
            if (Utils.isTrueOrYes(agreedToContinue)) {
               this.readyToStartSession();
               pw.print(SystemWebResources.getHomePage());
            } else {
               pw.print("/" + getMapping());
            }
         }
      } else {
         pw.println("Unknown token on page.\nPlease refresh page");
         response.setStatus(404);
      }

   }

   public boolean readyToStartSession() {
      if (this.hasRunInitializationCode) {
         return true;
      } else {
         this.hasRunInitializationCode = true;
         (new Thread(new Runnable() {
            public void run() {
               MessageOfTheDayServlet.this.invigilator.setInvigilatorState(InvigilatorState.running);
               WebAppDialogCoordinator.sync();
               MessageOfTheDayServlet.this.invigilator.initializeDeployedWebPagesAndApplications();
               MessageOfTheDayServlet.this.invigilator.readyForService();
            }
         })).start();
         return false;
      }
   }

   private boolean isRequired() {
      long timeMOTDLastRead = this.invigilator.getClientConfiguration().getReadMOTD();
      long timeSinceMOTDLastRead = System.currentTimeMillis() - timeMOTDLastRead;
      long timeSinceMOTDLastReadThreshold = (long)Utils.getIntegerOrDefault(this.invigilator.getProperties(), "session.readMOTDInterval", 86400) * 1000L;
      return this.message.length() > 0 && timeSinceMOTDLastRead > timeSinceMOTDLastReadThreshold;
   }

   private void getForm(HttpServletRequest request, HttpServletResponse response, String extraHTML) throws IOException {
      response.addHeader("Access-Control-Allow-Origin", "*");
      response.setContentType("text/html");
      PrintWriter wr = response.getWriter();
      wr.print("<!DOCTYPE html>\n<html lang=\"en\">\n <head>\n  <title>");
      wr.print(this.invigilator.getTitle());
      wr.print("CoMaS MOTD</title>\n");
      wr.println(SystemWebResources.getStylesheet());
      wr.println(SystemWebResources.getIcon());
      wr.println("</head>\n<body>\n");
      wr.println(this.invigilator.getServletProcessor().checkForServletCode((String)null));
      wr.println("<div class=\"w3-container w3-center\" id=\"form-wrapper\" style=\"max-width:500px;margin:auto;\">");
      wr.print("<h1><img alt=\"CoMaS logo\" src=\"");
      wr.print(SystemWebResources.getAppImage());
      wr.print("\"></h1>\n");
      wr.print("  <input type=\"hidden\" id=\"token\" name=\"token\" value=\"");
      wr.print(this.token);
      wr.print("\" />\n");
      wr.print("<div id=\"message\" class=\"w3-panel w3-card\" style=\"text-align:justify\">");
      wr.print(this.message == null ? "" : this.message);
      wr.println("</div>");
      wr.print("  </div>\n");
      ServletProcessor var10001 = this.invigilator.getServletProcessor();
      String var10004 = getMapping();
      wr.print(var10001.footerForServlet(true, true, "  <input accesskey=\"c\" class=\"w3-button w3-round-large w3-blue w3-border\" type=\"button\" onclick=\"continueWorkflow('/" + var10004 + "')\" value=\"Continue\" />\n" + this.invigilator.getServletProcessor().getMailButtonSeparatorBefore()));
      if (extraHTML != null && extraHTML.length() > 0) {
         wr.println("<script>document.body.onload = function() {");
         wr.println(extraHTML);
         wr.println("};</script>");
      }

      wr.println("<script>");
      wr.println("   function continueWorkflow(action) {");
      wr.println("      var xhttp = new XMLHttpRequest();");
      wr.println("      xhttp.onreadystatechange = function() {");
      wr.println("         if (this.readyState == 4 && this.status == 200) {");
      wr.println("            window.location.href=xhttp.responseText;");
      wr.println("         } else if (this.readyState == 4 && this.status >= 400) {");
      wr.println("            alert(xhttp.responseText);");
      wr.println("         }");
      wr.println("      };");
      wr.println("      const formData = new FormData();");
      wr.println("      formData.append(\"token\", document.getElementById(\"token\").value);");
      wr.println("      formData.append(\"continue\", \"true\");");
      wr.println("      xhttp.open(\"POST\", action);");
      wr.println("      xhttp.send(formData);");
      wr.println("   }");
      wr.println(this.invigilator.getServletProcessor().pingForServlet());
      wr.println("</script>");
      wr.println("</body></html>");
   }
}
