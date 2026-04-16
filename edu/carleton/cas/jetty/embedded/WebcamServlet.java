package edu.carleton.cas.jetty.embedded;

import com.cogerent.utility.PropertyValue;
import edu.carleton.cas.background.timers.TimerService;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
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
import java.util.ArrayList;
import java.util.Properties;
import java.util.TimerTask;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletHolder;

@MultipartConfig
public class WebcamServlet extends EmbeddedWebcamServlet implements Configurable {
   private static WebcamServlet singleton;
   private static final int DEFAULT_MAXIMUM_ALLOWED_ATTEMPTS = 4;
   private static final int DEFAULT_ATTEMPTS_BEFORE_WEBCAM_AUTHORIZATION_PROBLEM_CREATED = 2;
   private final ArrayList events = new ArrayList();
   private int defaultEventSize = 32;
   private String tableLabel = "EVENT DESCRIPTION";
   private boolean includingWebcam = false;
   private boolean includingFaceDetection = false;
   private boolean includingGazeDetection = false;
   private boolean includingGestureDetection = false;
   private int frequency = 30000;
   private int detectionFrequency = 30000;
   private WebcamTimer webcamTimer;
   private int countOfTimeouts = 0;
   private int maximumAttemptsAllowed = 4;
   private boolean closeOnExitAllowed = true;
   private boolean disablePiP = true;
   private int maxUploadErrors = 10;

   public WebcamServlet(Invigilator invigilator) {
      super(invigilator);
      this.configure(invigilator.getProperties());
      if (singleton == null) {
         singleton = this;
      }

   }

   public static String getMapping() {
      return "webcam";
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      String myMapping = "/" + getMapping();
      ServletHolder sh = sp.addServlet(this, myMapping);
      ServletRouter sr = sp.getRouter();
      sr.addRule(myMapping, InvigilatorState.running);
      sh.getRegistration().setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir"), this.maxFileSize, this.maxRequestSize, this.fileSizeThreshold));
   }

   public static WebcamServlet getSingleton() {
      return singleton;
   }

   public boolean isIncludingWebcam() {
      return this.includingWebcam;
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         this.getForm(request, response, "");
         this.resetExistingTimerAndSetupNewOne();
         this.invigilator.setLastServlet(getMapping());
      }

   }

   protected synchronized void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      this.resetExistingTimerAndSetupNewOne();
      this.saveImage("webcam", request);
      this.checkImage("webcam");
   }

   protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      String type = request.getParameter("type");
      String _token = request.getParameter("token");
      if (type != null && _token != null) {
         if (_token.equals(this.token)) {
            if (type.equals("event")) {
               this.processEvent(request, response);
            } else if (type.equals("status")) {
               this.processStatus(request, response);
            } else {
               response.setStatus(500);
            }

         }
      }
   }

   private void processEvent(HttpServletRequest request, HttpServletResponse response) {
      String name = request.getParameter("name");
      String state = request.getParameter("state");
      if (name != null && state != null) {
         if (state.equals("set") || state.equals("clear")) {
            synchronized(this.events) {
               for(Event event : this.events) {
                  if (event.getName().equals(name)) {
                     if (state.equals("set")) {
                        event.setChecked(true);
                     } else if (state.equals("clear")) {
                        event.setChecked(false);
                     }

                     if (name.startsWith("default")) {
                        String description = request.getParameter("description");
                        if (event.isChecked()) {
                           event.setDescription(description == null ? "" : description);
                           event.setValue(event.description);
                        }
                     }

                     if (event.type == WebcamServlet.EventType.select) {
                        String value = request.getParameter("value");
                        event.setValue(value == null ? "" : value);
                     }

                     if (event.value.length() == 0) {
                        return;
                     }

                     this.distributeMessage("event", event.value + ":" + state);
                     edu.carleton.cas.events.Event evt = new edu.carleton.cas.events.Event();
                     evt.put("time", System.currentTimeMillis());
                     evt.put("description", event.value);
                     evt.put("severity", state);
                     evt.put("args", new Object[]{"event"});
                     this.invigilator.getReportManager().notify(evt);
                     break;
                  }
               }

            }
         }
      }
   }

   public int getNumberOfCheckedEvents() {
      int numberOfCheckedEvents = 0;
      synchronized(this.events) {
         for(Event event : this.events) {
            if (event.isChecked()) {
               ++numberOfCheckedEvents;
            }
         }

         return numberOfCheckedEvents;
      }
   }

   private void resetExistingTimerAndSetupNewOne() {
      if (this.includingWebcam) {
         ReportManager reportManager = this.invigilator.getReportManager();
         if (reportManager != null && reportManager.hasProblemWithStatus("webcam_not_authorized", ProblemStatus.set)) {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Webcam authorization given", this.invigilator.createProblemClearEvent("webcam_not_authorized"));
            this.countOfTimeouts = 0;
         }

         this.setupNewTimer();
      }
   }

   private void setupNewTimer() {
      if (this.webcamTimer != null) {
         this.webcamTimer.cancel();
      }

      this.webcamTimer = new WebcamTimer();

      try {
         TimerService.schedule(this.webcamTimer, (long)(2 * this.frequency));
      } catch (IllegalStateException var2) {
      }

   }

   public File getWebcamFile() {
      File currentFile = this.getImageFile("webcam");
      return System.currentTimeMillis() - currentFile.lastModified() > (long)(2 * this.frequency) ? null : currentFile;
   }

   public synchronized BufferedImage getWebcamImage() {
      return this.getImage(this.getWebcamFile());
   }

   private String tabTitle() {
      return this.includingWebcam ? "CoMaS Webcam: " + this.invigilator.getNameAndID() : "CoMaS Events: " + this.invigilator.getNameAndID();
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
      if (this.includingWebcam) {
         wr.println(SystemWebResources.getWebcam());
      }

      wr.print("</head>\n<body>\n");
      wr.println(this.invigilator.getServletProcessor().checkForServletCode(0, false, "", ""));
      wr.println("<div class=\"w3-container w3-center\">");
      wr.print("<h1><img alt=\"CoMaS logo\" src=\"");
      wr.print(SystemWebResources.getAppImage());
      wr.print("\"></h1>\n");
      wr.print("  <input type=\"hidden\" id=\"token\" name=\"token\" value=\"");
      wr.print(this.token);
      wr.print("\" />\n");
      wr.print("<p id=\"status\"></p>");
      wr.print("</div>");
      if (this.includingWebcam) {
         wr.println("<div id=\"div\" style=\"text-align:center\">\n");
         wr.print("<video id=\"webcam\" enablePictureInPicture autoplay playsinline ");
         wr.print(this.webcam_canvas_size);
         wr.println("></video>\n");
         wr.print("<canvas id=\"canvas\" class=\"d-none\" ");
         wr.print(this.webcam_canvas_size);
         wr.println("></canvas>\n</div>\n");
      }

      this.generateEventCheckBoxes(wr);
      wr.println("<script type=\"module\">");
      wr.println(this.invigilator.getServletProcessor().pingForServlet(true));
      this.generateEventCheckBoxesScript(wr);
      if (this.includingWebcam) {
         wr.println("var timerId;");
         wr.print("const freq=");
         wr.print(this.frequency);
         wr.println(";");
         wr.print("const url2use = '/");
         wr.print(getMapping());
         wr.print("';\n");
      }

      wr.print("const token = document.getElementById('token').value;\n");
      wr.print("const id = \"");
      wr.print(this.invigilator.getID());
      wr.print("\";\n");
      wr.print("var uploadInProgress = false;\n");
      wr.print("var manualCloseAllowed = ");
      wr.print(this.closeOnExitAllowed);
      wr.print(";\n");
      wr.print("const status = document.getElementById(\"status\");\n");
      if (this.includingWebcam) {
         wr.print("var snapshot_taken = false;\n");
         wr.print("const webcamElement = document.getElementById('webcam');\n");
         if (this.disablePiP) {
            wr.print("webcamElement.disablePictureInPicture = true;\n");
         }

         wr.print("const canvasElement = document.getElementById('canvas');\n");
         wr.print("const snapSoundElement = document.getElementById('snapSound');\n");
         wr.print("const name = \"");
         wr.print(this.invigilator.getName());
         wr.print("\";\n");
         wr.print("window.addEventListener('unhandledrejection', function(e) { console.log(e.reason); });\n");
         wr.print("window.onbeforeunload = function(e) { wsSendMessage(\"webcam\", \"stopped\");\nif (uploadInProgress || !manualCloseAllowed) {\n   e.preventDefault();\n}\n};\n");
         wr.print("const webcam = new Webcam(webcamElement, 'user', canvasElement, snapSoundElement);\n");
      }

      if (this.includingFaceDetection) {
         wr.print("const detectionFrequency=");
         wr.print(this.detectionFrequency);
         wr.print(";\n");
         wr.print("const detectionThreshold = ");
         wr.print(this.detectionThreshold);
         wr.print(";\n");
         wr.print("const alertTimeout = ");
         wr.print(ClientShared.DISAPPEARING_ALERT_TIMEOUT);
         wr.print(";\n");
      }

      if (this.includingWebcam) {
         wr.print("var uploadErrors=0;\n");
         wr.print("const maxUploadErrors=");
         wr.print(this.maxUploadErrors);
         wr.print(";\n");
         wr.print("function upload(blob, targetURL) {\n   if (uploadInProgress) {\n      console.log(\"WARNING: An upload is already in progress\");\n      return;\n   }\n   var sdt = new Date();\n   var stme = sdt.getTime();\n   uploadInProgress = true;\n   var xhr = new XMLHttpRequest();\n   var erf = function(){\n      uploadInProgress = false;\n      uploadErrors++;\n      if (uploadErrors > maxUploadErrors)\n         window.location.reload();\n   };\n   xhr.onerror = erf;\n   xhr.onabort = erf;\n   xhr.ontimeout = erf;\n   xhr.open('POST', targetURL, true);\n   xhr.onreadystatechange = function(){\n      if (this.readyState === 4) {\n         uploadInProgress = false;\n         uploadErrors = 0;\n         if (this.status !== 200) {\n            status.innerHTML = \"✓ image upload problem\";\n            alertWithTimeout(\"Image could not be saved\", alertTimeout);\n         } else {\n            var edt = new Date();\n            var elapsed = edt.getTime() - stme;\n            console.log(\"IMAGE SAVED[\"+this.status+\"] in \"+elapsed+\" msecs\");\n         }\n      }\n   };\n   xhr.setRequestHeader(\"Content-Type\", \"image/png\");\n   xhr.send(blob);\n}\nfunction autosaveWebcamImage() {\n   try {\n      var picture = webcam.snap();\n      upload(picture, url2use);\n      snapshot_taken = true;\n      canvas.style.display=\"none\";\n   } catch (error) {\n      uploadErrors++;\n   }\n   timerId = setTimeout(autosaveWebcamImage, freq);\n}\n");
         wr.print("webcam.start()\n.then(result =>{\n   window.focus();\n   canvas.style.display=\"none\";\n   timerId = setTimeout(autosaveWebcamImage, freq / 2);\n   wsSendMessage(\"webcam\", \"started\");\n   const webcams = webcam.webcamList;\n   console.log(webcam.webcamList);\n   if (webcams.length > 1) {\n       document.getElementById(\"div\").appendChild(document.createElement(\"P\"));\n       for (let i = 0; i < webcams.length; i++) {\n          let b = document.createElement(\"BUTTON\");\n          b.className = \"w3-button w3-green w3-round\";\n          b.style.marginBottom = \"10px\";\n          let deviceId = webcams[i].deviceId;\n          b.addEventListener('click', () => {\n             webcam.changeCamera(deviceId);\n          });\n          b.innerHTML = webcams[i].label;\n          document.getElementById(\"div\").appendChild(b);\n          document.getElementById(\"div\").appendChild(document.createElement(\"BR\"));\n       }\n   }\n");
         if (this.includingFaceDetection) {
            wr.print("   setInterval( compute, detectionFrequency);\n");
         }

         wr.print("})\n.catch(err => {\n   console.log(err);\n   alertWithTimeout(\"Could not start webcam: \"+err, alertTimeout);\n   wsSendMessage(\"webcam\", \"error\");\n});\n");
         if (this.includingFaceDetection) {
            if (this.includingGazeDetection && this.includingGestureDetection) {
               wr.print("import { FaceLandmarker, GestureRecognizer, FaceDetector, FilesetResolver, DrawingUtils } ");
            } else if (this.includingGazeDetection) {
               wr.print("import { FaceLandmarker, FaceDetector, FilesetResolver, DrawingUtils } ");
            } else if (this.includingGestureDetection) {
               wr.print("import { GestureRecognizer, FaceDetector, FilesetResolver, DrawingUtils } ");
            } else {
               wr.print("import { FaceDetector, FilesetResolver, DrawingUtils } ");
            }

            wr.println("from \"https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3\";");
            wr.println("let faceDetector;");
            if (this.includingGestureDetection) {
               wr.println("let gestureRecognizer;");
            }

            if (this.includingGazeDetection) {
               wr.println("let faceLandmarker;");
            }

            wr.println("let runningMode = \"IMAGE\";");
            wr.println("const initializefaceDetector = async () => {\n\t\t\t\ttry {\n\t\t\t       const vision = await FilesetResolver.forVisionTasks(\"https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3/wasm\");\n\t\t\t       faceDetector = await FaceDetector.createFromOptions(vision, {\n\t\t\t           baseOptions: {\n\t\t\t               modelAssetPath: `https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite`,\n\t\t\t               delegate: \"GPU\"\n\t\t\t           },\n\t\t\t           runningMode: runningMode\n\t\t\t       });\n");
            if (this.includingGestureDetection) {
               wr.println("                gestureRecognizer = await GestureRecognizer.createFromOptions(vision, {\n                    baseOptions: {\n                        modelAssetPath: \"https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task\",\n                        delegate: \"GPU\"\n                    },\n                    runningMode: \"VIDEO\"\n                });\n");
            }

            if (this.includingGazeDetection) {
               wr.println("                faceLandmarker = await FaceLandmarker.createFromOptions(vision, {\n                    baseOptions: {\n                        modelAssetPath: `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task`,\n                        delegate: \"GPU\"\n                    },\n                    outputFaceBlendshapes: true,\n                    runningMode: \"VIDEO\",\n                    numFaces: 1\n                });\n");
            }

            wr.println("\t\t\t\t} catch(error) {\n\t\t\t\t\talertWithTimeout(error, alertTimeout);\n\t\t\t\t}\n\t\t\t};");
            wr.println("initializefaceDetector();");
            if (this.includingGazeDetection || this.includingGestureDetection) {
               wr.println("webcamElement.addEventListener(\"loadeddata\", predictFromVideoFeed);");
            }

            wr.print("async function compute() {\n   if (snapshot_taken && faceDetector !== null) {\n      try {\n         const detections = faceDetector.detect(canvasElement).detections;\n         console.log(detections);\n         face(detections, detectionThreshold);\n");
            if (this.includingGazeDetection) {
               wr.print("   await faceLandmarker.setOptions({ runningMode : \"IMAGE\" });\n");
               wr.print("   resultsLandmarker = faceLandmarker.detect(canvasElement);\n");
               wr.print("   await faceLandmarker.setOptions({ runningMode : \"VIDEO\" });\n");
               wr.println("   if (resultsLandmarker.faceBlendshapes.length > 0) {\n       if (resultsLandmarker.faceBlendshapes[0].categories !== undefined) {\n          const eyes = resultsLandmarker.faceBlendshapes[0].categories.filter( (shape) => {\n             return shape.categoryName.startsWith('eyeLook');\n          });\n          let maxLeft = predictFromEyes(eyes, 'Left');\n          let maxRight = predictFromEyes(eyes, 'Right');\n          eyeTrackingInference(maxLeft, maxRight, eyes);\n       }\n    }\n");
            }

            wr.print("      } catch (error) {\n         console.log(error);\n      }\n   }\n}\n");
            if (this.includingGazeDetection || this.includingGestureDetection) {
               wr.print("let studentID=\"");
               wr.print(this.invigilator.getID());
               wr.println("\";\n");
               wr.println("let lastVideoTime = -1;\nlet lastOutputTime = -1;\nlet results = undefined;\nlet lastGesture = 'nothing';\nlet resultsLandmarker = undefined;function predictFromVideoFeed() {\n    let nowInMs = Date.now();    if (webcamElement.currentTime !== lastVideoTime) {\n        lastVideoTime = webcamElement.currentTime;\n");
            }

            if (this.includingGestureDetection) {
               wr.print("        results = gestureRecognizer.recognizeForVideo(webcamElement, nowInMs);\n");
            }

            if (this.includingGazeDetection) {
               wr.print("        resultsLandmarker = faceLandmarker.detectForVideo(webcamElement, nowInMs);\n");
            }

            if (this.includingGazeDetection || this.includingGestureDetection) {
               wr.print("    }\n");
            }

            if (this.includingGestureDetection) {
               wr.print("    if (results.gestures.length > 0) {\n       const categoryName = results.gestures[0][0].categoryName;\n       results.gestures = [];\n       if (categoryName === 'Open_Palm')\n          openWindowUsingServer(\"/");
               wr.print(QuitServlet.getMapping());
               wr.println("\", \"Quit\");\n       if (categoryName === 'Thumb_Up' && categoryName !== lastGesture) {\n            lastGesture = categoryName;\n            wsSendMessage('event', 'student ' + studentID + ' problem:clear');\n       }\n       if (categoryName === 'Thumb_Down' && categoryName !== lastGesture) {\n            lastGesture = categoryName;\n            wsSendMessage('event', 'student ' + studentID + ' problem:set');\n       }\n       if (categoryName === 'Pointing_Up')\n          openWindowUsingServer(\"/pages/Chat.html\", \"Chat\");\n");
               if (this.includingGazeDetection) {
                  wr.print("       if (categoryName === 'Victory')\n          openWindowUsingServer(\"/");
                  wr.print(GazeServlet.getMapping());
                  wr.println("\", \"Gaze\");\n");
               }

               wr.println("    }\n");
            }

            if (this.includingGazeDetection) {
               wr.println("    if (resultsLandmarker.faceBlendshapes.length > 0 && (nowInMs - lastOutputTime) > 1000) {\n        lastOutputTime = nowInMs;\n        if (resultsLandmarker.faceBlendshapes[0].categories !== undefined) {\n           const eyes = resultsLandmarker.faceBlendshapes[0].categories.filter( (shape) => {\n              return shape.categoryName.startsWith('eyeLook');\n           });\n           let maxLeft = predictFromEyes(eyes, 'Left');\n           let maxRight = predictFromEyes(eyes, 'Right');\n           eyeTrackingInference(maxLeft, maxRight, eyes);\n        }\n    }\n    window.requestAnimationFrame(predictFromVideoFeed);}");
            }

            if (this.includingGazeDetection) {
               wr.println("function predictFromEyes(shapes, eye) {\n   let maxScoreForEye = 0.0;\n   let maxEye = undefined;\n   for (let shape of shapes) {\n      if (shape.categoryName.indexOf(eye) > -1) {\n         if (maxScoreForEye < shape.score) {\n            maxEye = shape;\n            maxScoreForEye = shape.score;\n         }\n      }\n   }\n   return maxEye;\n}\n");
            }

            if (this.includingGazeDetection) {
               wr.println("function eyeTrackingInference(leftEye, rightEye, eyes) {\n   if (!leftEye || !rightEye)\n      return;\n   saveEyeTrackingInformation(leftEye, rightEye, eyes);\n}");
            }

            wr.print("var last_prediction = -1;\nfunction face(predictions, threshold) {\n   var current = predictions.length;\n   if (last_prediction !== current) {\n      if (current > 0) {\n\t\t\tif (current > 1) {\n\t\t\t\twsSendMessage(\"webcam\", \"multiple_faces:\"+current);\n\t\t\t} else {\n\t\t\t\tif (last_prediction > 1) {\n\t\t\t\t\twsSendMessage(\"webcam\", \"multiple_faces:1\");\n\t\t\t\t}\n\t\t\t\twsSendMessage(\"webcam\", \"faces:1\");\n\t\t\t}\n\t\t } else {\n\t\t\tif (last_prediction > 1) {\n\t\t\t\twsSendMessage(\"webcam\", \"multiple_faces:0\");\n\t\t\t}\n\t\t\twsSendMessage(\"webcam\", \"faces:0\");\n\t\t }\n\t\t last_prediction = current;\n\t  }\n}");
            if (this.includingGazeDetection) {
               wr.print("function saveEyeTrackingInformation(leftEye, rightEye, eyes) {\n   var xhr = new XMLHttpRequest();\n   var erf = function(){\n      alertWithTimeout(\"Eye tracking data could not be saved\", alertTimeout);\n   };\n   xhr.onerror = erf;\n   xhr.onabort = erf;\n   xhr.ontimeout = erf;\n   xhr.onreadystatechange = function(){\n      if (this.readyState === 4) {\n         if (this.status !== 200) {\n            status.innerHTML = \"✓ eye tracking data problem\";\n            alertWithTimeout(\"Eye tracking data could not be saved\", alertTimeout);\n         }\n      }\n   };\n   const formData = new FormData();\n   formData.append('token', token);\n   formData.append('type', 'eye');\n   formData.append('leftEye', JSON.stringify(leftEye));\n   formData.append('rightEye', JSON.stringify(rightEye));\n   formData.append('all', JSON.stringify(eyes));\n   xhr.open('POST', \"/gaze\", true);\n   xhr.send(formData);\n}\n");
            }
         }
      }

      wr.print("function saveEvent(name, state, type) {\n   var xhr = new XMLHttpRequest();\n   var erf = function(){\n      alertWithTimeout(\"Event could not be saved\", alertTimeout);\n   };\n   xhr.onerror = erf;\n   xhr.onabort = erf;\n   xhr.ontimeout = erf;\n   xhr.onreadystatechange = function(){\n      if (this.readyState === 4) {\n         if (this.status !== 200) {\n            status.innerHTML = \"✓ event update problem\";\n            alertWithTimeout(\"Event could not be saved\", alertTimeout);\n         }\n      }\n   };\n   const formData = new FormData();\n   formData.append('token', token);\n   formData.append('type', type);\n   formData.append('name', name);\n   formData.append('state', state);\n   if (name.startsWith('default')) {\n      formData.append('description', document.getElementById(name+\"-input\").value);\n   }\n   var namedSelector = document.getElementById(name+\"-select\");\n   if (namedSelector !== null) {\n      formData.append('value', namedSelector.value);\n   }\n   xhr.open('PUT', \"/");
      wr.print(getMapping());
      wr.print("\", true);\n   xhr.send(formData);\n}\n");
      if (this.includingGestureDetection) {
         wr.print("function openWindowUsingServer(url, page) {\n   var xhr = new XMLHttpRequest();\n   var erf = function(){\n      alertWithTimeout(\"The \" + page + \" page could not be opened\", alertTimeout);\n   };\n   xhr.onerror = erf;\n   xhr.onabort = erf;\n   xhr.ontimeout = erf;\n   xhr.onreadystatechange = function(){\n      if (this.readyState === 4) {\n         if (this.status !== 200) {\n            status.innerHTML = \"✓ page opening problem\";\n            alertWithTimeout(\"The \" + page + \" page could not be opened\", alertTimeout);\n         }\n      }\n   };\n   const formData = new FormData();\n   formData.append('url', url);\n   formData.append('token', \"");
         wr.print(HostServlet.getSingleton().token);
         wr.print("\");\n   xhr.open('PUT', \"/");
         wr.print(HostServlet.getMapping());
         wr.print("\", true);\n   xhr.send(formData);\n}\n");
      }

      wr.print(SystemWebResources.getAlertWithTimeout());
      wr.print("function wsSendMessage(cmd, content) {\n");
      wr.print("   saveEvent(cmd, content, 'status');\n");
      wr.print("}\n");
      if (this.includingIDRecognition) {
         wr.print("    wsSendMessage(\"webcam\", \"student-id-detected:\"+id);\n");
         wr.print("    wsSendMessage(\"event\", \"student id verification:clear\");\n");
      }

      wr.println("window.onpageshow = function(event) {\n        if (event.persisted) {\n            window.location.reload();\n        }\n    };");
      wr.print("</script>\n");
      ServletProcessor var10001 = this.invigilator.getServletProcessor();
      String var10004 = this.invigilator.getServletProcessor().getMailButtonSeparatorAfter();
      wr.println(var10001.footerForServlet(true, true, var10004 + SystemWebResources.getHomeButton("")));
      wr.print("</body></html> ");
   }

   private void generateEventCheckBoxes(PrintWriter wr) {
      if (!this.events.isEmpty()) {
         wr.print("\n<br>\n<div id=\"event-check-boxes\" class=\"w3-container\"><table class=\"w3-table-all w3-centered\" style=\"margin:auto;width:40%\"><thead><th style=\"width:10%\">SELECT</th><th style=\"width:30%\">");
         wr.print(this.tableLabel);
         wr.print("</th></thead><tbody>");

         for(Event evt : this.events) {
            wr.print("<tr><td>");
            wr.print("<input class=\"selection-box\" type=\"checkbox\" id=\"");
            wr.print(evt.name);
            if (evt.type == WebcamServlet.EventType.userDefined) {
               wr.print("\" onchange='checkBoxEventForDefault(\"");
            } else if (evt.type == WebcamServlet.EventType.select) {
               wr.print("\" onchange='checkBoxEventForSelect(\"");
            } else {
               wr.print("\" onchange='checkBoxEvent(\"");
            }

            wr.print(evt.name);
            wr.print("\",\"");
            wr.print(evt.alert);
            wr.print("\")' value=\"");
            wr.print(evt.description);
            wr.print("\"");
            if (evt.isChecked()) {
               wr.print(" checked");
            }

            wr.print("></td>");
            wr.print("<td>");
            if (evt.type != WebcamServlet.EventType.select) {
               if (evt.type == WebcamServlet.EventType.userDefined) {
                  wr.print("<input type=\"text\" size=\"");
                  wr.print(this.defaultEventSize);
                  wr.print("\" id=\"");
                  wr.print(evt.name);
                  wr.print("-input\"");
                  if (evt.description != null) {
                     wr.print(" value=\"");
                     wr.print(evt.description);
                     wr.print("\"");
                     if (evt.isChecked()) {
                        wr.print(" readonly");
                     }
                  }

                  wr.print("/>");
               } else {
                  wr.print("<span id=\"");
                  wr.print(evt.name);
                  wr.print("-text\">");
                  wr.print(evt.description);
                  wr.print("</span>");
               }
            } else {
               String[] choices = evt.description.split(":");
               if (choices.length > 0) {
                  wr.print("<select class=\"w3-input w3-centered\" style=\"margin:auto;width:40%\" name=\"");
                  wr.print(evt.name + "-select");
                  wr.print("\" id=\"");
                  wr.print(evt.name + "-select\">");

                  for(String choice : choices) {
                     choice = choice.trim();
                     if (choice.length() > 0) {
                        wr.print("<option value=\"");
                        wr.print(choice);
                        wr.print("\"");
                        if (evt.value.equals(choice)) {
                           wr.print(" selected");
                        }

                        wr.print(">");
                        wr.print(choice);
                        wr.print("</option>");
                     }
                  }

                  wr.print("</select>");
               }
            }

            wr.print("</td></tr>");
         }

         wr.print("</tbody></table>");
         wr.print("<p id=\"status\" class=\"w3-center\"></p>");
         wr.print("</div>\n\n");
      }
   }

   private void generateEventCheckBoxesScript(PrintWriter wr) {
      if (!this.events.isEmpty()) {
         wr.print("window.checkBoxEvent = function checkBoxEvent(id, msg) {\n");
         wr.print("   const el = document.getElementById(id);\n");
         wr.print("   if (el.checked) {\n");
         wr.print("      alert(msg);\n");
         wr.print("      saveEvent(id, 'set', 'event');\n");
         wr.print("   } else {\n");
         wr.print("      saveEvent(id, 'clear', 'event');\n");
         wr.print("   }\n");
         wr.print("}\n");
         wr.print("window.checkBoxEventForDefault = function checkBoxEventForDefault(id, msg) {\n");
         wr.print("   const el = document.getElementById(id);\n");
         wr.print("   const el_input = document.getElementById(id+\"-input\");\n");
         wr.print("   if (el_input.value.trim().length == 0) {\n");
         wr.print("      alert(\"Event has no description\");\n");
         wr.print("      el.checked = false;\n");
         wr.print("      return;\n");
         wr.print("   }\n");
         wr.print("   if (el_input.value.includes(\"\\\"\") || el_input.value.includes(\"'\")) {\n");
         wr.print("      alert(\"Event may not include quotes\");\n");
         wr.print("      el.checked = false;\n");
         wr.print("      return;\n");
         wr.print("   }\n");
         wr.print("   if (el.checked) {\n");
         wr.print("      el_input.readOnly = true;\n");
         wr.print("      saveEvent(id, 'set', 'event');\n");
         wr.print("      alert(el_input.value+\" acknowledged.\\nPlease uncheck the box when complete.\");\n");
         wr.print("   } else {\n");
         wr.print("      el_input.readOnly = false;\n");
         wr.print("      saveEvent(id, 'clear', 'event');\n");
         wr.print("   }\n");
         wr.print("}\n");
         wr.print("window.checkBoxEventForSelect = function checkBoxEventForSelect(id, msg) {\n");
         wr.print("   const el = document.getElementById(id);\n");
         wr.print("   const el_input = document.getElementById(id+\"-select\");\n");
         wr.print("   if (el.checked) {\n");
         wr.print("      el_input.disabled = true;\n");
         wr.print("      saveEvent(id, 'set', 'event');\n");
         wr.print("      alert(msg);\n");
         wr.print("   } else {\n");
         wr.print("      el_input.disabled = false;\n");
         wr.print("      saveEvent(id, 'clear', 'event');\n");
         wr.print("   }\n");
         wr.print("}\n");
         int i = 0;

         for(Event evt : this.events) {
            if (evt.isChecked() && evt.type == WebcamServlet.EventType.select) {
               wr.print("const el");
               wr.print(i);
               wr.print(" = document.getElementById(\"");
               wr.print(evt.name);
               wr.print("-select\");\n");
               wr.print("el");
               wr.print(i);
               wr.print(".disabled = true;\n");
               ++i;
            }
         }

      }
   }

   public void configure(Properties properties) {
      super.configure(properties);
      this.maximumAttemptsAllowed = PropertyValue.getValue(this.invigilator, "webcam", "terminate_after_authorization_alerts", 4);
      if (this.maximumAttemptsAllowed <= 0) {
         this.maximumAttemptsAllowed = Integer.MAX_VALUE;
      }

      this.disablePiP = PropertyValue.getValue(this.invigilator, "webcam", "disable_pip", true);
      this.maxUploadErrors = PropertyValue.getValue(this.invigilator, "webcam", "max_upload_errors", 0);
      if (this.maxUploadErrors == 0) {
         this.maxUploadErrors = Integer.MAX_VALUE;
      } else if (this.maxUploadErrors >= 10 && this.maxUploadErrors <= 100) {
         Logger.log(Level.CONFIG, String.format("Webcam snapshot max upload errors set to %d", this.maxUploadErrors), "");
      } else {
         this.maxUploadErrors = 100;
         Logger.log(Level.WARNING, String.format("Webcam snapshot max upload errors set to default (%d)", this.maxUploadErrors), "");
      }

      if (properties.containsKey("webcam.snapshot_frequency")) {
         String frequencyAsString = properties.getProperty("webcam.snapshot_frequency");

         try {
            this.frequency = Integer.parseInt(frequencyAsString.trim()) * 1000;
            if (this.frequency < 0 || this.frequency > 60000) {
               throw new NumberFormatException("Webcam snapshot frequency out of range (0,60) seconds");
            }

            Logger.log(Level.CONFIG, String.format("Webcam snapshot frequency set to %s seconds", frequencyAsString), "");
         } catch (NumberFormatException var12) {
            this.frequency = 30000;
            Logger.log(Level.WARNING, String.format("Webcam snapshot frequency was not an acceptable number: %s, using %d seconds", frequencyAsString, this.frequency / 1000), "");
         }
      } else {
         this.frequency = 30000;
      }

      String value = PropertyValue.getValue(this.invigilator, "webcam.face", "recognition");
      if (value == null) {
         value = PropertyValue.getValue(this.invigilator, "webcam.face", "detection", "false");
      }

      this.includingFaceDetection = Utils.isTrueOrYes(value);
      if (this.includingFaceDetection) {
         Logger.log(Level.CONFIG, "Webcam face detection is enabled", "");
      }

      value = PropertyValue.getValue(this.invigilator, "webcam.gaze", "detection", "false");
      this.includingGazeDetection = Utils.isTrueOrYes(value);
      if (this.includingGazeDetection) {
         Logger.log(Level.CONFIG, "Webcam gaze detection is enabled", "");
      }

      value = PropertyValue.getValue(this.invigilator, "webcam.gesture", "detection", "false");
      this.includingGestureDetection = Utils.isTrueOrYes(value);
      if (this.includingGestureDetection) {
         Logger.log(Level.CONFIG, "Webcam gesture detection is enabled", "");
      }

      value = PropertyValue.getValue(this.invigilator, "webcam", "required", "true");
      this.includingWebcam = Utils.isTrueOrYes(value);
      if (this.includingWebcam) {
         Logger.log(Level.CONFIG, "Webcam is enabled", "");
      } else {
         this.includingFaceDetection = false;
      }

      if (!this.includingFaceDetection) {
         this.includingGestureDetection = false;
         this.includingGazeDetection = false;
      }

      value = PropertyValue.getValue(this.invigilator, "webcam", "close_automatically", "true");
      this.closeOnExitAllowed = Utils.isTrueOrYes(value);
      if (this.closeOnExitAllowed) {
         Logger.log(Level.CONFIG, "Webcam can close automatically on exit", "");
      }

      if (properties.containsKey("webcam.face.detection_frequency") && this.includingFaceDetection) {
         String frequencyAsString = properties.getProperty("webcam.face.detection_frequency");

         try {
            this.detectionFrequency = Integer.parseInt(frequencyAsString.trim()) * 1000;
            if (this.detectionFrequency < 0 || this.detectionFrequency > 60000) {
               throw new NumberFormatException("Face detection frequency out of range (0,60) seconds");
            }

            Logger.log(Level.CONFIG, String.format("Webcam face detection frequency set to %s seconds", frequencyAsString), "");
         } catch (NumberFormatException var11) {
            this.detectionFrequency = 30000;
            Logger.log(Level.WARNING, String.format("Webcam face detection frequency was not an acceptable number: %s, using %d seconds", frequencyAsString, this.detectionFrequency / 1000), "");
         }
      } else {
         this.detectionFrequency = 30000;
      }

      this.tableLabel = properties.getProperty("event.table.label", this.tableLabel).trim();
      Logger.log(Level.CONFIG, String.format("Event table label is \"%s\"", this.tableLabel), "");
      int i = 1;
      String eventString = properties.getProperty("event." + i);
      synchronized(this.events) {
         this.events.clear();

         for(; eventString != null; eventString = properties.getProperty("event." + i)) {
            ++i;
            String[] nameAndDescription = eventString.split(",");
            if (nameAndDescription != null && nameAndDescription.length > 1) {
               Event e;
               if (nameAndDescription.length == 2) {
                  e = new Event(nameAndDescription[0], nameAndDescription[1], WebcamServlet.EventType.named);
               } else {
                  e = new Event(nameAndDescription[0], nameAndDescription[1], nameAndDescription[2], WebcamServlet.EventType.named);
               }

               if (e.isValid() && !this.events.contains(e)) {
                  this.events.add(e);
               } else {
                  Logger.log(Level.WARNING, "Invalid event description: ", eventString);
               }
            } else {
               Logger.log(Level.WARNING, "Invalid event description: ", eventString);
            }
         }

         if (this.events.size() > 0) {
            Logger.log(Level.CONFIG, String.format("There were %d events defined", this.events.size()), "");
         }
      }

      eventString = properties.getProperty("event.default");
      if (eventString != null) {
         try {
            int numberOfDefaultEvents = Integer.parseInt(eventString.trim());
            if (numberOfDefaultEvents > 0 && numberOfDefaultEvents < 11) {
               for(int j = 0; j < numberOfDefaultEvents; ++j) {
                  this.events.add(new Event("default" + j, "", WebcamServlet.EventType.userDefined));
               }

               Logger.log(Level.CONFIG, String.format("There were %d default events defined", numberOfDefaultEvents), "");
            } else {
               Logger.log(Level.WARNING, "Invalid default event number description: ", "values must be >= 1 and <= 10");
            }
         } catch (NumberFormatException var9) {
            Logger.log(Level.WARNING, "Invalid default event number description: ", eventString);
         }
      }

      eventString = properties.getProperty("event.default.size");
      if (eventString != null) {
         try {
            this.defaultEventSize = Integer.parseInt(eventString.trim());
            if (this.defaultEventSize < 16 || this.defaultEventSize > 80) {
               throw new NumberFormatException("must be >= 16 and <= 80");
            }

            Logger.log(Level.CONFIG, String.format("The default event size was %d characters", this.defaultEventSize), "");
         } catch (NumberFormatException var8) {
            Logger.log(Level.WARNING, "Invalid default event size description: ", eventString);
            this.defaultEventSize = 32;
         }
      }

   }

   private static enum EventType {
      named,
      select,
      userDefined;
   }

   private static class Event {
      private final String name;
      private String description;
      private String value;
      private final EventType type;
      private final String alert;
      private boolean checked;

      public Event(String name, String description, EventType type) {
         this(name, description, description + " acknowledged.\\nPlease uncheck the box when complete.", type);
      }

      public Event(String name, String description, String alert, EventType type) {
         this.name = name.replace('"', ' ').replace('\'', ' ').trim();
         this.description = description.trim();
         String[] multiSelect = this.description.split(":");
         if (multiSelect != null && multiSelect.length > 1) {
            this.type = WebcamServlet.EventType.select;
            this.value = "";
         } else {
            this.type = type;
            this.value = this.description;
         }

         this.alert = alert.trim();
         this.checked = false;
      }

      public String getName() {
         return this.name;
      }

      public boolean isValid() {
         return this.name.length() > 0 && this.description.length() > 0 && this.alert.length() > 0;
      }

      public boolean isChecked() {
         return this.checked;
      }

      public void setChecked(boolean checked) {
         this.checked = checked;
      }

      public void setDescription(String description) {
         this.description = description;
      }

      public void setValue(String value) {
         this.value = value;
      }

      public boolean equals(Object event) {
         return event instanceof Event ? this.name.equals(((Event)event).getName()) : false;
      }
   }

   private class WebcamTimer extends TimerTask {
      public void run() {
         if (!WebcamServlet.this.distributionOfImageOk) {
            WebcamServlet.this.distributionOfImageOk = true;
            WebcamServlet.this.setupNewTimer();
         } else {
            ++WebcamServlet.this.countOfTimeouts;
            int timeouts = WebcamServlet.this.countOfTimeouts;
            if (timeouts > 2) {
               WebcamServlet.this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Webcam authorization required", WebcamServlet.this.invigilator.createProblemSetEvent("webcam_not_authorized"));
            }

            int type = timeouts > WebcamServlet.this.maximumAttemptsAllowed ? 0 : 2;
            DisappearingAlert da = new DisappearingAlert((long)ClientShared.DISAPPEARING_ALERT_TIMEOUT, 1, type);
            Runnable action;
            String msg;
            if (timeouts > WebcamServlet.this.maximumAttemptsAllowed) {
               msg = "You have failed to authorize webcam use.\n\nYour session will now end";
               WebcamServlet.this.invigilator.setInvigilatorState(InvigilatorState.ended);
               action = new SessionEndTask(WebcamServlet.this.invigilator, ProgressServlet.getSingleton(), "Session ended because " + WebcamServlet.this.invigilator.getNameAndID() + " failed to authorize webcam use");
            } else {
               String var10000 = WebcamServlet.this.tabTitle();
               msg = "No webcam image saved recently. Is browser webcam tab running?\nPlease go to the \"" + var10000 + "\" tab and authorize webcam use.\nIf the webcam tab is not running, press any Home button followed by the Webcam button.\nYou may also recreate the session page at " + WebcamServlet.this.invigilator.getServletProcessor().getService() + "\n\nYou have " + (WebcamServlet.this.maximumAttemptsAllowed - timeouts + 1) * 2 * WebcamServlet.this.frequency / 1000 + " secs before your session ends";
               action = new Runnable() {
                  public void run() {
                     WebcamServlet.this.setupNewTimer();
                  }
               };
            }

            da.setRunOnCloseRegardless(action);
            da.show(msg, "CoMaS Webcam Alert(" + timeouts + ")!");
         }
      }
   }
}
