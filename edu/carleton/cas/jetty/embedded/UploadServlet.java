package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.utility.Sleeper;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class UploadServlet extends EmbeddedServlet {
   private static UploadServlet singleton;
   private long lastUploadTime;

   public UploadServlet(Invigilator invigilator) {
      super(invigilator);
      if (singleton == null) {
         singleton = this;
      }

      this.lastUploadTime = 0L;
   }

   public static UploadServlet getSingleton() {
      return singleton;
   }

   public static String getMapping() {
      return "upload";
   }

   public long getLastUploadTime() {
      return this.lastUploadTime;
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      sp.addServlet(this, "/" + getMapping());
      sp.getRouter().addRule("/" + getMapping(), InvigilatorState.running);
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         if (ClientShared.AUTO_ARCHIVE) {
            long now = System.currentTimeMillis();
            if (now - this.lastUploadTime > (long)ClientShared.MIN_MSECS_BETWEEN_USER_UPLOADS) {
               boolean result = this.invigilator.createAndUploadArchive(true);
               if (result) {
                  this.lastUploadTime = now;
               }
            }

            for(int tries = 0; this.invigilator.examArchiver.backlog() > 0 && tries < 10; ++tries) {
               Sleeper.sleep(1000);
            }
         }

         response.sendRedirect(this.invigilator.getSessionContext());
      }
   }
}
