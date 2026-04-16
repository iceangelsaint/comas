package edu.carleton.cas.background;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.utility.ClientHelper;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;

public class UploadArchiver extends Archiver {
   private String passkey;
   private long threshold;

   public UploadArchiver(Invigilator login, String target, String service, String passkey, String name, long threshold) {
      super(login, target, service, name);
      this.passkey = passkey;
      this.threshold = threshold;
   }

   public boolean doWork(Object obj) {
      String item;
      boolean annotated;
      if (obj instanceof AnnotatedObject) {
         item = ((AnnotatedObject)obj).object;
         annotated = ((AnnotatedObject)obj).annotated;
      } else {
         item = (String)obj;
         annotated = false;
      }

      return this.uploadArchive(new File(item), annotated, this.target, this.type);
   }

   public void doWorkOnFailure(Object obj) {
      if (this.status == 507 && ClientShared.BACKUP_SERVERS.nextIsOkToUse()) {
         String server = ClientShared.BACKUP_SERVERS.next(true);
         if (this.login.changeServer(server, "automatic due to insufficient storage")) {
            return;
         }

         ClientShared.BACKUP_SERVERS.reset(server);
      }

   }

   private boolean uploadArchive(File file, boolean annotated, String target, String service) {
      if (!this.login.isAllowedToUpload()) {
         return true;
      } else {
         long timeInMillisBefore = System.currentTimeMillis();
         Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
         client.register(MultiPartFeature.class);
         WebTarget webTarget = client.target(target).path(service);
         Invocation.Builder invocationBuilder = webTarget.request(new MediaType[]{MediaType.MULTIPART_FORM_DATA_TYPE});
         invocationBuilder.accept(new String[]{"application/json"});
         String token = this.login.getToken();
         if (token != null) {
            invocationBuilder.cookie("token", token);
         }

         FileDataBodyPart filePart = new FileDataBodyPart("file", file);
         FormDataMultiPart fdmp = new FormDataMultiPart();
         fdmp.field("passkey", this.passkey);
         fdmp.field("course", this.login.getCourse());
         fdmp.field("activity", this.login.getActivity());
         fdmp.field("name", this.login.getCanonicalStudentName());
         fdmp.field("annotated", annotated ? "true" : "false");
         MultiPart multipartEntity = fdmp.bodyPart(filePart);
         Response response = null;
         this.lock();

         boolean rtn;
         try {
            response = invocationBuilder.post(Entity.entity(fdmp, fdmp.getMediaType()));
            if (response.getStatus() == 401 && !Authenticator.isRunning()) {
               (new Thread(new Authenticator(this.login))).start();
            }

            this.status = response.getStatus();
            rtn = this.status < 204;
         } catch (Exception e) {
            this.statistics.setLastException(e);
            rtn = false;
         } finally {
            multipartEntity.cleanup();

            try {
               multipartEntity.close();
            } catch (IOException var32) {
            }

            fdmp.cleanup();

            try {
               fdmp.close();
            } catch (IOException var31) {
            }

            this.unlock();
         }

         long timeInMillisAfter = System.currentTimeMillis();
         int status;
         if (response == null) {
            status = 503;
         } else {
            status = response.getStatus();
         }

         long delta = timeInMillisAfter - timeInMillisBefore;
         if (rtn) {
            String warningText;
            if (delta > this.threshold) {
               warningText = " SLOW!";
            } else {
               warningText = "";
            }

            String msg = String.format("[%d] %s uploaded to: %s%s (%d msecs%s)", status, file.getName(), target, service, delta, warningText);
            Logger.log(Level.CONFIG, msg, "");
            this.login.finalizeOnArchive(file);
         } else {
            String msg = String.format("[%d] %s upload failed to: %s%s (%d msecs)", status, file.getName(), target, service, delta);
            Logger.log(edu.carleton.cas.logging.Level.WARNING, msg, "");
         }

         return rtn;
      }
   }
}
