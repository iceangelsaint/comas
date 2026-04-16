package edu.carleton.cas.jetty.embedded.processors;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

public interface RendererInterface {
   void render(HttpServletResponse var1) throws IOException;

   String getMimeType();
}
