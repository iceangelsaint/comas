package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.utility.Password;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ThreadLocalRandom;
import javax.servlet.http.HttpServlet;

public abstract class EmbeddedServlet extends HttpServlet {
   private static final long serialVersionUID = 1L;
   protected long maxFileSize = 1048576L;
   protected long maxRequestSize = 1048576L;
   protected int fileSizeThreshold = 262144;
   protected final Invigilator invigilator;
   protected final String token;

   public EmbeddedServlet(Invigilator invigilator) {
      this.invigilator = invigilator;
      this.token = this.initToken();
   }

   private String initToken() {
      String _token;
      try {
         _token = Password.getPassCode(32);
      } catch (NoSuchAlgorithmException var3) {
         _token = Utils.shuffle("" + ThreadLocalRandom.current().nextInt(this.invigilator.hashCode()));
      }

      return _token;
   }
}
