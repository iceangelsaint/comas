package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.jetty.embedded.processors.ProcessorFactory;
import edu.carleton.cas.jetty.embedded.processors.ProcessorInterface;
import edu.carleton.cas.jetty.embedded.processors.RendererFactory;
import edu.carleton.cas.jetty.embedded.processors.RendererInterface;
import edu.carleton.cas.resources.BrowserTabsFromServer;
import edu.carleton.cas.resources.SystemWebResources;
import java.io.File;
import java.io.IOException;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletHolder;

public class ExamQuestionServlet extends EmbeddedServlet {
   public ExamQuestionServlet(Invigilator invigilator) {
      super(invigilator);
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      ServletHolder sh = sp.addServlet(this, "/*");
      sh.getRegistration().setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir"), this.maxFileSize, this.maxRequestSize, this.fileSizeThreshold));
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      String name = request.getRequestURI();
      if (name.equals("/")) {
         if (this.invigilator.isInInvigilatorState(InvigilatorState.running)) {
            (new Thread(new Runnable() {
               public void run() {
                  MessageOfTheDayServlet motd = MessageOfTheDayServlet.getSingleton();
                  if (motd == null || motd.readyToStartSession()) {
                     BrowserTabsFromServer bt1 = new BrowserTabsFromServer(ExamQuestionServlet.this.invigilator);
                     bt1.start();
                  }

               }
            })).start();
            response.sendRedirect(SystemWebResources.getHomePage());
         } else {
            this.invigilator.getServletProcessor().getRouter().redirect(request, response);
         }

      } else if (name.equals("/questions")) {
         response.sendRedirect("/" + this.invigilator.getSessionContext());
      } else if (!this.invigilator.isInInvigilatorState(InvigilatorState.running)) {
         response.sendError(404);
      } else {
         String resource;
         if (name.startsWith("/")) {
            String var10000 = ClientShared.getBaseDirectory(this.invigilator.getCourse(), this.invigilator.getActivity());
            resource = var10000 + name.substring(1);
            this.invigilator.setLastServlet(name.substring(1));
         } else {
            String var7 = ClientShared.getBaseDirectory(this.invigilator.getCourse(), this.invigilator.getActivity());
            resource = var7 + name;
            this.invigilator.setLastServlet(name);
         }

         RendererInterface r = RendererFactory.create(new File(resource), this.invigilator);
         String mimeType = r.getMimeType();
         r.render(response);
         response.addHeader("Access-Control-Allow-Origin", "*");
         response.setContentType(mimeType);
         response.setStatus(200);
      }
   }

   protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      if (!this.invigilator.isInInvigilatorState(InvigilatorState.running)) {
         response.sendError(404);
      } else {
         this.invigilator.getServletProcessor().updateLastAccessTime();
         ProcessorInterface pi = ProcessorFactory.create(request, this.invigilator);
         pi.process(request, response);
      }
   }
}
