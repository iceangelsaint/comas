package edu.carleton.cas.jetty.embedded;

import com.cogerent.utility.PropertyValue;
import edu.carleton.cas.background.timers.TimerService;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.reporting.ReportManager;
import edu.carleton.cas.reporting.ReportManager.ProblemStatus;
import edu.carleton.cas.resources.Configurable;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.ui.DisappearingAlert;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;
import java.util.TimerTask;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletHolder;

@MultipartConfig
public class IDVerificationServlet extends EmbeddedWebcamServlet implements Configurable {
   private static IDVerificationServlet singleton;
   private static final int DEFAULT_MAXIMUM_ALLOWED_ATTEMPTS = 4;
   private static final int DEFAULT_ATTEMPTS_BEFORE_ID_AUTHORIZATION_PROBLEM_CREATED = 2;
   private int thresholdMatch = 0;
   private int numberOfFacesInID = 1;
   private String warning_message = "Try holding the card closer and reducing reflections";
   private String covered_or_unavailable_webcam_warning_message = "Nothing like a face could be seen. Is your webcam covered?";
   private String too_many_faces_warning_message = "Too many faces detected: ";
   private int percentageConfidence;
   private int numberOfFacesDetected;
   private int numberOfAttempts;
   private int frequency = 30000;
   private IDTimer idTimer;
   private int countOfTimeouts;
   private int maximumAttemptsAllowed;
   private boolean autoContinue;

   public IDVerificationServlet(Invigilator invigilator) {
      super(invigilator);
      this.configure(invigilator.getProperties());
      this.percentageConfidence = 0;
      this.numberOfFacesDetected = 0;
      this.numberOfAttempts = 0;
      this.countOfTimeouts = 0;
      if (singleton == null) {
         singleton = this;
      }

   }

   public int getPercentageConfidence() {
      return this.percentageConfidence;
   }

   public int getNumberOfFacesDetected() {
      return this.numberOfFacesDetected;
   }

   public int getNumberOfAttempts() {
      return this.numberOfAttempts;
   }

   public static String getMapping() {
      return "verify";
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      String myMapping = "/" + getMapping();
      ServletHolder sh = sp.addServlet(this, myMapping);
      ServletRouter sr = sp.getRouter();
      sr.addRule(myMapping, InvigilatorState.initializing);
      sr.addRule(myMapping, InvigilatorState.verifying);
      sr.addRedirect(InvigilatorState.verifying, myMapping);
      sh.getRegistration().setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir"), this.maxFileSize, this.maxRequestSize, this.fileSizeThreshold));
   }

   public static IDVerificationServlet getSingleton() {
      return singleton;
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         if (this.isRequired()) {
            this.getForm(request, response, "");
            this.resetExistingTimerAndSetupNewOne();
            this.invigilator.setLastServlet(getMapping());
         } else {
            this.invigilator.setInvigilatorState(InvigilatorState.initializing);
            response.sendRedirect(SystemWebResources.getLocalResource("progressLandingPage", "/progress"));
         }
      }

   }

   protected synchronized void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      this.saveImage("id", request);
   }

   protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      String confidence = request.getParameter("confidence");
      String count = request.getParameter("count");
      String attempts = request.getParameter("attempts");
      String _token = request.getParameter("token");
      if (confidence != null && count != null && attempts != null && _token != null) {
         if (_token.equals(this.token)) {
            try {
               int confidenceInt = Integer.parseInt(confidence.trim());
               int countInt = Integer.parseInt(count.trim());
               int attemptsInt = Integer.parseInt(attempts.trim());
               this.percentageConfidence = confidenceInt;
               this.numberOfFacesDetected = countInt;
               this.numberOfAttempts = attemptsInt;
               this.resetExistingTimerAndSetupNewOne();
            } catch (NumberFormatException var10) {
            }

            if ((long)this.percentageConfidence >= Math.round(this.detectionThreshold * (double)100.0F)) {
               this.invigilator.setInvigilatorState(InvigilatorState.initializing);
               if (this.autoContinue && this.idTimer != null) {
                  this.idTimer.cancel();
               }
            }

         }
      }
   }

   public File getWebcamIDFile() {
      return this.getImageFile("id");
   }

   public synchronized BufferedImage getWebcamIDImage() {
      return this.getImage(this.getWebcamIDFile());
   }

   private void resetExistingTimerAndSetupNewOne() {
      if (this.isRequired()) {
         ReportManager reportManager = this.invigilator.getReportManager();
         if (reportManager != null && reportManager.hasProblemWithStatus("id_not_verified", ProblemStatus.set)) {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "ID saved image", this.invigilator.createProblemClearEvent("id_not_verified"));
            this.countOfTimeouts = 0;
         }

         this.setupNewTimer();
      }
   }

   private void setupNewTimer() {
      if (this.idTimer != null) {
         this.idTimer.cancel();
      }

      this.idTimer = new IDTimer();

      try {
         TimerService.schedule(this.idTimer, (long)this.frequency);
      } catch (IllegalStateException var2) {
      }

   }

   private String tabTitle() {
      return "CoMaS ID: " + this.invigilator.getNameAndID();
   }

   private void getForm(HttpServletRequest request, HttpServletResponse response, String extraHTML) throws IOException {
      response.addHeader("Access-Control-Allow-Origin", this.invigilator.getServletProcessor().getService());
      response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
      response.setContentType("text/html");
      PrintWriter wr = response.getWriter();
      wr.print("<!DOCTYPE html>\n<html lang=\"en\">\n <head>\n  <title>");
      wr.print(this.invigilator.getTitle());
      wr.print(this.tabTitle());
      wr.print("</title>\n");
      wr.println(SystemWebResources.getStylesheet());
      wr.println(SystemWebResources.getIcon());
      wr.println(SystemWebResources.getFonts());
      wr.println(SystemWebResources.getWebcam());
      wr.println(SystemWebResources.getTesseract());
      wr.print("</head>\n<body>\n");
      wr.println(this.invigilator.getServletProcessor().checkForServletCode(0, false, "", ""));
      wr.println("<div class=\"w3-container w3-center\">");
      wr.print("<h1><img alt=\"CoMaS logo\" src=\"");
      wr.print(SystemWebResources.getAppImage());
      wr.print("\"></h1>\n");
      wr.print("  <input type=\"hidden\" id=\"token\" name=\"token\" value=\"");
      wr.print(this.token);
      wr.print("\" />\n");
      wr.print("  <input type=\"hidden\" id=\"status\" name=\"status\" value=\"");
      wr.print(this.token);
      wr.print("\" />\n");
      wr.print("</div>");
      wr.println("<div id=\"div\" style=\"text-align:center;width:50%;margin:auto\">\n");
      wr.print("<video id=\"webcam\" autoplay playsinline ");
      wr.print(this.webcam_canvas_size);
      wr.println("></video>\n");
      wr.print("<canvas id=\"canvas\" class=\"d-none\" ");
      wr.print(this.webcam_canvas_size);
      wr.println("></canvas>");
      wr.println("</div>");
      wr.println("<script type=\"module\">");
      wr.print("const url2use = '/");
      wr.print(getMapping());
      wr.print("';\n");
      wr.print("const token = document.getElementById('token').value;\n");
      wr.print("const webcamElement = document.getElementById('webcam');\n");
      wr.print("const canvasElement = document.getElementById('canvas');\n");
      wr.print("const ctx = canvasElement.getContext('2d');\n");
      wr.print("const idImage = new Image();\n");
      wr.print("idImage.addEventListener('load',() => {  ctx.drawImage(idImage, 50, 10);},false,);");
      wr.print("idImage.src = '/images/id-small.png';\n");
      wr.print("const snapSoundElement = document.getElementById('snapSound');\n");
      wr.print("const status = document.getElementById(\"status\");\n");
      wr.print("const name = \"");
      wr.print(this.invigilator.getName());
      wr.print("\";\n");
      wr.print("const id = \"");
      wr.print(this.invigilator.getID());
      wr.print("\";\n");
      wr.print("window.addEventListener('unhandledrejection', function(e) { console.log(e.reason); });\n");
      wr.print("const webcam = new Webcam(webcamElement, 'user', canvasElement, snapSoundElement);\n");
      wr.print("const faceCountRequired = ");
      wr.print(this.numberOfFacesInID);
      wr.print(";\n");
      wr.print("const detectionThreshold = ");
      wr.print(this.detectionThreshold);
      wr.print(";\n");
      wr.print("const alertTimeout = ");
      wr.print(ClientShared.DISAPPEARING_ALERT_TIMEOUT);
      wr.print(";\n");
      wr.print("const thresholdMatch=");
      wr.print(this.thresholdMatch);
      wr.print(";\n");
      wr.print("var attempts=");
      wr.print(0);
      wr.print(";\n");
      wr.print("var totalAttempts=");
      wr.print(0);
      wr.print(";\n");
      wr.print("const warningMessage=\"");
      wr.print(this.warning_message);
      wr.print("\";\n");
      wr.print("const coveredOrUnavailableWebcamMessage=\"");
      wr.print(this.covered_or_unavailable_webcam_warning_message);
      wr.print("\";\n");
      wr.print("const tooManyFacesMessage=\"");
      wr.print(this.too_many_faces_warning_message);
      wr.print("\";\n");
      wr.print("const tesseractWorkerPath=\"");
      wr.print(SystemWebResources.getTesseractWorkerPath());
      wr.print("\";\n");
      wr.print("function upload(blob, targetURL) {\n   var xhr = new XMLHttpRequest();\n   xhr.open('POST', targetURL, true);\n   xhr.onreadystatechange = function(){\n      if (this.readyState == 4) {\n         if (this.status != 200) {\n            status.innerHTML = \"✓ id upload problem\";\n            saveButton.disabled = false;\n            saveButton.value = \"Save ID ❗\";\n            alertWithTimeout(\"ID could not be saved\", alertTimeout);\n         } else {\n            compute();\n         }\n      }\n   };\n   xhr.setRequestHeader(\"Content-Type\", \"image/png\");\n   xhr.send(blob);\n}\n");
      wr.print("webcam.start()\n.then(result =>{\n   window.focus();\n   saveButton.disabled = false;\n   const webcams = webcam.webcamList;\n   console.log(webcam.webcamList);\n   if (webcams.length > 1) {\n       document.getElementById(\"div\").appendChild(document.createElement(\"P\"));\n       for (let i = 0; i < webcams.length; i++) {\n          let b = document.createElement(\"BUTTON\");\n          b.className = \"w3-button w3-green w3-round\";\n          b.style.marginBottom = \"10px\";\n          let deviceId = webcams[i].deviceId;\n          b.addEventListener('click', () => {\n             webcam.changeCamera(deviceId);\n          });\n          b.innerHTML = webcams[i].label;\n          document.getElementById(\"div\").appendChild(b);\n          document.getElementById(\"div\").appendChild(document.createElement(\"BR\"));\n       }\n   }\n})\n.catch(err => {\n   console.log(err);\n   alertWithTimeout(\"Could not start webcam: \"+err, alertTimeout);\n});\n");
      wr.print(SystemWebResources.getAlertWithTimeout());
      wr.print("function saveIDStatistics(confidence, faceCount, numberOfAttempts) {\n   var xhr = new XMLHttpRequest();\n   var erf = function(){\n      alertWithTimeout(\"ID data could not be saved\", alertTimeout);\n   };\n   xhr.onerror = erf;\n   xhr.onabort = erf;\n   xhr.ontimeout = erf;\n   const status = document.getElementById(\"status\");\n   xhr.onreadystatechange = function(){\n      if (this.readyState === 4) {\n         if (this.status !== 200) {\n            status.innerHTML = \"✓ ID data update problem\";\n            alertWithTimeout(\"ID data could not be saved\", alertTimeout);\n         }\n");
      if (this.autoContinue) {
         wr.print("         else if (!continueButton.disabled) {\n            window.location.href='");
         wr.print(SystemWebResources.getLocalResource("progressLandingPage", "/progress"));
         wr.print("';\n");
         wr.print("         }\n");
      }

      wr.print("      }\n   };\n   const formData = new FormData();\n   formData.append('token', token);\n   formData.append('confidence', confidence);\n   formData.append('count', faceCount);\n   formData.append('attempts', numberOfAttempts);\n   xhr.open('PUT', \"");
      wr.print(getMapping());
      wr.print("\", true);\n   xhr.send(formData);\n}\n");
      wr.print("function setupContinueButton(msg) {\n   saveButton.disabled = true;\n   continueButton.disabled = false;\n   saveButton.value = \"Save ID ✅ \" + msg;\n}function setupSaveButton(msg, confidence) {\n   saveButton.disabled = false;\n   continueButton.disabled = true;\n   saveButton.value = \"Save ID ❌\"+ \"( \" + confidence + \")\";\n   alertWithTimeout(msg, alertTimeout);\n}");
      wr.println("import { FaceDetector, FilesetResolver } from \"https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3\";");
      wr.println("let faceDetector;");
      wr.println("const runningMode = \"IMAGE\";");
      wr.println("const initializefaceDetector = async () => {\n\t\t\t\ttry {\n\t\t\t       const vision = await FilesetResolver.forVisionTasks(\"https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3/wasm\");\n\t\t\t       faceDetector = await FaceDetector.createFromOptions(vision, {\n\t\t\t           baseOptions: {\n\t\t\t               modelAssetPath: `https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite`,\n\t\t\t               delegate: \"GPU\"\n\t\t\t           },\n\t\t\t           runningMode: runningMode\n\t\t\t       });\n\t\t\t\t} catch(error) {\n\t\t\t\t\talertWithTimeout(error, alertTimeout);\n\t\t\t\t}\n\t\t\t};");
      wr.println("initializefaceDetector();");
      wr.print("async function compute() {\n      const detections = faceDetector.detect(canvasElement).detections;\n      const worker = await Tesseract.createWorker('eng', 1, {\n          workerPath: tesseractWorkerPath\n      });\n      await (async () => {\n         const { data: { text } } = await worker.recognize(canvasElement);\n         const lowerCaseText = text.toLowerCase();\n         console.log(lowerCaseText);\n         let idLCS = longestCommonSubstring(lowerCaseText, id);\n         console.log(\"id=\" + idLCS);\n         let nameLCS = longestCommonSubstring(lowerCaseText, name);\n         let lcsLength = Math.max(idLCS.length, nameLCS.length);\n         console.log(\"name=\" + nameLCS);\n         console.log(detections);\n         let countOfAcceptableFaces = 0;\n         let maxConfidence = 0.0;\n         for (let detection of detections) {\n            if (detection.categories[0].score >= detectionThreshold)\n               countOfAcceptableFaces++;\n            if (detection.categories[0].score > maxConfidence)\n               maxConfidence = detection.categories[0].score;\n         }\n         let confidence = Math.round(maxConfidence*100);\n         if (confidence === 0) {\n            setupSaveButton(coveredOrUnavailableWebcamMessage, confidence);\n         } else if (countOfAcceptableFaces > faceCountRequired) {\n            setupSaveButton(tooManyFacesMessage + countOfAcceptableFaces, confidence);\n            saveIDStatistics(confidence, countOfAcceptableFaces, totalAttempts);\n         } else if (detections.length > 0 &&\n            lcsLength >= thresholdMatch && countOfAcceptableFaces === faceCountRequired) {\n            setupContinueButton(\"(\"+confidence+\")\");\n            saveIDStatistics(confidence, countOfAcceptableFaces, totalAttempts);\n         } else {\n            saveButton.disabled = false;\n            continueButton.disabled = true;\n            saveButton.value = \"Save ID ❌ (\"+confidence+\")\";\n            if (attempts > 3 && warningMessage.length > 0) {\n               alertWithTimeout(warningMessage, alertTimeout);\n               attempts = 0;\n            }\n         }\n         await worker.terminate();\n      })().catch(error => {\n         setupSaveButton('There was an ID verification problem: '+error, 0);\n      });\n}\n");
      wr.print(SystemWebResources.getLCS());
      wr.println("function saveID() {");
      wr.println("   attempts++;\n");
      wr.println("   totalAttempts++;\n");
      wr.println("   saveButton.value = \"Processing ...\"");
      wr.println("   saveButton.disabled = true;");
      wr.println("   var blob = webcam.snap();");
      wr.println("   upload(blob, url2use);");
      wr.println("}");
      wr.println("   const continueButton = document.getElementById('continueButton');");
      wr.println("   continueButton.disabled = true;");
      wr.println("   const saveButton = document.getElementById('saveButton');");
      wr.println("   saveButton.addEventListener(\"click\", saveID);");
      wr.println("   saveButton.disabled = true;");
      if (this.autoContinue) {
         wr.println("   continueButton.style.display='none';");
      }

      wr.println(this.invigilator.getServletProcessor().pingForServlet(5000, true));
      wr.println("</script>");
      String buttons = "<br/><input accesskey =\"s\" class=\"w3-button w3-round-large w3-blue w3-border\" type=\"button\" id=\"saveButton\" value=\"Save ID\" />\n&nbsp;&nbsp;&nbsp;&nbsp;\n<input accesskey =\"c\" class=\"w3-button w3-round-large w3-blue w3-border\" type=\"button\" id=\"continueButton\" onclick=\"window.location.href='" + SystemWebResources.getLocalResource("progressLandingPage", "/progress") + "'\" value=\"Continue\" />\n";
      if (this.autoContinue) {
         buttons = buttons + this.invigilator.getServletProcessor().getMailButton();
      } else {
         buttons = buttons + this.invigilator.getServletProcessor().getMailButtonSeparatorBefore();
      }

      wr.println(this.invigilator.getServletProcessor().footerForServlet(true, true, buttons));
      wr.print("</body></html>");
   }

   private boolean isRequired() {
      return this.includingIDRecognition;
   }

   public void configure(Properties properties) {
      super.configure(properties);
      this.autoContinue = PropertyValue.getValue(this.invigilator, "webcam", "continue_after_id_verification", true);
      this.maximumAttemptsAllowed = PropertyValue.getValue(this.invigilator, "webcam", "terminate_after_verification_alerts", 4);
      if (this.maximumAttemptsAllowed <= 0) {
         this.maximumAttemptsAllowed = Integer.MAX_VALUE;
      }

      if (properties.containsKey("webcam.id_timeout")) {
         String frequencyAsString = properties.getProperty("webcam.id_timeout");

         try {
            this.frequency = Integer.parseInt(frequencyAsString.trim()) * 1000;
            if (this.frequency < 0 || this.frequency > 60000) {
               throw new NumberFormatException("ID snapshot timeout out of range (0,60) seconds");
            }

            Logger.log(java.util.logging.Level.CONFIG, String.format("ID snapshot timeout set to %s seconds", frequencyAsString), "");
         } catch (NumberFormatException var7) {
            this.frequency = 30000;
            Logger.log(java.util.logging.Level.WARNING, String.format("ID snapshot timeout was not an acceptable number: %s, using %d seconds", frequencyAsString, this.frequency / 1000), "");
         }
      } else {
         this.frequency = 30000;
      }

      String value = PropertyValue.getValue(this.invigilator, "webcam.id", "number_of_faces", "1");

      try {
         this.numberOfFacesInID = Integer.parseInt(value);
         if (this.numberOfFacesInID < 1 || this.numberOfFacesInID > 10) {
            throw new NumberFormatException("ID face count requirement match out of range (1,10)");
         }

         Logger.log(java.util.logging.Level.CONFIG, String.format("Webcam id face count threshold match set to %s", value), "");
      } catch (NumberFormatException var6) {
         this.numberOfFacesInID = 1;
         Logger.log(java.util.logging.Level.WARNING, String.format("Webcam id face count match was not an acceptable number: %s, using %d", value, this.numberOfFacesInID), "");
      }

      if (properties.containsKey("webcam.id.threshold_match") && this.includingIDRecognition) {
         String countString = properties.getProperty("webcam.id.threshold_match", "0");

         try {
            this.thresholdMatch = Integer.parseInt(countString.trim());
            if (this.thresholdMatch < 0 || this.thresholdMatch > 10) {
               throw new NumberFormatException("ID detection threshold match out of range (0,10)");
            }

            Logger.log(java.util.logging.Level.CONFIG, String.format("Webcam id detection threshold match set to %s", countString), "");
         } catch (NumberFormatException var5) {
            this.thresholdMatch = 0;
            Logger.log(java.util.logging.Level.WARNING, String.format("Webcam id threshold match was not an acceptable number: %s, using %d", countString, this.thresholdMatch), "");
         }
      } else {
         this.thresholdMatch = 0;
      }

      if (properties.containsKey("webcam.id.warning_message") && this.includingIDRecognition) {
         this.warning_message = properties.getProperty("webcam.id.warning_message", "").trim();
      }

      if (properties.containsKey("webcam.id.covered_warning_message") && this.includingIDRecognition) {
         this.covered_or_unavailable_webcam_warning_message = properties.getProperty("webcam.id.covered_warning_message", "").trim();
      }

      if (properties.containsKey("webcam.id.too_many_faces_warning_message") && this.includingIDRecognition) {
         this.too_many_faces_warning_message = properties.getProperty("webcam.id.too_many_faces_warning_message", "").trim();
      }

      (new File(ClientShared.getScreensDirectory(this.invigilator.getCourse(), this.invigilator.getActivity()))).mkdirs();
   }

   private class IDTimer extends TimerTask {
      public void run() {
         if (IDVerificationServlet.this.invigilator.isInInvigilatorState(InvigilatorState.running) || IDVerificationServlet.this.invigilator.isInInvigilatorState(InvigilatorState.initializing) && IDVerificationServlet.this.autoContinue) {
            ReportManager reportManager = IDVerificationServlet.this.invigilator.getReportManager();
            if (reportManager != null && reportManager.hasProblemWithStatus("id_not_verified", ProblemStatus.set)) {
               IDVerificationServlet.this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "ID saved image", IDVerificationServlet.this.invigilator.createProblemClearEvent("id_not_verified"));
               IDVerificationServlet.this.countOfTimeouts = 0;
            }

         } else if (!IDVerificationServlet.this.distributionOfImageOk) {
            IDVerificationServlet.this.distributionOfImageOk = true;
            IDVerificationServlet.this.setupNewTimer();
         } else {
            ++IDVerificationServlet.this.countOfTimeouts;
            int timeouts = IDVerificationServlet.this.countOfTimeouts;
            if (timeouts > 2) {
               IDVerificationServlet.this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "ID verification required", IDVerificationServlet.this.invigilator.createProblemSetEvent("id_not_verified"));
            }

            int type = timeouts > IDVerificationServlet.this.maximumAttemptsAllowed ? 0 : 2;
            DisappearingAlert da = new DisappearingAlert((long)ClientShared.DISAPPEARING_ALERT_TIMEOUT, 1, type);
            Runnable action;
            String msg;
            if (timeouts > IDVerificationServlet.this.maximumAttemptsAllowed) {
               msg = "You have failed to save an ID.\nPlease close your browser tab.\n\nYour session will now end.";
               IDVerificationServlet.this.invigilator.setInvigilatorState(InvigilatorState.ending);
               action = new SessionEndTask(IDVerificationServlet.this.invigilator, ProgressServlet.getSingleton(), "Session ended because " + IDVerificationServlet.this.invigilator.getNameAndID() + " failed to provide a valid ID");
            } else {
               String var10000 = IDVerificationServlet.this.tabTitle();
               msg = "No ID has been saved recently. Is browser ID tab running?\nPlease go to the \"" + var10000 + "\" tab and click on \"Save ID\" button.\nIf \"Save ID\" button is greyed out, click on \"Continue\" button.\nYou may also recreate the session page at " + IDVerificationServlet.this.invigilator.getServletProcessor().getService() + "\n\nYou have " + (IDVerificationServlet.this.maximumAttemptsAllowed - timeouts + 1) * IDVerificationServlet.this.frequency / 1000 + " secs before your session ends";
               action = new Runnable() {
                  public void run() {
                     IDVerificationServlet.this.setupNewTimer();
                  }
               };
            }

            da.setRunOnCloseRegardless(action);
            da.show(msg, "CoMaS ID Alert(" + timeouts + ")!");
         }
      }
   }
}
