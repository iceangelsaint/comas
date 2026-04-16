package edu.carleton.cas.jetty.embedded.processors;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.file.Utils;
import javax.servlet.http.HttpServletRequest;

public abstract class ProcessorFactory {
   public static ProcessorInterface create(HttpServletRequest request, Invigilator invigilator) {
      String value = request.getParameter("QUESTION_FILE");
      if (value != null) {
         String category = invigilator.getExamQuestionProperties().getCategory(value, value);
         if (value.endsWith(".aiken") || category.endsWith(".aiken")) {
            return new MultipleChoiceQuestionProcessor(invigilator.getServletProcessor(), Utils.removeExtension(value));
         }

         if (value.endsWith(".htxt") || category.endsWith(".htxt")) {
            return new TextQuestionProcessor(invigilator.getServletProcessor());
         }
      }

      return new UnknownQuestionProcessor(invigilator.getServletProcessor());
   }
}
