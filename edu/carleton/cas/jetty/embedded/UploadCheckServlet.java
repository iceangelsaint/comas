package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.security.Checksum;
import edu.carleton.cas.utility.ClientHelper;
import java.io.File;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;

public class UploadCheckServlet extends EmbeddedServlet {
   private static UploadCheckServlet singleton;
   private static final int INITIAL_VALUE = -1;
   private long size = -1L;
   private int status = 200;
   private long checkTime = 0L;
   private long lastModified = 0L;
   private String checkSum = "local";
   private String serverCheckSum = "remote";

   public UploadCheckServlet(Invigilator invigilator) {
      super(invigilator);
      if (singleton == null) {
         singleton = this;
      }

   }

   public static UploadCheckServlet getSingleton() {
      return singleton;
   }

   public static String getMapping() {
      return "check";
   }

   public long getSize() {
      return this.size;
   }

   public synchronized String getStatusString() {
      if (this.checkTime > this.lastModified) {
         if (this.status == 200) {
            return this.serverCheckSum.equals(this.checkSum) ? "✅" : "❌";
         } else {
            return "❗";
         }
      } else {
         return "";
      }
   }

   public long getLastModified() {
      return this.lastModified;
   }

   public synchronized String computeArchiveAttributes() throws Exception {
      File archiveFile = this.invigilator.archiveFile();
      this.size = archiveFile.length();
      this.lastModified = archiveFile.lastModified();
      this.checkSum = Checksum.getSHA256Checksum(archiveFile.getAbsolutePath());
      return this.checkSum;
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      sp.addServlet(this, "/" + getMapping());
      sp.getRouter().addRule("/" + getMapping(), InvigilatorState.running);
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         try {
            this.performUploadCheck();
         } catch (Exception var4) {
            this.status = 500;
         }

         response.sendRedirect(this.invigilator.getSessionContext());
      }
   }

   public synchronized void performUploadCheck() {
      Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
      WebTarget webTarget = client.target(ClientShared.BASE_UPLOAD).path("check");
      Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
      invocationBuilder.accept(new String[]{"text/plain"});
      Form form = new Form();
      form.param("course", this.invigilator.getCourse());
      form.param("activity", this.invigilator.getActivity());
      form.param("name", this.invigilator.getName());
      String token = this.invigilator.getToken();
      if (token != null) {
         invocationBuilder.cookie("token", token);
      }

      Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
      this.status = response.getStatus();
      if (this.status == 200) {
         this.serverCheckSum = (String)response.readEntity(String.class);
         this.checkTime = System.currentTimeMillis();
      } else {
         this.serverCheckSum = "";
      }

   }
}
