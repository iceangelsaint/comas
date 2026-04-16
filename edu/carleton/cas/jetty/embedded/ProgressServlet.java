package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.background.timers.TimerService;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.ui.WebAlert;
import java.awt.Desktop;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.TimerTask;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletHolder;

@MultipartConfig
public class ProgressServlet extends EmbeddedProgressServlet implements ProgressIndicator {
   private static ProgressServlet singleton;
   private static final int DEFAULT_TIMEOUT_IN_MSECS = 5000;
   private Thread prepareThread = null;
   private Thread endThread = null;
   private ProgressTimer progressTimer = null;

   public ProgressServlet(Invigilator invigilator) {
      super(invigilator);
      if (singleton == null) {
         singleton = this;
      }

   }

   public static String getMapping() {
      return "progress";
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      String myMapping = "/" + getMapping();
      ServletHolder sh = sp.addServlet(this, myMapping);
      ServletRouter sr = sp.getRouter();
      sr.addRule(myMapping, InvigilatorState.initializing);
      sr.addRule(myMapping, InvigilatorState.ending);
      sr.addRedirect(InvigilatorState.initializing, myMapping);
      sh.getRegistration().setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir"), this.maxFileSize, this.maxRequestSize, this.fileSizeThreshold));
   }

   public static ProgressServlet getSingleton() {
      return singleton;
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         this.cancelTimer();
         if (this.invigilator.isDone() && this.invigilator.isInInvigilatorState(InvigilatorState.ending)) {
            this.invigilator.setInvigilatorState(InvigilatorState.ended);
            response.sendRedirect(SystemWebResources.getLocalResource("endedLandingPage", "/ended"));
         } else if (this.invigilator.isInitialized() && this.invigilator.isInInvigilatorState(InvigilatorState.initializing)) {
            this.invigilator.setInvigilatorState(InvigilatorState.running);
            response.sendRedirect(SystemWebResources.getLocalResource("motdLandingPage", "/motd"));
         } else {
            this.setupTimer();
            if (this.invigilator.isInInvigilatorState(InvigilatorState.initializing) && this.prepareThread == null) {
               this.prepareToRunExam();
            }

            if (this.invigilator.isInInvigilatorState(InvigilatorState.ending) && this.endThread == null) {
               this.prepareToEndExam();
            }

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
            this.cancelTimer();
            int completion = this.getProgress();
            String currentMessage = this.getProgressMessage();
            if (completion == 100 && this.invigilator.isInInvigilatorState(InvigilatorState.ending)) {
               this.invigilator.setInvigilatorState(InvigilatorState.ended);
               pw.print("100:");
               pw.print(SystemWebResources.getLocalResource("endedLandingPage", "/ended"));
            } else if (completion == 100 && this.invigilator.isInInvigilatorState(InvigilatorState.initializing)) {
               this.invigilator.setInvigilatorState(InvigilatorState.running);
               pw.print("100:");
               pw.print(SystemWebResources.getLocalResource("motdLandingPage", "/motd"));
            } else {
               pw.print(completion);
               pw.print(":");
               pw.print(currentMessage);
            }

            this.setupTimer();
         }
      } else {
         pw.println("Unknown token on page.\nPlease refresh page");
         response.setStatus(404);
      }

   }

   private void getForm(HttpServletRequest request, HttpServletResponse response, String extraHTML) throws IOException {
      response.addHeader("Access-Control-Allow-Origin", "*");
      response.setContentType("text/html");
      PrintWriter wr = response.getWriter();
      String currentProgressMessage = this.getProgressMessage();
      int currentProgress = this.getProgress();
      wr.print("<!DOCTYPE html>\n<html lang=\"en\">\n <head>\n  <title>");
      wr.print(this.invigilator.getTitle());
      wr.print("CoMaS Progress</title>\n");
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
      wr.print("<div ><h2 id=\"message\">");
      wr.print(currentProgressMessage == null ? "" : currentProgressMessage);
      wr.println("</h2></div>");
      wr.print("<div class=\"w3-border w3-light-grey\" style=\"width:100%;text-align:center\">");
      wr.print("<div id=\"progress-bar\" class=\"w3-container w3-grey w3-round\" style=\"height:24px;width:1%\">");
      wr.print("  </div></div></div>\n");
      if (extraHTML != null && extraHTML.length() > 0) {
         wr.println("<script>document.body.onload = function() {");
         wr.println(extraHTML);
         wr.println("};</script>");
      }

      wr.println("<script>");
      wr.println("   var continueFlowTimer;");
      wr.println("   var warningHasBeenDisplayed = false;");
      wr.println("   const progressBar = document.getElementById('progress-bar');");
      wr.println("   const messageField = document.getElementById('message');");
      wr.println("   function continueWorkflow(action) {");
      wr.println("      var xhttp = new XMLHttpRequest();");
      wr.println("      xhttp.onload = function() {");
      wr.println("         if (warningHasBeenDisplayed)");
      wr.println("            return;");
      wr.println("         if (!(xhttp.status >= 200 && xhttp.status < 300)) {");
      wr.println("            warningHasBeenDisplayed = true;");
      wr.println("            progressBar.style.width = '100%';");
      wr.println("            progressBar.textContent = '100%';");
      wr.println("            messageField.innerText = \"Your session has now ended\";");
      wr.println("            alertWithTimeout(\"Your session has now ended\", 5000);");
      wr.println("            clearTimeout(continueFlowTimer);");
      wr.println("         }");
      wr.println("      };");
      wr.println("      xhttp.onerror = function() {");
      wr.println("         if (warningHasBeenDisplayed)");
      wr.println("            return;");
      wr.println("         warningHasBeenDisplayed = true;");
      wr.println("         progressBar.style.width = '100%';");
      wr.println("         progressBar.textContent = '100%';");
      wr.println("         messageField.innerText = \"Your session has now ended\";");
      wr.println("         alertWithTimeout(\"Your session has now ended\", 5000);");
      wr.println("         clearTimeout(continueFlowTimer);");
      wr.println("      };");
      wr.println("      xhttp.onabort = xhttp.onerror;\n");
      wr.println("      xhttp.ontimeout = xhttp.onerror;\n");
      wr.println("      xhttp.onreadystatechange = function() {");
      wr.println("         if (this.readyState === 4 && this.status === 200) {");
      wr.println("            var tokens = xhttp.responseText.split(\":\");");
      wr.println("            var completion = parseInt(tokens[0]);");
      wr.println("            if (completion === 100) {");
      wr.println("               try {");
      wr.println("                  window.location.href = tokens[1];");
      wr.println("               } catch (error) {");
      wr.println("                  progressBar.style.width = '100%';");
      wr.println("                  progressBar.textContent = '100%';");
      wr.println("                  messageField.innerText = \"Your session has now ended\";");
      wr.println("               }");
      wr.println("            } else if (typeof completion === 'number' && !isNaN(completion)) {");
      wr.println("               progressBar.style.width = tokens[0] + '%';");
      wr.println("               progressBar.textContent = tokens[0] + '%';");
      wr.println("               messageField.innerText = tokens[1];");
      wr.println("            }");
      wr.println("         } else if (this.readyState === 4 && this.status === 404) {");
      wr.println("            alertWithTimeout(\"Your session has now ended\", 5000);");
      wr.println("         }");
      wr.println("      };");
      wr.println("      const formData = new FormData();");
      wr.println("      formData.append(\"token\", document.getElementById(\"token\").value);");
      wr.println("      xhttp.open(\"POST\", action);");
      wr.println("      xhttp.send(formData);");
      wr.println("   }");
      wr.println("   continueFlowTimer = setInterval('continueWorkflow(\"/progress\")', 1000);");
      wr.print("   progressBar.style.width = Math.round(");
      wr.print(currentProgress);
      wr.print(") + '%';\n        progressBar.value = Math.round(");
      wr.print(currentProgress);
      wr.println(");");
      wr.println("</script>");
      wr.println(this.invigilator.getServletProcessor().footerForServlet(true, true));
      wr.println("</body></html>");
   }

   private void cancelTimer() {
      if (this.progressTimer != null && !this.progressTimer.cancel()) {
         this.progressTimer = null;
      }

   }

   private void setupTimer() {
      if (this.invigilator.isInInvigilatorState(InvigilatorState.initializing)) {
         this.progressTimer = new ProgressTimer();

         try {
            TimerService.schedule(this.progressTimer, 5000L);
         } catch (IllegalStateException var2) {
         }
      }

   }

   private void prepareToEndExam() {
      this.invigilator.updateProgressServlet(0, "Session ending");
      this.endThread = new Thread(new SessionEndTask(this.invigilator, this));
      this.endThread.start();
   }

   private void prepareToRunExam() {
      this.invigilator.updateProgressServlet(0, "Session initializing");
      this.prepareThread = new Thread(new SessionStartTask(this.invigilator, this));
      this.prepareThread.start();
   }

   private class ProgressTimer extends TimerTask {
      public void run() {
         if (ProgressServlet.this.invigilator.isInInvigilatorState(InvigilatorState.initializing)) {
            try {
               Desktop var10000 = Desktop.getDesktop();
               String var10003 = ProgressServlet.this.invigilator.getServletProcessor().getService();
               var10000.browse(new URI(var10003 + ProgressServlet.getMapping()));
            } catch (URISyntaxException | UnsupportedOperationException | IOException var2) {
               WebAlert.warning("The progress web page could not be created");
            }
         }

      }
   }
}
