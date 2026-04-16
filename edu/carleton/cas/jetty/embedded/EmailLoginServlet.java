package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.utility.ClientConfiguration;
import edu.carleton.cas.utility.Named;
import edu.carleton.cas.utility.Sleeper;
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
import javax.ws.rs.core.Response;
import org.eclipse.jetty.servlet.ServletHolder;

@MultipartConfig
public class EmailLoginServlet extends BasicLoginServlet {
   public EmailLoginServlet(Invigilator invigilator) {
      super(invigilator);
   }

   public static String getMapping() {
      return "elogin";
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
            String email = request.getParameter("email");
            if (email == null || email.length() > 64) {
               pw.println("Email is invalid");
               response.setStatus(404);
               return;
            }

            try {
               if (!email.matches(SystemWebResources.getEmailPattern())) {
                  pw.println("Email is invalid");
                  response.setStatus(404);
                  return;
               }
            } catch (PatternSyntaxException var16) {
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
            } catch (PatternSyntaxException var19) {
               pw.println("A pattern matching error occurred. Please contact support");
               response.setStatus(404);
               return;
            }

            this.invigilator.setEmail(email);
            this.setupCourseLoginStateAndURLs(course);
            ClientShared.setupPrimaryServer(course);
            ClientShared.setupURLs(course);

            try {
               JsonObject meAsJson = this.authenticateAndProcessResponseForSpecialConditions(course, pw, response);
               if (meAsJson == null) {
                  return;
               }

               String id = null;
               String name = null;
               String first = null;
               String last = null;

               for(Map.Entry entry : meAsJson.entrySet()) {
                  if (!((String)entry.getKey()).contains("-")) {
                     String key = (String)entry.getKey();
                     String value = ((JsonValue)entry.getValue()).toString();
                     if (key.equals("EMAIL")) {
                        email = Named.unquoted(value);
                        this.invigilator.setEmail(email);
                     } else if (key.equals("ID")) {
                        id = Named.unquoted(value);
                        this.invigilator.setID(id);
                     } else if (key.equals("name")) {
                        name = Named.unquoted(value);
                        this.invigilator.setFullName(name);
                     } else if (key.equals("FIRST_NAME")) {
                        first = Named.unquoted(value);
                     } else if (key.equals("LAST_NAME")) {
                        last = Named.unquoted(value);
                     }

                     this.invigilator.setProperty("student.directory." + key, Named.unquoted(value));
                  }
               }

               this.postProcessToken(meAsJson, course);
               boolean publicSession = Utils.getBooleanOrDefault(this.invigilator.getProperties(), "session.public", false) || Utils.getBooleanOrDefault(this.invigilator.getProperties(), "session." + this.invigilator.getID() + ".public", false);
               if (!publicSession) {
                  ClientConfiguration cc = this.invigilator.getClientConfiguration();
                  if (first == null && last == null) {
                     if (name != null) {
                        String[] tokens = name.split(" ");
                        first = tokens[0];
                        cc.setFirst(tokens[0]);
                        last = "";
                        if (tokens.length <= 2) {
                           if (tokens.length == 2) {
                              last = tokens[1];
                           }
                        } else {
                           for(int i = 1; i < tokens.length; ++i) {
                              last = last + tokens[i] + " ";
                           }

                           last = last.trim();
                        }

                        cc.setLast(last);
                     }
                  } else {
                     cc.setLast(last);
                     cc.setFirst(first);
                     this.invigilator.setName(first, last);
                  }

                  cc.setID(id);
                  cc.setEmail(email);
                  cc.setLastSession(System.currentTimeMillis());
                  cc.setCourse(course);
                  cc.setIPv4Address(this.invigilator.getHardwareAndSoftwareMonitor().getIPv4Address());
                  if (ClientShared.SERVER_CHOSEN != null) {
                     cc.setRecentHost(ClientShared.SERVER_CHOSEN);
                  }

                  cc.save(String.format("CoMaS server location information for %s %s (%s). DO NOT EDIT", first, last, id));
               }

               this.postProcessLoginForm(response);
            } catch (IOException var17) {
               response.setStatus(404);
               pw.print("Authentication was not possible, please try again");
            } catch (IllegalStateException | JsonException var18) {
               response.setStatus(404);
               pw.print("An unexpected response was received from " + ClientShared.DIRECTORY_HOST);
            }
         } else {
            pw.println("Unknown token on page.\nPlease refresh page");
            response.setStatus(404);
         }

      }
   }

   protected Response authenticate(int retries) {
      int authentication_tries = 0;

      Response response;
      do {
         try {
            response = this.invigilator.authenticateUsingEmail();
         } catch (Exception var8) {
            response = null;
            Sleeper.sleep(2000, 1000);
         } finally {
            ++authentication_tries;
         }
      } while(response == null && authentication_tries < retries);

      return response;
   }

   protected void getForm(HttpServletRequest request, HttpServletResponse response, String extraHTML) throws IOException {
      response.addHeader("Access-Control-Allow-Origin", "*");
      response.setContentType("text/html");
      PrintWriter wr = response.getWriter();
      ClientConfiguration cc = this.invigilator.getClientConfiguration();
      this.getTop(wr, cc);
      wr.print("  <td><label style=\"vertical-align:bottom\" for=\"id\">Email:</label></td>\n  <td><input accesskey=\"e\" type=\"email\" id=\"email\" name=\"email\" value=\"");
      wr.print(cc.getEmail() == null ? "" : cc.getEmail());
      wr.print("\" pattern=\"");
      wr.print(SystemWebResources.getEmailPattern());
      wr.print("\" minlength=\"1\" maxlength=\"32\" required/></td>\n");
      String formData = "\tformData.append(\"email\", document.getElementById(\"email\").value);\tformData.append(\"course\", document.getElementById(\"course\").value);\tformData.append(\"token\", document.getElementById(\"token\").value);";
      this.getTail(wr, extraHTML, getMapping(), formData);
   }
}
