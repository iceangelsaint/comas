package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.ui.WebAlert;
import java.awt.Desktop;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletHolder;

public class HostServlet extends EmbeddedServlet {
   private static HostServlet singleton;

   public HostServlet(Invigilator invigilator) {
      super(invigilator);
      if (singleton == null) {
         singleton = this;
      }

   }

   public static HostServlet getSingleton() {
      return singleton;
   }

   public static String getMapping() {
      return "host";
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      ServletHolder sh = sp.addServlet(this, "/" + getMapping());
      sh.getRegistration().setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir"), this.maxFileSize, this.maxRequestSize, this.fileSizeThreshold));
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      PrintWriter wr = response.getWriter();
      wr.println(this.invigilator.toStudentDirectoryContext());
   }

   protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      String url = request.getParameter("url");
      String _token = request.getParameter("token");
      if (url != null && _token != null) {
         if (this.token.equals(_token)) {
            try {
               Desktop.getDesktop().browse(new URI(this.invigilator.getServletProcessor().getService(url)));
            } catch (URISyntaxException | UnsupportedOperationException | IOException var6) {
               WebAlert.warning("Could not create session browser page for: " + url);
            }

         }
      }
   }
}
