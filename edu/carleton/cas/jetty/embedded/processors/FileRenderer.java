package edu.carleton.cas.jetty.embedded.processors;

import edu.carleton.cas.background.LogArchiver;
import edu.carleton.cas.jetty.embedded.ServletProcessor;
import edu.carleton.cas.resources.ApplicationRunner;
import java.io.File;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

public class FileRenderer extends AbstractRenderer implements RendererInterface {
   private static final boolean USE_LOGARCHIVER = false;

   public FileRenderer(File file, ServletProcessor servletProcessor) {
      super(file, servletProcessor);
      String name = file.getName();
      name = name.replace("%20", " ");
      this.file = new File(file.getParentFile(), name);
   }

   public void render(HttpServletResponse response) throws IOException {
      response.sendRedirect(this.servletProcessor.getService(this.servletProcessor.getMapping()));
      ApplicationRunner fe = new ApplicationRunner(this.file, (LogArchiver)null, true);
      fe.start();
   }
}
