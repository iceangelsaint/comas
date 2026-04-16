package edu.carleton.cas.jetty.embedded;

import com.cogerent.utility.PropertyValue;
import edu.carleton.cas.background.LogArchiver;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.DirectoryUtils;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.security.IllegalVersionException;
import edu.carleton.cas.ui.WebAlert;
import edu.carleton.cas.utility.ClientConfiguration;
import edu.carleton.cas.utility.ClientHelper;
import edu.carleton.cas.utility.Named;
import edu.carleton.cas.utility.Password;
import edu.carleton.cas.utility.Sleeper;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.regex.PatternSyntaxException;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.stream.JsonParsingException;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import org.eclipse.jetty.servlet.ServletHolder;

@MultipartConfig
public class ActivityServlet extends EmbeddedServlet {
   private static ActivityServlet singleton;
   public static final int MAXIMUM_SERVER_RETRIES = 10;
   public static final int SERVER_RETRY_SLEEP = 3000;
   public static final int SERVER_RANDOM_RETRY_SLEEP = 1000;
   private String[] activities = this.getActivities();

   public ActivityServlet(Invigilator invigilator) {
      super(invigilator);
      if (singleton == null) {
         singleton = this;
      }

   }

   public static ActivityServlet getSingleton() {
      return singleton;
   }

   public static String getMapping() {
      return "activity";
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      String myMapping = "/" + getMapping();
      ServletHolder sh = sp.addServlet(this, myMapping);
      ServletRouter sr = sp.getRouter();
      sr.addRule(myMapping, InvigilatorState.loggingIn);
      sr.addRule(myMapping, InvigilatorState.choosing);
      sr.addRedirect(InvigilatorState.choosing, myMapping);
      sh.getRegistration().setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir"), this.maxFileSize, this.maxRequestSize, this.fileSizeThreshold));
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         this.getForm(request, response, this.activities.length == 0 ? "alert(\"There are no activities available\");" : "");
         this.invigilator.setLastServlet(getMapping());
      }

   }

   protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         PrintWriter pw = response.getWriter();
         String tokenFromForm = request.getParameter("token");
         if (tokenFromForm != null && tokenFromForm.equals(this.token)) {
            String activity = request.getParameter("activity");
            if (activity == null || activity.length() > 32) {
               pw.println("Activity name is invalid");
               response.setStatus(404);
               return;
            }

            try {
               if (!activity.matches(SystemWebResources.getEntityPattern())) {
                  pw.println("Activity name is invalid");
                  response.setStatus(404);
                  return;
               }
            } catch (PatternSyntaxException var13) {
               pw.println("A pattern matching error occurred. Please contact support");
               response.setStatus(404);
               return;
            }

            String passcode = request.getParameter("passcode");
            if (this.passcodeRequired()) {
               if (passcode == null || passcode.length() > 32) {
                  pw.println("Passcode is invalid");
                  response.setStatus(404);
                  return;
               }

               try {
                  if (!passcode.matches(SystemWebResources.getPasscodePattern())) {
                     pw.println("Passcode is invalid");
                     response.setStatus(404);
                     return;
                  }
               } catch (PatternSyntaxException var12) {
                  pw.println("A pattern matching error occurred. Please contact support");
                  response.setStatus(404);
                  return;
               }
            }

            try {
               this.invigilator.setActivity(activity);
               if (!this.getActivity()) {
                  pw.println("The " + activity + " could not be obtained from " + ClientShared.DIRECTORY_HOST);
                  response.setStatus(404);
               }

               if (!this.invigilator.canRunAtThisIPAddress()) {
                  this.invigilator.setStateAndAuthenticate("Terminated");
                  this.invigilator.setEndOfSessionStateFlags();
                  WebAlert.exitAfterAlert("Session can't be run from this address", -1);
               }

               if (!this.invigilator.isDone() && !this.invigilator.isInEndingState()) {
                  if (this.passcodeIsOk(passcode)) {
                     if (!this.setupRootFolderForExamUsage()) {
                        EndedServlet.getSingleton().setMessage("Your session will now end as creation of the folder for " + this.invigilator.getSessionContext() + " failed");
                        response.sendRedirect(SystemWebResources.getLocalResource("endLandingPage", "/end"));
                        return;
                     }

                     if (!this.overwriteExistingActivityDirectoryOrConfirmNoDownload()) {
                        EndedServlet.getSingleton().setMessage("Your session will now end as overwriting of " + this.invigilator.getSessionContext() + " was denied");
                        response.sendRedirect(SystemWebResources.getLocalResource("endLandingPage", "/end"));
                        return;
                     }

                     this.saveConfiguration(activity);
                     this.invigilator.setProperty("student.directory.protocol", ClientShared.PROTOCOL);
                     this.invigilator.setProperty("student.directory.ws_protocol", ClientShared.WS_PROTOCOL);
                     this.invigilator.setProperty("student.directory.activity", activity);
                     this.invigilator.setProperty("student.directory.webserver", this.invigilator.getServletProcessor().getService());
                     this.invigilator.setProperty("student.directory.host", ClientShared.WEBSOCKET_HOST);
                     this.invigilator.setProperty("student.directory.port", ClientShared.PORT);
                     AgreementServlet as = new AgreementServlet(this.invigilator);
                     as.configure();
                     as.addServletHandler();
                     MessageOfTheDayServlet ms = new MessageOfTheDayServlet(this.invigilator);
                     ms.addServletHandler();
                     IDVerificationServlet idvs = new IDVerificationServlet(this.invigilator);
                     idvs.addServletHandler();
                     WebcamServlet ws = new WebcamServlet(this.invigilator);
                     GazeServlet es = new GazeServlet(this.invigilator);
                     ws.addServletHandler();
                     es.addServletHandler();
                     SystemWebResources.configure(this.invigilator.getProperties());
                     this.invigilator.setInvigilatorState(InvigilatorState.authorizing);
                     response.sendRedirect(SystemWebResources.getLocalResource("agreementLandingPage", "/agreement"));
                  } else {
                     pw.println("The passcode was not correct for " + this.invigilator.getSessionContext());
                     response.setStatus(404);
                  }
               } else {
                  response.sendRedirect(SystemWebResources.getLocalResource("endLandingPage", "/end"));
               }
            } catch (Exception var14) {
               String var10001 = this.invigilator.getSessionContext();
               pw.println("The " + var10001 + " details could not be obtained from " + ClientShared.DIRECTORY_HOST + ".");
               pw.println("Please refresh page and try again");
               response.setStatus(404);
            }
         } else {
            pw.println("Unknown token on page.\nPlease refresh page");
            response.setStatus(404);
         }

      }
   }

   private void getForm(HttpServletRequest request, HttpServletResponse response, String extraHTML) throws IOException {
      response.addHeader("Access-Control-Allow-Origin", "*");
      response.setContentType("text/html");
      PrintWriter wr = response.getWriter();
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
      ClientConfiguration cc = this.invigilator.getClientConfiguration();
      wr.print(this.token);
      wr.print("\" />\n  <table class=\"w3-table w3-centered\">\n<tr>");
      this.addActivitySelectionOptions(wr, cc.getActivity() == null ? "" : cc.getActivity());
      wr.print("  </tr>");
      if (this.passcodeRequired()) {
         wr.print("<tr>\n  <td><label style=\"vertical-align:bottom\" class=\"w3-label\" for=\"passcode\">Passcode:</label></td>\n");
         wr.print("  <td><input accesskey=\"p\" class=\"w3-input\" type=\"text\" name=\"passcode\" id=\"passcode\" value=\"");
         wr.print("");
         wr.print("\" minlength=\"1\" maxlength=\"16\" required/></td>\n  </tr>\n");
      }

      wr.print("  </table>\n  <br/>\n");
      wr.println("  </div>\n");
      wr.println(this.invigilator.getServletProcessor().footerForServlet(true, true, this.invigilator.getServletProcessor().getMailButtonSeparatorAfter() + "  <input accesskey =\"c\" class=\"w3-button w3-round-large w3-blue w3-border\" type=\"button\" id=\"signIn\" onclick=\"continueWorkflow('/activity', '/progress')\" value=\"Continue\" />\n"));
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
      wr.println("     };");
      wr.println("     signInButton.disabled = true;");
      wr.println("     var formData = new FormData();");
      if (this.passcodeRequired()) {
         wr.println("     formData.append(\"passcode\", document.getElementById(\"passcode\").value);");
      } else {
         wr.println("     formData.append(\"passcode\", \"activity_code_not_used\");");
      }

      wr.println("     formData.append(\"activity\", document.getElementById(\"activity\").value);");
      wr.println("     formData.append(\"token\", document.getElementById(\"token\").value);");
      wr.println("     xhttp.open(\"POST\", action);");
      wr.println("     xhttp.send(formData);");
      wr.println("   }");
      wr.println(this.invigilator.getServletProcessor().pingForServlet(true));
      wr.println("</script>");
      wr.println("</body>\n</html>");
   }

   private void saveConfiguration(String activity) {
      boolean publicSession = Utils.getBooleanOrDefault(this.invigilator.getProperties(), "session.public", false) || Utils.getBooleanOrDefault(this.invigilator.getProperties(), "session." + this.invigilator.getID() + ".public", false);
      if (!publicSession) {
         ClientConfiguration cc = this.invigilator.getClientConfiguration();
         cc.setProperty(SystemWebResources.getVariable("name"), SystemWebResources.getNamePattern());
         cc.setProperty(SystemWebResources.getVariable("id"), SystemWebResources.getIDPattern());
         cc.setProperty(SystemWebResources.getVariable("passcode"), SystemWebResources.getPasscodePattern());
         cc.setProperty(SystemWebResources.getVariable("entity"), SystemWebResources.getEntityPattern());
         cc.setStartupScreenURI(this.invigilator.getProperty("session.startup_screen"));
         cc.setActivity(activity);
         long comasPID = ProcessHandle.current().pid();
         cc.setPID(comasPID);
         String mailTo = this.invigilator.getProperty("mail.to", "").trim();
         if (mailTo.length() == 0) {
            cc.removeProperty("mail.to");
         } else {
            cc.setProperty("mail.to", mailTo);
         }

         cc.save();
      }

   }

   private void addActivitySelectionOptions(PrintWriter wr, String last_activity) {
      wr.print("  <p><label class=\"w3-label\" for=\"activity\">Activity:</label>\n");
      wr.print("  <select accesskey=\"a\" class=\"w3-row w3-blue\" id=\"activity\" name=\"activity\">");

      String[] var6;
      for(String activity : var6 = this.activities) {
         activity = activity.trim();
         wr.print("<option value=\"");
         wr.print(activity);
         wr.print("\"");
         if (last_activity != null && activity.equals(last_activity)) {
            wr.print(" selected=\"selected\"");
         }

         wr.print(">");
         wr.print(activity);
         wr.print("</option>");
      }

      wr.print("</select></p>");
   }

   private String[] getActivities() {
      String[] possibilities = null;
      int tries = 0;

      while(tries < 10 && possibilities == null) {
         try {
            ++tries;
            Response response = this.activities();
            if (response != null) {
               String activitiesAsJson = (String)response.readEntity(String.class);
               if (activitiesAsJson.equalsIgnoreCase("{\"ILLEGAL VERSION\"}")) {
                  throw new IllegalVersionException(ClientShared.VERSION);
               }

               Logger.output("Activities:");
               Logger.output(activitiesAsJson);
               JsonReader reader = Json.createReader(new StringReader(activitiesAsJson));
               JsonArray meAsJson = reader.readArray();
               possibilities = new String[meAsJson.size()];

               for(int i = 0; i < meAsJson.size(); ++i) {
                  possibilities[i] = Named.unquoted(meAsJson.getJsonString(i).toString());
                  possibilities[i] = possibilities[i].trim();
               }

               Arrays.sort(possibilities);
            }
         } catch (IllegalVersionException var8) {
            tries = 10;
            possibilities = null;
         } catch (Exception var9) {
            if (tries < 10) {
               Sleeper.sleep(3000, 1000);
            } else {
               possibilities = null;
            }
         }
      }

      return possibilities == null ? new String[0] : possibilities;
   }

   public Response activities() throws ProcessingException, ResponseProcessingException, WebApplicationException {
      Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
      WebTarget webTarget = client.target(ClientShared.BASE_LOGIN).path("startable-activities");
      Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
      invocationBuilder.accept(new String[]{"application/json"});
      if (this.invigilator.getToken() != null) {
         invocationBuilder.cookie("token", this.invigilator.getToken());
      }

      Form form = new Form();
      form.param("course", this.invigilator.getCourse());
      form.param("version", ClientShared.VERSION);
      form.param("passkey", ClientShared.PASSKEY_DIRECTORY);
      Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
      return response;
   }

   private boolean getActivity() {
      int tries = 0;
      boolean activityOk = false;

      while(tries < 10 && !activityOk) {
         ++tries;

         try {
            Response response = this.activity();
            if (response != null) {
               String activitiesAsJson = (String)response.readEntity(String.class);
               if (activitiesAsJson.equals("{\"ILLEGAL VERSION\"}")) {
                  throw new IllegalVersionException(ClientShared.VERSION);
               }

               JsonReader reader = Json.createReader(new StringReader(activitiesAsJson));
               JsonObject activityAsJson = reader.readObject();

               for(Map.Entry entry : activityAsJson.entrySet()) {
                  String key = Named.unquoted(((String)entry.getKey()).toString());
                  String value = Named.unquoted(((JsonValue)entry.getValue()).toString());
                  if (!key.equals("DESCRIPTION")) {
                     this.invigilator.setProperty(key, value);
                  }
               }

               ClientShared.updateSessionParameters(this.invigilator.getProperties(), false);
               QuitServlet.getSingleton().setMessage(this.invigilator.resolveVariablesInMessage(ClientShared.END_MESSAGE));
               tries = 10;
               activityOk = true;
            }
         } catch (IllegalVersionException var11) {
            tries = 10;
         } catch (Exception e) {
            if (e instanceof JsonParsingException) {
               this.invigilator.logArchiver.put(Level.SEVERE, "Option parsing error. Correct either course or activity options");
               tries = 9;
            }

            if (tries < 10) {
               Sleeper.sleep(3000, 1000);
            }
         }
      }

      Logger.output("Session Configuration:");
      Logger.output(this.invigilator.getProperties().toString());
      return activityOk;
   }

   private Response activity() throws ProcessingException, ResponseProcessingException, WebApplicationException {
      Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
      WebTarget webTarget = client.target(ClientShared.BASE_LOGIN).path("activities").path(this.invigilator.getActivity());
      Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
      invocationBuilder.accept(new String[]{"application/json"});
      if (this.invigilator.getToken() != null) {
         invocationBuilder.cookie("token", this.invigilator.getToken());
      }

      Form form = new Form();
      form.param("course", this.invigilator.getCourse());
      form.param("version", ClientShared.VERSION);
      form.param("passkey", ClientShared.PASSKEY_DIRECTORY);
      form.param("name", this.invigilator.getName());
      Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
      return response;
   }

   private boolean passcodeRequired() {
      boolean isRequired = ClientShared.USE_ACTIVITY_CODES || ClientShared.USE_STUDENT_CODES;
      return isRequired;
   }

   private boolean passcodeIsOk(String passcode) {
      if (ClientShared.USE_ACTIVITY_CODES) {
         return this.activityPasscodeIsOk(passcode);
      } else {
         return ClientShared.USE_STUDENT_CODES ? this.studentPasscodeIsOk(passcode) : true;
      }
   }

   private boolean studentPasscodeIsOk(String passcode) {
      String studentPasscode = this.invigilator.getProperty("student.directory.PASSCODE");
      return studentPasscode == null ? false : passcode.equals(studentPasscode);
   }

   private boolean activityPasscodeIsOk(String studentPasscode) {
      String saltAsString = this.invigilator.getProperty("SALT");
      String passcode = this.invigilator.getProperty("PASSCODE");
      if (saltAsString != null && passcode != null) {
         byte[] salt = Password.stringToByte(saltAsString);
         String securePassCode = Password.getSecurePassword(studentPasscode, salt);
         return securePassCode.equals(passcode);
      } else {
         return false;
      }
   }

   private boolean setupRootFolderForExamUsage() {
      Properties config = this.invigilator.getProperties();
      String folderRequired = config.getProperty("session." + this.invigilator.getID() + ".folder");
      if (folderRequired != null) {
         return this.setupSessionFolder(folderRequired, "Session folder %s is unavailable, cannot proceed", true);
      } else if (Utils.getBooleanOrDefault(config, "session." + this.invigilator.getID() + ".use_default_folder", false)) {
         return true;
      } else {
         String os = ClientShared.getOSString();
         folderRequired = config.getProperty("session." + os + ".folder");
         if (folderRequired != null) {
            return this.setupSessionFolder(folderRequired, "Session folder %s is unavailable, cannot proceed", true);
         } else {
            folderRequired = config.getProperty("session.folder");
            if (folderRequired != null) {
               return this.setupSessionFolder(folderRequired, "Session folder %s is unavailable, cannot proceed", true);
            } else {
               return Utils.getBooleanOrDefault(config, "session.use_temp_folder", false) ? this.setupSessionFolder(System.getProperty("java.io.tmpdir"), "Temporary folder %s is unavailable, cannot proceed", false) : true;
            }
         }
      }
   }

   public String resolveFolderVariables(String folder) {
      Properties config = this.invigilator.getProperties();
      String resolved = folder.replace("${NAME}", this.invigilator.getCanonicalStudentName());
      resolved = resolved.replace("${/}", File.separator);
      resolved = resolved.replace("${HOME}", System.getProperty("user.home"));
      resolved = resolved.replace("${ID}", this.invigilator.getID());
      resolved = resolved.replace("${COURSE}", this.invigilator.getCourse());
      resolved = resolved.replace("${ACTIVITY}", this.invigilator.getActivity());
      resolved = resolved.replace("${TIME}", String.format("%d", System.currentTimeMillis()));
      resolved = resolved.replace("${RANDOM}", Password.getPassCode());
      String value = config.getProperty("PASSWORD");
      if (value == null) {
         value = config.getProperty("student.directory.PASSWORD");
      }

      if (value == null) {
         value = config.getProperty("PASSCODE");
      }

      resolved = resolved.replace("${PASSWORD}", value == null ? "" : value);
      value = config.getProperty("PASSCODE");
      if (value == null) {
         value = config.getProperty("student.directory.PASSCODE");
      }

      resolved = resolved.replace("${PASSCODE}", value == null ? "" : value);
      resolved = resolved.replace("${TEMP}", System.getProperty("java.io.tmpdir"));
      resolved = resolved.replace("${DRIVE}", ClientShared.isWindowsOS() ? ClientShared.DIR_DRIVE : "");
      resolved = resolved.replace("${MACHINE}", this.invigilator.getHardwareAndSoftwareMonitor().getComputerIdentifier());

      try {
         if (ClientShared.isWindowsOS()) {
            resolved = resolved.replaceAll("\\{2,}", "\\");
         } else {
            resolved = resolved.replaceAll("/{2,}", "/");
         }
      } catch (Exception var7) {
         resolved = resolved;
      }

      return resolved;
   }

   private boolean setupSessionFolder(String folder, String msg, boolean create) {
      String actualFolder = this.resolveFolderVariables(folder.trim());
      File file_folder = new File(actualFolder);
      if (create) {
         file_folder.mkdirs();
      }

      if (file_folder.exists() && file_folder.isDirectory() && file_folder.canWrite()) {
         if (actualFolder.charAt(actualFolder.length() - 1) == File.separatorChar) {
            actualFolder = actualFolder.substring(0, actualFolder.length() - 1);
         }

         ClientShared.DIR = actualFolder;
         return true;
      } else {
         String error = String.format(msg, actualFolder);
         this.invigilator.logArchiver.put(Level.DIAGNOSTIC, error + ". Session ended");
         WebAlert.errorDialog(error);
         return false;
      }
   }

   private boolean overwriteExistingActivityDirectoryOrConfirmNoDownload() {
      if (!this.invigilator.isBeingHarvested() && !this.invigilator.isBeingRestarted() && !PropertyValue.getValue(this.invigilator, "session", "resumable", false)) {
         Properties config = this.invigilator.getProperties();
         String course = this.invigilator.getCourse();
         String activity = this.invigilator.getActivity();
         String activityDirectory = ClientShared.getActivityDirectory(course, activity);
         File archives = new File(activityDirectory);
         if (!archives.exists()) {
            return true;
         } else {
            if (!Utils.isTrueOrYes(this.invigilator.getProperty("RESUMABLE"), false)) {
               int res = WebAlert.confirmDialog("Are you sure you want to overwrite " + this.invigilator.getSessionContext() + "?", "Overwrite activity?");
               if (res != 0) {
                  return false;
               }

               try {
                  DirectoryUtils.destroyDirectoryContents(activityDirectory);
               } catch (IOException e1) {
                  String msg = "Failed to delete " + activityDirectory + ".\n\nYour session will now end.\n\nPlease remove " + activityDirectory + " and try again.";
                  LogArchiver var10000 = this.invigilator.logArchiver;
                  java.util.logging.Level var10001 = Level.DIAGNOSTIC;
                  String var10002 = this.invigilator.getNameAndID();
                  var10000.put(var10001, "Session ended because " + var10002 + " failed to delete " + activityDirectory + ". Reason: " + String.valueOf(e1));
                  this.invigilator.setStateAndAuthenticateCommon("Terminated");
                  WebAlert.exitAfterAlert(msg, -5);
                  return false;
               }
            } else {
               File[] archiveFiles = archives.listFiles();
               if (archiveFiles != null && archiveFiles.length > 0) {
                  int res = WebAlert.confirmDialog("Do you want to download a new copy of the activity?\nThis will overwrite your work.", "New activity download?");
                  if (res == 1) {
                     Logger.output("No download required");
                     config.setProperty("NO_DOWNLOAD_REQUIRED", "true");
                  } else {
                     String examDirectory = activityDirectory + File.separator + "exam";
                     Logger.output("Overwriting " + examDirectory);
                     config.remove("NO_DOWNLOAD_REQUIRED");

                     try {
                        DirectoryUtils.destroyDirectoryContents(examDirectory);
                     } catch (IOException e1) {
                        String msg = "Failed to delete " + examDirectory + ".\n\nYour session will now end.\n\nPlease remove " + examDirectory + " and try again.";
                        LogArchiver var15 = this.invigilator.logArchiver;
                        java.util.logging.Level var16 = Level.DIAGNOSTIC;
                        String var17 = this.invigilator.getNameAndID();
                        var15.put(var16, "Session ended because " + var17 + " failed to delete " + examDirectory + ". Reason: " + String.valueOf(e1));
                        this.invigilator.setStateAndAuthenticateCommon("Terminated");
                        WebAlert.exitAfterAlert(msg, -5);
                        return false;
                     }
                  }
               }
            }

            return true;
         }
      } else {
         return true;
      }
   }
}
