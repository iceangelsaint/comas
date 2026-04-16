package edu.carleton.cas.jetty.embedded.processors;

import edu.carleton.cas.jetty.embedded.ServletProcessor;
import java.io.File;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

public abstract class AbstractRenderer implements RendererInterface {
   protected File file;
   protected ServletProcessor servletProcessor;

   public AbstractRenderer(File file, ServletProcessor servletProcessor) {
      this.file = file;
      this.servletProcessor = servletProcessor;
   }

   public String getMimeType() {
      return "application/octet-stream";
   }

   public void render(HttpServletResponse response) throws IOException {
   }
}
