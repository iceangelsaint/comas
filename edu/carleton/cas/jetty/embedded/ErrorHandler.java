package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.resources.SystemWebResources;
import java.io.IOException;
import java.io.Writer;
import javax.servlet.http.HttpServletRequest;

public class ErrorHandler extends org.eclipse.jetty.server.handler.ErrorHandler {
   String message;

   public ErrorHandler(String message) {
      this.message = message;
   }

   protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
      writer.append(String.format(this.message, SystemWebResources.getStylesheet(), SystemWebResources.getServerAppImage(), request.getRequestURI(), code));
   }

   protected void writeErrorPage​(HttpServletRequest request, Writer writer, int code, String message, boolean showStacks) throws IOException {
      writer.append(String.format(this.message, SystemWebResources.getStylesheet(), SystemWebResources.getServerAppImage(), request.getRequestURI(), code));
   }
}
