package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import java.util.logging.Level;

public class LogMessageHandler extends BaseMessageHandler implements MessageHandler {
   public LogMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      try {
         String levelString = message.getContentMessage().trim().toUpperCase();
         Logger.setLevel(levelString);
         Logger.log(Level.INFO, message.getFrom() + "set the Log level: ", levelString);
      } catch (Exception e) {
         Logger.log(Level.WARNING, message.getFrom() + " tried to set the Log level. There was a message exception: ", e);
      }

   }
}
