package edu.carleton.cas.jetty.embedded.processors;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.jetty.embedded.ServletProcessor;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TextQuestionProcessor implements ProcessorInterface {
   ServletProcessor servletProcessor;

   public TextQuestionProcessor(ServletProcessor servletProcessor) {
      this.servletProcessor = servletProcessor;
   }

   public void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
      int statusCode = 200;
      String mimeType = "text/html";
      String data = request.getParameter("answer");
      data = data.replaceAll("</textarea>", "");
      String fileName = request.getParameter("file");
      Invigilator invigilator = this.servletProcessor.getInvigilator();
      String resource = ClientShared.getExamDirectory(invigilator.getCourse(), invigilator.getActivity());
      File examAnswerFile = new File(resource, fileName);
      PrintWriter wr = null;

      try {
         wr = new PrintWriter(examAnswerFile);
         wr.print(data);
      } catch (IOException var15) {
         statusCode = 404;
         response.getWriter().print("Unable to save ");
         response.getWriter().print(fileName);
      } finally {
         if (wr != null) {
            wr.close();
         }

      }

      response.addHeader("Access-Control-Allow-Origin", "*");
      response.setContentType(mimeType);
      response.setStatus(statusCode);
   }
}
