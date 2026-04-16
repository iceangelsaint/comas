package edu.carleton.cas.jetty.embedded;

import com.cogerent.launcher.LauncherChecker;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.ui.WebAlert;
import edu.carleton.cas.utility.ClientConfiguration;
import edu.carleton.cas.utility.Sleeper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Properties;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@MultipartConfig
public abstract class BasicLoginServlet extends EmbeddedServlet {
   public static final int MAXIMUM_SERVER_RETRIES = 10;
   public static final int SERVER_AUTHENTICATION_RETRY_SLEEP = 2000;
   public static final int SERVER_RANDOM_RETRY_SLEEP = 1000;
   public static final int MAX_FIELD_LENGTH = 32;
   protected String[] courses;
   protected String server_to_try = null;

   public BasicLoginServlet(Invigilator invigilator) {
      super(invigilator);
      this.courses = this.getCourses(ClientShared.SERVER_CHOSEN);
   }

   public static String getMapping() {
      return LoginServlet.getMapping();
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         this.getForm(request, response, this.courses.length == 0 ? "alert(\"There are no courses available\");" : "");
         this.invigilator.setLastServlet(getMapping());
      }

      String userAgent = request.getHeader("User-Agent");
      if (userAgent != null) {
         this.invigilator.setProperty("session.userAgent", userAgent);
      }

   }

   protected Response authenticate(int retries) {
      int authentication_tries = 0;

      Response response;
      do {
         try {
            response = this.invigilator.authenticate();
         } catch (Exception var8) {
            response = null;
            Sleeper.sleep(2000, 1000);
         } finally {
            ++authentication_tries;
         }
      } while(response == null && authentication_tries < retries);

      return response;
   }

   protected JsonObject authenticateAndProcessResponseForSpecialConditions(String course, PrintWriter pw, HttpServletResponse response) {
      Response resp = this.authenticate(10);
      if (resp == null || resp.getStatusInfo() != Status.OK) {
         this.server_to_try = this.getPrimaryOrBackupServer(true);
         if (this.server_to_try != null) {
            ClientShared.CONFIGS.setProperty(course + ".hostname", this.server_to_try);
            ClientShared.setupURLs(course);
            response.setStatus(404);
            pw.print("Please retry as server temporarily unavailable");
            return null;
         }

         String msg = String.format("%s is not responding.\nTry using backup server provided in email.\n\nYour session will now end", ClientShared.DIRECTORY_HOST);
         WebAlert.exitAfterAlert(msg, -1);
      }

      String rtn = (String)resp.readEntity(String.class);
      if (rtn.equalsIgnoreCase("{\"IS RESTRICTED\"}")) {
         response.setStatus(404);
         pw.print(this.invigilator.getNameAndID() + "  has been denied access. You may not login. Please close browser tab");
         if (!this.invigilator.isInEndingState()) {
            this.invigilator.setInvigilatorState(InvigilatorState.ending);
            Thread t = new Thread(new SessionEndTask(this.invigilator, ProgressServlet.getSingleton(), "Session ended because " + this.invigilator.getNameAndID() + " denied access"));
            t.start();
         }

         return null;
      } else if (rtn.equalsIgnoreCase("{\"DOES NOT EXIST\"}")) {
         response.setStatus(404);
         String var10001 = this.invigilator.getEmail();
         pw.print("User not found: " + var10001 + " for " + this.invigilator.getCourse());
         return null;
      } else if (rtn.equalsIgnoreCase("{\"ILLEGAL VERSION\"}")) {
         response.setStatus(404);
         pw.print("Your CoMaS version is not correct. You should quit now");
         return null;
      } else if (!rtn.equalsIgnoreCase("{\"STOPPED\"}") && !rtn.equalsIgnoreCase("{\"STOPPING\"}")) {
         if (!rtn.startsWith("<html") && !rtn.startsWith("<!doctype") && !rtn.startsWith("<!DOCTYPE")) {
            JsonReader reader = Json.createReader(new StringReader(rtn));
            JsonObject meAsJson = reader.readObject();
            return meAsJson;
         } else {
            response.setStatus(404);
            pw.print("A error occurred on the server: " + ClientShared.DIRECTORY_HOST);
            return null;
         }
      } else {
         response.setStatus(404);
         pw.print("The CoMaS server is unavailable: " + ClientShared.DIRECTORY_HOST);
         return null;
      }
   }

   protected void getTop(PrintWriter wr, ClientConfiguration cc) {
      wr.print("<!DOCTYPE html>\n<html lang=\"en\">\n <head>\n  <title>");
      wr.print(this.invigilator.getTitle());
      wr.print("CoMaS Login</title>\n");
      wr.println(SystemWebResources.getStylesheet());
      wr.println(SystemWebResources.getIcon());
      wr.println("</head>\n<body>\n");
      wr.println(this.invigilator.getServletProcessor().checkForServletCode((String)null));
      wr.println("<div class=\"w3-container w3-center\" id=\"form-wrapper\" style=\"width:30%;margin:auto;\">");
      wr.print("<h1><img alt=\"CoMaS logo\" src=\"");
      wr.print(SystemWebResources.getAppImage());
      wr.print("\"></h1>\n");
      wr.print("  <input type=\"hidden\" id=\"token\" name=\"token\" value=\"");
      wr.print(this.token);
      wr.print("\" />\n  <table class=\"w3-table w3-centered\">\n<tr>");
      this.addCourseSelectionOptions(wr, cc.getCourse() == null ? "" : cc.getCourse());
      wr.print("  </tr><tr>\n");
   }

   protected void getTail(PrintWriter wr, String extraHTML, String mapping, String formData) {
      wr.print("  </tr>\n  </table>\n  <br/>\n");
      wr.println("  </div>\n");
      ServletProcessor var10001 = this.invigilator.getServletProcessor();
      String var10004 = this.invigilator.getServletProcessor().getMailButtonSeparatorAfter();
      wr.println(var10001.footerForServlet(true, true, var10004 + "  <input accesskey=\"s\" class=\"w3-button w3-round-large w3-blue w3-border\" id=\"signIn\" type=\"button\" onclick=\"continueWorkflow('/" + mapping + "','/activity')\" value=\"Sign In\" />\n"));
      if (extraHTML != null && extraHTML.length() > 0) {
         wr.println("<script>document.body.onload = function() {");
         wr.println(extraHTML);
         wr.println("};</script>");
      }

      wr.println("<script>");
      wr.println("   const signInButton = document.getElementById('signIn');");
      wr.println("   function continueWorkflow(action, nextPage) {");
      wr.println("      var xhttp = new XMLHttpRequest();");
      wr.println("      xhttp.onreadystatechange = function() {");
      wr.println("         if (this.readyState == 4 && this.status == 200) {");
      wr.println("            window.location.href=nextPage;");
      wr.println("         } else if (this.readyState == 4 && this.status >= 400) {");
      wr.println("            signInButton.disabled = false;");
      wr.println("            alert(xhttp.responseText);");
      wr.println("         }");
      wr.println("      };");
      wr.println("      signInButton.disabled = true;");
      wr.println("      const formData = new FormData();");
      wr.println(formData);
      wr.println("      xhttp.open(\"POST\", action);");
      wr.println("      xhttp.send(formData);");
      wr.println("   }");
      wr.println(this.invigilator.getServletProcessor().pingForServlet(true));
      wr.println("</script>");
      wr.println("</body>\n</html>");
   }

   protected void getForm(HttpServletRequest request, HttpServletResponse response, String extraHTML) throws IOException {
      response.addHeader("Access-Control-Allow-Origin", "*");
      response.setContentType("text/html");
      PrintWriter wr = response.getWriter();
      ClientConfiguration cc = this.invigilator.getClientConfiguration();
      this.getTop(wr, cc);
      wr.print("  <td><label style=\"vertical-align:bottom\" for=\"first\">First:</label></td>\n");
      wr.print("  <td><input accesskey=\"f\" type=\"text\" name=\"first\" id=\"first\" value=\"");
      wr.print(cc.getFirst() == null ? "" : cc.getFirst());
      wr.print("\" minlength=\"1\" maxlength=\"32\" required/></td>\n  </tr>\n    <tr>\n  <td><label style=\"vertical-align:bottom\" for=\"last\">Last:</label></td>\n  <td><input accesskey=\"l\" type=\"text\" name=\"last\" id=\"last\" value=\"");
      wr.print(cc.getLast() == null ? "" : cc.getLast());
      wr.print("\" minlength=\"1\" maxlength=\"32\" required/></td>\n  </tr>\n  <tr>\n  <td><label style=\"vertical-align:bottom\" for=\"id\">ID:</label></td>\n  <td><input accesskey=\"i\" type=\"text\" id=\"id\" name=\"id\" value=\"");
      wr.print(cc.getID() == null ? "" : cc.getID());
      wr.print("\" pattern=\"");
      wr.print(SystemWebResources.getIDPattern());
      wr.print("\" required/></td>\n");
      String formData = "\tformData.append(\"first\", document.getElementById(\"first\").value);\n\tformData.append(\"last\", document.getElementById(\"last\").value);\n\tformData.append(\"course\", document.getElementById(\"course\").value);\n\tformData.append(\"id\", document.getElementById(\"id\").value);\n\tformData.append(\"token\", document.getElementById(\"token\").value);\n";
      this.getTail(wr, extraHTML, getMapping(), formData);
   }

   protected void addCourseSelectionOptions(PrintWriter wr, String last_course) {
      wr.print("  <p><label class=\"w3-label w3-validate\" for=\"course\">Course:</label>\n");
      wr.print("  <select accesskey=\"c\" class=\"w3-row w3-blue\" id=\"course\" name=\"course\">");

      String[] var6;
      for(String course : var6 = this.courses) {
         course = course.trim();
         wr.print("<option value=\"");
         wr.print(course);
         wr.print("\"");
         if (last_course != null && course.equals(last_course)) {
            wr.print(" selected=\"selected\"");
         }

         wr.print(">");
         wr.print(course);
         wr.print("</option>");
      }

      wr.print("</select></p>");
   }

   protected void setupCourseLoginStateAndURLs(String course) {
      this.invigilator.setCourse(course);
      this.invigilator.setActualState("Logging in");
      ClientShared.setupPrimaryServer(course);
      ClientShared.setupURLs(course);
   }

   protected void postProcessToken(JsonObject meAsJson, String course) {
      JsonString tokenAsJsonString = meAsJson.getJsonString("TOKEN");
      if (tokenAsJsonString != null) {
         String tokenAsString = tokenAsJsonString.getString();
         if (tokenAsString != null) {
            this.invigilator.setToken(tokenAsString);
         }
      }

      if (this.invigilator.getToken() != null) {
         Properties properties = ClientShared.getClientLoginProperties(course, this.invigilator.getToken());
         if (properties != null && !properties.isEmpty()) {
            this.invigilator.mergeProperties(properties);
         }

         this.invigilator.setActualState("Login");

         try {
            LauncherChecker lc = new LauncherChecker(this.invigilator);
            lc.checkAndUpgradeIfRequired();
         } catch (Exception e) {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Launcher check exception: " + Utils.printFirstApplicationStackFrameOrException(e));
         }
      }

   }

   protected void postProcessLoginForm(HttpServletResponse response) throws IOException {
      if (!this.invigilator.isDone() && !this.invigilator.isInEndingState()) {
         if (ClientShared.BACKUP_SERVERS != null) {
            ClientShared.BACKUP_SERVERS.setServerInUse(this.server_to_try);
         }

         if (ActivityServlet.getSingleton() == null) {
            ActivityServlet as = new ActivityServlet(this.invigilator);
            as.addServletHandler();
            boolean backPageAllowed = Utils.getBooleanOrDefault(this.invigilator.getProperties(), "session.allow_back_page", false) || Utils.getBooleanOrDefault(this.invigilator.getProperties(), "session." + this.invigilator.getID() + ".allow_back_page", false);
            if (!backPageAllowed) {
               this.invigilator.setInvigilatorState(InvigilatorState.choosing);
            }
         }

         response.sendRedirect(SystemWebResources.getLocalResource("activityLandingPage", "/activity"));
      } else {
         response.sendRedirect(SystemWebResources.getLocalResource("endLandingPage", "/end"));
      }

   }

   protected String[] getCourses(String host) {
      if (host == null) {
         return null;
      } else {
         Properties configs = null;
         ClientShared.EXAM_CONFIGURATION_FILE = ClientShared.examDotIni(host);
         configs = Utils.getProperties(ClientShared.EXAM_CONFIGURATION_FILE, "Cookie", "token=0.7.15");
         if (configs == null || configs.isEmpty()) {
            configs = Utils.getProperties(ClientShared.LOCAL_EXAM_CONFIGURATION_FILE, "Cookie", "token=0.7.15");
         }

         Logger.output("Server is " + host);
         Logger.output("Configuration is " + String.valueOf(configs));
         if (configs != null && !configs.isEmpty()) {
            ClientShared.CONFIGS = configs;
            String courses = configs.getProperty("courses");
            if (courses != null) {
               String[] possibilities = courses.split(",");

               for(int i = 0; i < possibilities.length; ++i) {
                  possibilities[i] = possibilities[i].trim();
               }

               Arrays.sort(possibilities);
               return possibilities;
            }
         }

         return new String[0];
      }
   }

   protected String getPrimaryOrBackupServer(boolean used) {
      String server = ClientShared.PRIMARY_SERVERS.next(used);
      if (server != null) {
         return server;
      } else {
         return ClientShared.BACKUP_SERVERS != null ? ClientShared.BACKUP_SERVERS.next(used) : server;
      }
   }
}
