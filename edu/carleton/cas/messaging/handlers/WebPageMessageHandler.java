package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import edu.carleton.cas.ui.DisappearingAlert;
import java.awt.Desktop;
import java.net.URI;

public class WebPageMessageHandler extends BaseMessageHandler implements MessageHandler {
   public WebPageMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(final Message message) {
      Thread thread = new Thread(new Runnable() {
         public void run() {
            try {
               String cmd = message.getContentMessage().trim();
               if (!cmd.startsWith("http")) {
                  cmd = WebPageMessageHandler.this.invigilator.getServletProcessor().getService(cmd);
               }

               Desktop.getDesktop().browse(new URI(cmd));
               DisappearingAlert da = new DisappearingAlert();
               da.show("An administrator (" + message.getFrom() + ") has displayed a web page for you");
            } catch (Exception var3) {
               WebPageMessageHandler.this.invigilator.logArchiver.put(Level.WARNING, "Could not start a web page " + message.getContentMessage());
            }

         }
      });
      thread.start();
   }
}
