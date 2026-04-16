package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import edu.carleton.cas.modules.foundation.ModuleManager;
import java.net.URL;
import java.util.logging.Level;

public class URLMessageHandler extends BaseMessageHandler implements MessageHandler {
   public URLMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      String msg = message.getContentMessage();

      try {
         String[] token = msg.split(" ");
         ModuleManager manager = this.invigilator.getModuleManager();
         if (token.length < 1) {
            throw new RuntimeException("Insufficient number of tokens (< 1) provided for URL message");
         }

         String urlProp = token[0].trim();
         if (urlProp.startsWith("/")) {
            urlProp = ClientShared.service(ClientShared.PROTOCOL, ClientShared.CMS_HOST, ClientShared.PORT, urlProp);
         }

         manager.addURL(new URL(urlProp));
         Logger.log(Level.INFO, message.getFrom() + " added a new module loading URL: ", urlProp);
      } catch (Exception e) {
         Logger.log(Level.WARNING, message.getFrom() + " provided a incorrectly formed URL message. The following exception occurred: ", e);
      }

   }
}
