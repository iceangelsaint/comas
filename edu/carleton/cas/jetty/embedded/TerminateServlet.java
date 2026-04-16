package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.resources.SystemWebResources;
import edu.carleton.cas.ui.DisappearingAlert;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TerminateServlet extends HttpServlet {
   private final Invigilator invigilator;

   public TerminateServlet(Invigilator invigilator) {
      this.invigilator = invigilator;
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      PrintWriter pw = response.getWriter();
      pw.println(SystemWebResources.htmlPage("<h1>A CoMaS session has been terminated</h1><h1>Affected browser tab titles will be \"CoMaS Session Ended\"</h1><h1>Other tabs may display \"404\" errors</h1><h1>ACTION: Please close these browser tabs</h1>"));
      DisappearingAlert da = new DisappearingAlert((long)ClientShared.DISAPPEARING_ALERT_TIMEOUT, 1, 2);
      this.invigilator.setInvigilatorState(InvigilatorState.ending);
      Runnable action = new Runnable() {
         public void run() {
            System.exit(0);
         }
      };
      da.setRunOnCloseRegardless(action);
      da.show("A CoMaS session has been terminated.\nAffected browser tab titles will be \"CoMaS Session Ended\".\nOther tabs may display \"404\" errors.\n\nACTION: Please close these browser tabs", "CoMaS Configuration Alert");
   }
}
