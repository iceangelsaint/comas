package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RedirectServlet extends EmbeddedServlet {
   public RedirectServlet(Invigilator invigilator) {
      super(invigilator);
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      if (!sp.getRouter().redirect(request, response)) {
         if (!this.invigilator.isInInvigilatorState(InvigilatorState.initializing) && !this.invigilator.isInInvigilatorState(InvigilatorState.ending)) {
            response.sendRedirect(sp.getService(QuitServlet.getMapping()));
         } else {
            response.sendRedirect(sp.getService(ProgressServlet.getMapping()));
         }
      }

   }
}
