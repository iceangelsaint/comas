package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.background.timers.TimerService;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.utility.ClientConfiguration;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimerTask;
import java.util.logging.Level;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletHolder;

@MultipartConfig
public class AgreementServlet extends EmbeddedProgressServlet implements ProgressIndicator {
   private static AgreementServlet singleton;
   private static final String agreementMessage = "<div class=\"w3-panel w3-card\" style=\"text-align:justify\"><p>CoMaS monitors the local file system for unauthorized access to materials, takes screen shots periodically and monitors all network and process access. The screen shots are uploaded to the CoMaS server. Web cam access and identity verification may also be required in order for the activity to proceed. System checks are also performed in order to confirm that your system meets requirements for e-proctoring. The end user license agreement can be found at <a href=\"https://cogerent.com/eula.html\" target=\"_blank\" rel=\"noopener noreferrer\">CoMaS EULA</a>.</p><p>If you agree, press <span class=\"w3-blue\">Yes</span> and the session will continue.</p><p>If you do not agree, press <span class=\"w3-blue\">No</span> and this session will end.</p><p>This session will be terminated in %d seconds if you do nothing.</p></div>";
   int number_of_seconds_to_wait = 120;
   String messageToDisplay;
   AgreementTimer timeout;

   public AgreementServlet(Invigilator invigilator) {
      super(invigilator);
      if (singleton == null) {
         singleton = this;
      }

   }

   public static String getMapping() {
      return "agreement";
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      String myMapping = "/" + getMapping();
      ServletHolder sh = sp.addServlet(this, myMapping);
      ServletRouter sr = sp.getRouter();
      sr.addRule(myMapping, InvigilatorState.authorizing);
      sr.addRedirect(InvigilatorState.authorizing, myMapping);
      sh.getRegistration().setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir"), this.maxFileSize, this.maxRequestSize, this.fileSizeThreshold));
   }

   public static AgreementServlet getSingleton() {
      return singleton;
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         if (!this.isRequired()) {
            this.invigilator.setInvigilatorState(InvigilatorState.verifying);
            response.sendRedirect(SystemWebResources.getLocalResource("verifyLandingPage", "/verify"));
            return;
         }

         if (this.invigilator.isDone() && this.invigilator.isInInvigilatorState(InvigilatorState.ending)) {
            this.invigilator.setInvigilatorState(InvigilatorState.ended);
            response.sendRedirect(SystemWebResources.getLocalResource("endedLandingPage", "/ended"));
         } else if (this.invigilator.isInitialized() && this.invigilator.isInInvigilatorState(InvigilatorState.initializing)) {
            this.invigilator.setInvigilatorState(InvigilatorState.running);
            response.sendRedirect(SystemWebResources.getHomePage());
         } else {
            if (this.timeout == null) {
               this.timeout = new AgreementTimer();
               TimerService.schedule(this.timeout, System.currentTimeMillis() + (long)(this.number_of_seconds_to_wait * 1000));
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
            String agreed = request.getParameter("agreed");
            long percentage = this.timeout.progress(this.number_of_seconds_to_wait);
            if (percentage > 100L) {
               percentage = 100L;
            }

            if (agreed != null && agreed.length() < 32 && agreed.equalsIgnoreCase("yes")) {
               this.invigilator.logArchiver.put(Level.INFO, String.format("%s authorized monitoring of %s/%s", this.invigilator.getName(), this.invigilator.getCourse(), this.invigilator.getActivity()));
               this.timeout.cancel();
               this.invigilator.setInvigilatorState(InvigilatorState.initializing);
               ClientConfiguration cc = this.invigilator.getClientConfiguration();
               cc.setAgreedToMonitor(System.currentTimeMillis());
               cc.save();
               pw.print("100:");
               pw.print(SystemWebResources.getLocalResource("verifyLandingPage", "/verify"));
               return;
            }

            if (agreed != null && agreed.length() < 32 && agreed.equalsIgnoreCase("no") || percentage == 100L) {
               this.invigilator.logArchiver.put(Level.INFO, String.format("%s refused to authorize monitoring of %s/%s. Session was terminated.", this.invigilator.getName(), this.invigilator.getCourse(), this.invigilator.getActivity()), new Object[]{"session", "terminated"});
               this.timeout.cancel();
               ClientConfiguration cc = this.invigilator.getClientConfiguration();
               cc.setAgreedToMonitor(0L);
               cc.save();
               this.invigilator.setState("Terminated");
               this.invigilator.updateProgressServlet(0, "Session ending. You refused to authorize monitoring");
               this.invigilator.setInvigilatorState(InvigilatorState.ending);
               pw.print("100:");
               pw.print(SystemWebResources.getLocalResource("endLandingPage", "/end"));
               return;
            }

            pw.print(percentage);
            pw.print(":");
            pw.print(percentage + "%");
         }
      } else {
         pw.println("Unknown token on page.\nPlease refresh page");
         response.setStatus(404);
      }

   }

   private boolean isRequired() {
      boolean required = true;
      if (this.invigilator.isBeingHarvested()) {
         required = false;
      } else {
         required = Utils.isTrueOrYes(this.invigilator.getProperty("monitoring.screenshots.required"));
         required = required || Utils.isTrueOrYes(this.invigilator.getProperty("monitoring.network.required"));
         required = required || Utils.isTrueOrYes(this.invigilator.getProperty("monitoring.file.required"));
         required = required || Utils.isTrueOrYes(this.invigilator.getProperty("monitoring.processes.required"));
         required = required || Utils.isTrueOrYes(this.invigilator.getProperty("monitoring.windows.required"));
         required = required || Utils.isTrueOrYes(this.invigilator.getProperty("monitoring.bluetooth.required"));
         required = required || Utils.isTrueOrYes(this.invigilator.getProperty("monitoring.audio.required"));
         required = required || Utils.isTrueOrYes(this.invigilator.getProperty("monitoring.video.required"));
         required = required || Utils.isTrueOrYes(this.invigilator.getProperty("webcam.required"));
      }

      if (!required) {
         return false;
      } else {
         ClientConfiguration cc = this.invigilator.getClientConfiguration();
         Date agreedToDate = new Date(cc.getAgreedToMonitor());
         String resetDateFormatString = this.invigilator.getProperty("session.agreedToMonitorFormat", "yyyy-MM-dd").trim();
         String resetAgreedToMonitorDate = this.invigilator.getProperty("session.agreedToMonitor");
         if (resetAgreedToMonitorDate != null && resetAgreedToMonitorDate.trim().length() > 0) {
            try {
               SimpleDateFormat resetDateFormat = new SimpleDateFormat(resetDateFormatString);
               Date resetDate = resetDateFormat.parse(resetAgreedToMonitorDate.trim(), new ParsePosition(0));
               required = resetDate.after(agreedToDate);
               if (!required) {
                  this.invigilator.logArchiver.put(Level.INFO, String.format("%s authorized monitoring of %s/%s on %s", this.invigilator.getName(), this.invigilator.getCourse(), this.invigilator.getActivity(), agreedToDate));
               }

               return required;
            } catch (NullPointerException | IllegalArgumentException var8) {
            }
         }

         required = cc.getAgreedToMonitor() == 0L;
         if (!required) {
            this.invigilator.logArchiver.put(Level.INFO, String.format("%s authorized monitoring of %s/%s on %s", this.invigilator.getName(), this.invigilator.getCourse(), this.invigilator.getActivity(), agreedToDate));
         }

         return required;
      }
   }

   private void getForm(HttpServletRequest request, HttpServletResponse response, String extraHTML) throws IOException {
      response.addHeader("Access-Control-Allow-Origin", "*");
      response.setContentType("text/html");
      PrintWriter wr = response.getWriter();
      wr.print("<!DOCTYPE html>\n<html lang=\"en\">\n <head>\n  <title>");
      wr.print(this.invigilator.getTitle());
      wr.print("CoMaS Agreement</title>\n");
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
      wr.print("<div id=\"message\">");
      String currentMessage = this.getProgressMessage();
      wr.print(currentMessage == null ? "" : currentMessage);
      wr.println("</div>");
      wr.print("<div id=\"progress\" class=\"w3-border w3-light-grey w3-center\" style=\"width:100%;text-align:center\">");
      wr.print("<div id=\"progress-bar\" class=\"w3-container w3-grey w3-round\" style=\"height:24px;width:1%\">");
      wr.print("  </div></div>\n");
      wr.print("<p><button accesskey=\"y\" id =\"yes\" class=\"w3-button w3-round-large w3-blue\" onclick='setAgreed(\"yes\");'>Yes</button>&nbsp;&nbsp;&nbsp;&nbsp;");
      wr.print("<button accesskey=\"n\" id=\"no\" class=\"w3-button w3-round-large w3-blue\" onclick='setAgreed(\"no\");'>No</button></p>");
      wr.print("  </div>\n");
      if (extraHTML != null && extraHTML.length() > 0) {
         wr.println("<script>document.body.onload = function() {");
         wr.println(extraHTML);
         wr.println("};</script>");
      }

      wr.println("<script>");
      wr.println("   const progressBar = document.getElementById('progress-bar');");
      wr.println("   var agreed = \"not set\";");
      wr.println("   function continueWorkflow(action) {");
      wr.println("      var xhttp = new XMLHttpRequest();");
      wr.println("      xhttp.onreadystatechange = function() {");
      wr.println("         if (this.readyState == 4 && this.status == 200) {");
      wr.println("            var tokens = xhttp.responseText.split(\":\");");
      wr.println("            var completion = parseInt(tokens[0]);");
      wr.println("            if (completion === 100) {");
      wr.println("               window.location.href = tokens[1];");
      wr.println("            } else {");
      wr.println("               progressBar.style.width = tokens[0] + '%';");
      wr.println("               progressBar.textContent = tokens[0] + '%';");
      wr.println("               if (completion > 75) {");
      wr.println("                  progressBar.className = \"w3-container w3-orange w3-round\";");
      wr.println("               }");
      wr.println("               if (completion > 90) {");
      wr.println("                  progressBar.className = \"w3-container w3-red w3-round\";");
      wr.println("               }");
      wr.println("            }");
      wr.println("         } else if (this.readyState == 4 && this.status >= 400) {");
      wr.println("            alert(xhttp.responseText);");
      wr.println("         }");
      wr.println("      };");
      wr.println("      const formData = new FormData();");
      wr.println("      formData.append(\"token\", document.getElementById(\"token\").value);");
      wr.println("      formData.append(\"agreed\", agreed);");
      wr.println("      xhttp.open(\"POST\", action);");
      wr.println("      xhttp.send(formData);");
      wr.println("   }");
      wr.println("   setInterval('continueWorkflow(\"/agreement\")', 1000);");
      wr.print("   progressBar.style.width = Math.round(");
      int currentProgress = this.getProgress();
      wr.print(currentProgress);
      wr.print(") + '%';\n        progressBar.value = Math.round(");
      wr.print(currentProgress);
      wr.println(");");
      wr.println("   function setAgreed(yesOrNo) {");
      wr.println("      agreed = yesOrNo;");
      wr.println("      document.getElementById('yes').disabled = true;");
      wr.println("      document.getElementById('no').disabled = true;");
      wr.println("   }");
      wr.println(this.invigilator.getServletProcessor().pingForServlet());
      wr.println("</script>");
      wr.println(this.invigilator.getServletProcessor().footerForServlet(false, true));
      wr.println("</body></html>");
   }

   public void configure() {
      String timeToAgree = this.invigilator.getProperty("time_to_agree_to_monitoring");
      if (timeToAgree == null) {
         timeToAgree = this.invigilator.getProperty("application.agreement.timeout");
      }

      if (timeToAgree != null) {
         try {
            this.number_of_seconds_to_wait = Integer.parseInt(timeToAgree.trim());
         } catch (NumberFormatException var3) {
            this.number_of_seconds_to_wait = 120;
         }
      }

      this.messageToDisplay = this.invigilator.getProperty("application.agreement.message");
      if (this.messageToDisplay == null) {
         this.messageToDisplay = "<div class=\"w3-panel w3-card\" style=\"text-align:justify\"><p>CoMaS monitors the local file system for unauthorized access to materials, takes screen shots periodically and monitors all network and process access. The screen shots are uploaded to the CoMaS server. Web cam access and identity verification may also be required in order for the activity to proceed. System checks are also performed in order to confirm that your system meets requirements for e-proctoring. The end user license agreement can be found at <a href=\"https://cogerent.com/eula.html\" target=\"_blank\" rel=\"noopener noreferrer\">CoMaS EULA</a>.</p><p>If you agree, press <span class=\"w3-blue\">Yes</span> and the session will continue.</p><p>If you do not agree, press <span class=\"w3-blue\">No</span> and this session will end.</p><p>This session will be terminated in %d seconds if you do nothing.</p></div>";
      }

      this.setProgressMessage(String.format(this.messageToDisplay, this.number_of_seconds_to_wait));
   }

   private class AgreementTimer extends TimerTask {
      private final long start = System.currentTimeMillis();

      AgreementTimer() {
      }

      long progress(int secs) {
         return Math.round((double)(System.currentTimeMillis() - this.start) * (double)100.0F / (double)(secs * 1000));
      }

      public void run() {
         AgreementServlet.this.invigilator.logArchiver.put(Level.INFO, String.format("%s DID NOT authorize monitoring of %s/%s after %d seconds. Session was terminated.", AgreementServlet.this.invigilator.getName(), AgreementServlet.this.invigilator.getCourse(), AgreementServlet.this.invigilator.getActivity(), AgreementServlet.this.number_of_seconds_to_wait), new Object[]{"session", "terminated"});
         AgreementServlet.this.invigilator.setState("Terminated");
         AgreementServlet.this.invigilator.setInvigilatorState(InvigilatorState.ending);
         AgreementServlet.this.invigilator.updateProgressServlet(0, "Session ending. You refused to authorize monitoring");
         (new Thread(new SessionEndTask(AgreementServlet.this.invigilator, AgreementServlet.singleton))).start();
      }
   }
}
