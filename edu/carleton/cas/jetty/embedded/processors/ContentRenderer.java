package edu.carleton.cas.jetty.embedded.processors;

import edu.carleton.cas.file.Utils;
import edu.carleton.cas.jetty.embedded.ServletProcessor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

public class ContentRenderer extends AbstractRenderer implements RendererInterface {
   public ContentRenderer(File file, ServletProcessor servletProcessor) {
      super(file, servletProcessor);
   }

   public String getMimeType() {
      return this.file.getName().endsWith(".html") ? "text/html" : "application/octet-stream";
   }

   public void render(HttpServletResponse response) throws IOException {
      ServletOutputStream os = response.getOutputStream();
      FileInputStream fis = new FileInputStream(this.file);
      Utils.copyInputStream(fis, os);
   }
}
