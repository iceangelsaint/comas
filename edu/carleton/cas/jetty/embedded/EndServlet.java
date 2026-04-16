package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.resources.SystemWebResources;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EndServlet extends EmbeddedServlet {
   private static EndServlet singleton;

   public EndServlet(Invigilator invigilator) {
      super(invigilator);
      if (singleton == null) {
         singleton = this;
      }

   }

   public static String getMapping() {
      return "end";
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      String myMapping = "/" + getMapping();
      sp.addServlet(this, myMapping);
      ServletRouter sr = sp.getRouter();
      sr.addRule(myMapping, InvigilatorState.loggingIn);
      sr.addRule(myMapping, InvigilatorState.choosing);
      sr.addRule(myMapping, InvigilatorState.initializing);
      sr.addRule(myMapping, InvigilatorState.verifying);
      sr.addRule(myMapping, InvigilatorState.authorizing);
      sr.addRule(myMapping, InvigilatorState.running);
      sr.addRule(myMapping, InvigilatorState.ending);
      sr.addRule(myMapping, InvigilatorState.ended);
      sr.addRedirect(InvigilatorState.ending, myMapping);
   }

   public static EndServlet getSingleton() {
      return singleton;
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         this.invigilator.setInvigilatorState(InvigilatorState.ending);
         response.sendRedirect(SystemWebResources.getLocalResource("progressLandingPage", "/progress"));
      }

   }
}
