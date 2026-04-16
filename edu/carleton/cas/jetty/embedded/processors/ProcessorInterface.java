package edu.carleton.cas.jetty.embedded.processors;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface ProcessorInterface {
   void process(HttpServletRequest var1, HttpServletResponse var2) throws IOException;
}
