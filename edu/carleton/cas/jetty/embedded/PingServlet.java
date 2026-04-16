package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class PingServlet extends HttpServlet {
   private final Invigilator invigilator;

   public PingServlet(Invigilator invigilator) {
      this.invigilator = invigilator;
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.isInEndingState()) {
         response.setStatus(404);
      } else {
         response.getWriter().println(ClientShared.WEBSOCKET_HOST + ":" + ClientShared.PORT);
      }

   }
}
