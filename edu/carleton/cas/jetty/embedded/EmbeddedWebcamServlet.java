package edu.carleton.cas.jetty.embedded;

import com.cogerent.utility.PropertyValue;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.events.Event;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.reporting.ReportManager;
import edu.carleton.cas.utility.ClientHelper;
import edu.carleton.cas.utility.Displays;
import edu.carleton.cas.utility.Sleeper;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;

public abstract class EmbeddedWebcamServlet extends EmbeddedServlet {
   private static final long serialVersionUID = 1L;
   private static final double DEFAULT_DETECTION_THRESHOLD = (double)0.5F;
   public static final int MAXIMUM_SERVER_RETRIES = 5;
   public static final int SERVER_RETRY_SLEEP = 1000;
   public static final int SERVER_RANDOM_RETRY_SLEEP = 1000;
   public static final boolean DEFAULT_FOR_RELIABLE_EVENT_DISTRIBUTION = true;
   public static final boolean DEFAULT_FOR_RELIABLE_IMAGE_DISTRIBUTION = false;
   protected boolean includingIDRecognition = false;
   protected double detectionThreshold = (double)0.5F;
   private boolean check_prefix = true;
   protected String webcam_canvas_size = "width=\"300\" height=\"150\"";
   protected boolean lastImageStateOk = true;
   protected boolean distributionOfImageOk = true;
   protected String nameWithDashes;
   protected boolean reliableStateDistribution;
   protected boolean reliableImageDistribution;
   protected byte[] lastImage = null;
   protected int sameCount = 0;
   protected int sameCountThreshold = 0;

   public EmbeddedWebcamServlet(Invigilator invigilator) {
      super(invigilator);
      String var10001 = invigilator.getName().replace(" ", "-");
      this.nameWithDashes = var10001 + "-" + invigilator.getID();
   }

   protected void saveImage(String prefixForFileAndServiceName, HttpServletRequest request) throws IOException {
      byte[] buffer = new byte[16384];
      InputStream input = new BufferedInputStream(request.getInputStream());
      ByteArrayOutputStream output = new ByteArrayOutputStream(16384);
      File webcamFile = this.getImageFile(prefixForFileAndServiceName);
      webcamFile.delete();
      Base64.Decoder decoder = Base64.getDecoder();
      boolean comma = false;
      StringBuffer prefix = new StringBuffer();

      int bytesRead;
      while((bytesRead = input.read(buffer)) != -1) {
         if (!comma) {
            int index = 0;

            for(int i = 0; i < bytesRead; ++i) {
               prefix.append((char)buffer[i]);
               if (buffer[i] == 44) {
                  index = i;
                  comma = true;
                  break;
               }
            }

            output.write(buffer, index + 1, bytesRead - index - 1);
         } else {
            output.write(buffer, 0, bytesRead);
         }
      }

      if (this.check_prefix && !prefix.toString().equals("data:image/png;base64,")) {
         Logger.log(Level.INFO, "Webcam image corruption, data URL not found: ", prefix.toString());
      }

      FileOutputStream fos = null;

      try {
         fos = new FileOutputStream(webcamFile);
         byte[] image = output.toByteArray();
         fos.write(decoder.decode(image));
         this.sendImageToServer(prefixForFileAndServiceName, "data:image/png;base64,".getBytes(), image);
         if (this.sameCountThreshold > 0) {
            if (this.isSameAsLastImage(image)) {
               ++this.sameCount;
            } else {
               this.sameCount = 0;
            }

            if (this.sameCount > this.sameCountThreshold) {
               if (!this.invigilator.getReportManager().hasProblemWithSetStatus("webcam_image_content")) {
                  this.invigilator.logArchiver.put(Level.NOTED, "The webcam image appears to be frozen", this.invigilator.createProblemSetEvent("webcam_image_content"));
               }
            } else if (this.invigilator.getReportManager().hasProblemWithSetStatus("webcam_image_content")) {
               this.invigilator.logArchiver.put(Level.NOTED, "The webcam image is now okay", this.invigilator.createProblemClearEvent("webcam_image_content"));
            }

            this.lastImage = image;
         }
      } finally {
         if (fos != null) {
            try {
               fos.close();
            } catch (IOException var24) {
            }
         }

         try {
            output.close();
         } catch (IOException var23) {
         }

         try {
            input.close();
         } catch (IOException var22) {
         }

      }

   }

   protected boolean isSameAsLastImage(byte[] image) {
      if (image != null && this.lastImage != null && image.length == this.lastImage.length) {
         for(int i = 0; i < image.length; ++i) {
            if (image[i] != this.lastImage[i]) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   protected File getImageFile(String prefix) {
      return new File(ClientShared.getScreensDirectory(this.invigilator.getCourse(), this.invigilator.getActivity()), prefix + "-" + this.invigilator.getName() + ".png");
   }

   protected synchronized BufferedImage getImage(File f) {
      if (f == null) {
         return null;
      } else {
         try {
            FileInputStream fis = null;

            BufferedImage var4;
            try {
               fis = new FileInputStream(f);
               var4 = ImageIO.read(fis);
            } finally {
               if (fis != null) {
                  try {
                     fis.close();
                  } catch (IOException var10) {
                  }
               }

            }

            return var4;
         } catch (IOException var12) {
            return null;
         }
      }
   }

   protected void sendImageToServer(String service, byte[] prefix, byte[] imageBase64) {
      if (!this.invigilator.isInEndingState()) {
         Response response = null;
         Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
         WebTarget webTarget = client.target(ClientShared.BASE_VIDEO).path(service).path(this.invigilator.getCourse()).path(this.invigilator.getActivity()).path(this.invigilator.getName()).path(this.invigilator.getStudentPassword());
         Invocation.Builder invocationBuilder = webTarget.request(new String[]{"image/png"});
         invocationBuilder.accept(new String[]{"text/html"});
         ByteBuffer buffer = ByteBuffer.wrap(new byte[prefix.length + imageBase64.length]);
         buffer.put(prefix);
         buffer.put(imageBase64);
         int tries = 0;

         do {
            ++tries;

            try {
               response = invocationBuilder.post(Entity.entity(buffer.array(), "image/png"));
            } catch (ProcessingException e) {
               Logger.output("Webcam Image Processing Exception: " + String.valueOf(e));
            }

            this.distributionOfImageOk = this.invigilator.isInEndingState() || response != null && response.getStatus() == 200;
            if (this.reliableImageDistribution && !this.distributionOfImageOk) {
               Sleeper.sleep(1000, 1000);
            }
         } while(this.reliableImageDistribution && !this.distributionOfImageOk && tries < 5);

      }
   }

   public boolean checkImage(String name) {
      BufferedImage image = this.getImage(this.getImageFile(name));
      if (image != null) {
         boolean isOk = Displays.isOk(image);
         this.processWebcamImageState(isOk);
         return isOk;
      } else {
         return false;
      }
   }

   protected void processWebcamImageState(boolean imageStateOk) {
      if (imageStateOk != this.lastImageStateOk) {
         if (imageStateOk) {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Webcam image is okay", this.invigilator.createProblemClearEvent("webcam_image_content"));
         } else {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, "Webcam image is not okay", this.invigilator.createProblemSetEvent("webcam_image_content"));
         }

         this.lastImageStateOk = imageStateOk;
      }
   }

   protected void processStatus(HttpServletRequest request, HttpServletResponse response) {
      String name = request.getParameter("name");
      String state = request.getParameter("state");
      if (name != null && state != null) {
         this.distributeMessage(name, state);
         Event evt = new Event();
         evt.put("time", System.currentTimeMillis());
         evt.put("description", state);
         evt.put("severity", "event");
         evt.put("logged", 1);
         if (state.equals("started")) {
            evt.put("args", new Object[]{"session", "webcam:start", "problem", "webcam_error", "clear"});
         } else if (state.equals("stopped")) {
            evt.put("args", new Object[]{"session", "webcam:stop"});
         } else if (state.equals("error")) {
            evt.put("args", new Object[]{"session", "webcam:error", "problem", "webcam_error", "set"});
         } else if (state.equals("close")) {
            evt.put("args", new Object[]{"session", "webcam:close"});
         } else if (state.startsWith("pip")) {
            evt.put("args", new Object[]{"session", state});
         } else if (state.startsWith("faces")) {
            String number = state.substring("faces:".length());
            String type;
            if (number.equals("0")) {
               type = "set";
            } else {
               type = "clear";
            }

            evt.put("args", new Object[]{"problem", "faces", type});
            this.invigilator.takeScreenShot(1000L * (long)ClientShared.MIN_INTERVAL);
         } else if (state.startsWith("multiple_faces")) {
            String number = state.substring("multiple_faces:".length());

            try {
               Integer numberOfFaces = Integer.parseInt(number);
               String type;
               if (numberOfFaces > 1) {
                  type = "set";
               } else {
                  type = "clear";
               }

               evt.put("args", new Object[]{"problem", "multiple_faces", type});
               this.invigilator.takeScreenShot(1000L * (long)ClientShared.MIN_INTERVAL);
            } catch (NumberFormatException var9) {
               return;
            }
         } else if (state.startsWith("student-id-detected")) {
            this.invigilator.takeScreenShot(1000L * (long)ClientShared.MIN_INTERVAL);
            evt.put("args", new Object[]{"session", "student-id-detected"});
         } else if (state.equals("snapshot")) {
            this.invigilator.takeScreenShot(1000L * (long)ClientShared.MIN_INTERVAL);
            return;
         }

         ReportManager reportManager = this.invigilator.getReportManager();
         if (reportManager != null) {
            reportManager.notify(evt);
         }

      }
   }

   private boolean distributeMessage(String service, String path, String name, String password, String state, String value) {
      try {
         Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
         WebTarget webTarget = client.target(service).path(path);
         Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
         invocationBuilder.accept(new String[]{"text/plain"});
         Form form = new Form();
         form.param("name", name);
         form.param("password", password);
         form.param("course", this.invigilator.getCourse());
         form.param("activity", this.invigilator.getActivity());
         form.param("state", state);
         form.param("value", value);
         String authToken = this.invigilator.getToken();
         if (authToken != null) {
            invocationBuilder.cookie("token", authToken);
            form.param("token", authToken);
         }

         Logger.log(Level.FINE, service + " " + path + " " + name + " " + password + " " + state + " " + value, "");
         Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
         String rtn = (String)response.readEntity(String.class);
         Logger.log(Level.FINE, "Response from " + service + "/" + path + " ", rtn);
         if (response.getStatus() == 200) {
            return true;
         }
      } catch (Exception e) {
         Logger.log(Level.WARNING, "Exception occurred during distribution of state for " + this.invigilator.getNameAndID() + ":", e);
      }

      return false;
   }

   protected boolean distributeMessage(String state, String value) {
      if (this.reliableStateDistribution) {
         this.invigilator.stateDistributor.put(state, value);
         return true;
      } else {
         int tries = 0;

         boolean distributionOk;
         do {
            ++tries;
            distributionOk = this.distributeMessage(ClientShared.BASE_LOGIN, "state", this.nameWithDashes, this.invigilator.getStudentPassword(), state, value);
            if (!distributionOk) {
               Sleeper.sleep(1000, 1000);
            }
         } while(!distributionOk && tries < 5);

         return distributionOk;
      }
   }

   public void configure(Properties properties) {
      String value = PropertyValue.getValue(this.invigilator, "webcam.id", "verification", "false");
      this.includingIDRecognition = Utils.isTrueOrYes(value);
      if (this.includingIDRecognition) {
         Logger.log(Level.CONFIG, "Webcam id verification is enabled", "");
      }

      this.check_prefix = Utils.isTrueOrYes(this.invigilator.getProperty("webcam.check_prefix", "true").trim());
      if (properties.containsKey("webcam.face.detection_threshold")) {
         String detectionThresholdString = properties.getProperty("webcam.face.detection_threshold");

         try {
            this.detectionThreshold = (double)Integer.parseInt(detectionThresholdString.trim()) / (double)100.0F;
            if (this.detectionThreshold < (double)0.0F || this.detectionThreshold > (double)1.0F) {
               throw new NumberFormatException("Not a percentage");
            }

            Logger.log(Level.CONFIG, String.format("Webcam face detection threshold set to %s%s", detectionThresholdString, "%"), "");
         } catch (NumberFormatException var5) {
            this.detectionThreshold = (double)0.5F;
            Logger.log(Level.WARNING, String.format("Webcam face detection threshold was not a percentage: %s, using %d%s", detectionThresholdString, Math.round(this.detectionThreshold * (double)100.0F), "%"), "");
         }
      } else {
         this.detectionThreshold = (double)0.5F;
      }

      this.reliableStateDistribution = Utils.getBooleanOrDefault(properties, "webcam.event.reliable_distribution", true);
      this.reliableImageDistribution = Utils.getBooleanOrDefault(properties, "webcam.image.reliable_distribution", false);
      this.webcam_canvas_size = properties.getProperty("webcam.canvas.size", "width=\"300\" height=\"150\"").trim();
      this.sameCountThreshold = Utils.getIntegerOrDefaultInRange(properties, "webcam.same_image_threshold", 0, 0, 100);
   }
}
