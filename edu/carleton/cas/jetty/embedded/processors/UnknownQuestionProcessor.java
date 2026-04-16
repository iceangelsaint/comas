package edu.carleton.cas.jetty.embedded.processors;

import edu.carleton.cas.file.Utils;
import edu.carleton.cas.jetty.embedded.ServletProcessor;
import edu.carleton.cas.logging.Logger;
import java.io.IOException;
import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class UnknownQuestionProcessor implements ProcessorInterface {
   public UnknownQuestionProcessor(ServletProcessor servletProcessor) {
   }

   public void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
      response.setContentType("text/html");
      response.addHeader("Access-Control-Allow-Origin", "*");
      response.setStatus(406);
      String value = request.getParameter("QUESTION_FILE");
      if (value != null) {
         Logger.log(Level.SEVERE, "Unknown question type: ", value);
         response.getWriter().println(Utils.localHTMLPage("No question processor found for: " + value));
      } else {
         String msg = "No question type was provided, question ignored";
         Logger.log(Level.SEVERE, msg, "");
         response.getWriter().println(Utils.localHTMLPage(msg));
      }

   }
}
