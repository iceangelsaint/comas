package edu.carleton.cas.jetty.embedded.processors;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.file.Utils;
import java.io.File;
import java.util.Properties;

public class ExamQuestionProperties {
   private Properties properties;

   public ExamQuestionProperties(Invigilator invigilator) {
      String var10000 = ClientShared.getExamDirectory(invigilator.getCourse(), invigilator.getActivity());
      String questionIndexFile = var10000 + File.separator + "activity.idx";
      this.properties = Utils.getPropertiesFromFile(questionIndexFile);
      if (this.properties == null) {
         this.properties = new Properties();
      }

   }

   public String getProperty(String key) {
      return this.properties.getProperty(key);
   }

   public String getProperty(String key, String defaultValue) {
      return this.properties.getProperty(key, defaultValue);
   }

   public boolean contains(String key) {
      return this.properties.contains(key);
   }

   public boolean containsKey(String key) {
      return this.properties.containsKey(key);
   }

   public String getLabel(String key, String defaultValue) {
      return this.getValue(key, 0, defaultValue);
   }

   public String getCategory(String key, String defaultValue) {
      return this.getValue(key, 1, defaultValue);
   }

   public String getValue(String key, int index, String defaultValue) {
      String pty = this.getProperty(key);
      if (pty != null) {
         String[] values = pty.split(":");
         return values.length > index ? values[index].trim() : pty;
      } else {
         return defaultValue;
      }
   }
}
