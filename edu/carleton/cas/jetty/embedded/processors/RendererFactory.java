package edu.carleton.cas.jetty.embedded.processors;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.resources.AbstractFileTask;
import java.io.File;

public abstract class RendererFactory {
   public static RendererInterface create(File type, Invigilator invigilator) {
      String name = type.getName();
      String category = invigilator.getExamQuestionProperties().getCategory(name, name);
      if (category.endsWith(".htxt") && name.endsWith(".htxt")) {
         return new TextRenderer(type, invigilator.getServletProcessor());
      } else if (!category.endsWith(".aiken") && !name.endsWith(".html") && !name.endsWith(".htm")) {
         for(String fileType : AbstractFileTask.getFileTypes()) {
            if (name.endsWith(fileType)) {
               return new FileRenderer(type, invigilator.getServletProcessor());
            }
         }

         return new ContentRenderer(type, invigilator.getServletProcessor());
      } else {
         return new ContentRenderer(type, invigilator.getServletProcessor());
      }
   }
}
