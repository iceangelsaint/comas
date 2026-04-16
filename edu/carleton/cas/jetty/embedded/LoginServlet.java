package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.utility.ClientConfiguration;
import edu.carleton.cas.utility.Named;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.regex.PatternSyntaxException;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletHolder;

@MultipartConfig
public class LoginServlet extends BasicLoginServlet {
   public LoginServlet(Invigilator invigilator) {
      super(invigilator);
   }

   public static String getMapping() {
      return "login";
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      String myMapping = "/" + getMapping();
      ServletHolder sh = sp.addServlet(this, myMapping);
      ServletRouter sr = sp.getRouter();
      sr.addRule(myMapping, InvigilatorState.loggingIn);
      sr.addRedirect(InvigilatorState.loggingIn, myMapping);
      sh.getRegistration().setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir"), this.maxFileSize, this.maxRequestSize, this.fileSizeThreshold));
   }

   protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTimeRegardlessOfState();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         PrintWriter pw = response.getWriter();
         String tokenFromForm = request.getParameter("token");
         if (tokenFromForm != null && tokenFromForm.equals(this.token)) {
            String first = request.getParameter("first");
            if (first == null || first.length() > 32) {
               pw.println("First name is invalid");
               response.setStatus(404);
               return;
            }

            try {
               if (!first.matches(SystemWebResources.getNamePattern())) {
                  pw.println("First name is invalid");
                  response.setStatus(404);
                  return;
               }
            } catch (PatternSyntaxException var17) {
               pw.println("A pattern matching error occurred. Please contact support");
               response.setStatus(404);
               return;
            }

            String last = request.getParameter("last");
            if (last == null || last.length() > 32) {
               pw.println("Last name is invalid");
               response.setStatus(404);
               return;
            }

            try {
               if (!last.matches(SystemWebResources.getNamePattern())) {
                  pw.println("Last name is invalid");
                  response.setStatus(404);
                  return;
               }
            } catch (PatternSyntaxException var16) {
               pw.println("A pattern matching error occurred. Please contact support");
               response.setStatus(404);
               return;
            }

            String id = request.getParameter("id");
            if (id == null || id.length() > 32) {
               pw.println("ID is invalid");
               response.setStatus(404);
               return;
            }

            try {
               if (!id.matches(SystemWebResources.getIDPattern())) {
                  pw.println("ID is invalid");
                  response.setStatus(404);
                  return;
               }
            } catch (PatternSyntaxException var15) {
               pw.println("A pattern matching error occurred. Please contact support");
               response.setStatus(404);
               return;
            }

            String course = request.getParameter("course");
            if (course == null || course.length() > 32) {
               pw.println("Course name is invalid");
               response.setStatus(404);
               return;
            }

            try {
               if (!course.matches(SystemWebResources.getEntityPattern())) {
                  pw.println("Course name is invalid");
                  response.setStatus(404);
                  return;
               }
            } catch (PatternSyntaxException var20) {
               pw.println("A pattern matching error occurred. Please contact support");
               response.setStatus(404);
               return;
            }

            this.invigilator.setName(first, last);
            this.invigilator.setID(id);
            this.setupCourseLoginStateAndURLs(course);

            try {
               JsonObject meAsJson = this.authenticateAndProcessResponseForSpecialConditions(course, pw, response);
               if (meAsJson == null) {
                  return;
               }

               String email = null;

               for(Map.Entry entry : meAsJson.entrySet()) {
                  if (!((String)entry.getKey()).contains("-")) {
                     String key = (String)entry.getKey();
                     String value = ((JsonValue)entry.getValue()).toString();
                     if (key.equals("EMAIL")) {
                        email = Named.unquoted(value);
                        this.invigilator.setEmail(email);
                     }

                     this.invigilator.setProperty("student.directory." + key, Named.unquoted(value));
                  }
               }

               this.postProcessToken(meAsJson, course);
               boolean publicSession = Utils.getBooleanOrDefault(this.invigilator.getProperties(), "session.public", false) || Utils.getBooleanOrDefault(this.invigilator.getProperties(), "session." + this.invigilator.getID() + ".public", false);
               if (!publicSession) {
                  ClientConfiguration cc = this.invigilator.getClientConfiguration();
                  cc.setEmail(email);
                  cc.setFirst(first);
                  cc.setLast(last);
                  cc.setID(id);
                  cc.setLastSession(System.currentTimeMillis());
                  cc.setCourse(course);
                  cc.setIPv4Address(this.invigilator.getHardwareAndSoftwareMonitor().getIPv4Address());
                  if (ClientShared.SERVER_CHOSEN != null) {
                     cc.setRecentHost(ClientShared.SERVER_CHOSEN);
                  }

                  cc.save(String.format("CoMaS server location information for %s %s (%s). DO NOT EDIT", first, last, id));
               }

               this.postProcessLoginForm(response);
            } catch (IOException var18) {
               response.setStatus(404);
               pw.print("Authentication was not possible, please try again");
            } catch (IllegalStateException | JsonException var19) {
               response.setStatus(404);
               pw.print("An unexpected response was received from " + ClientShared.DIRECTORY_HOST);
            }
         } else {
            pw.println("Unknown token on page.\nPlease refresh page");
            response.setStatus(404);
         }

      }
   }
}
