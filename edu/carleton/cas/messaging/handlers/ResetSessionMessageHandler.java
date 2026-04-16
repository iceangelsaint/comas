package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;

public class ResetSessionMessageHandler extends BaseMessageHandler implements MessageHandler {
   public ResetSessionMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      String msg = message.getContentMessage();
      this.invigilator.sendMessageToTools(true, "reset");
      if (msg != null && msg.length() > 0) {
         String var10000 = message.getFrom();
         msg = var10000 + " reset session: " + msg.trim();
      } else {
         msg = message.getFrom() + " reset session";
      }

      this.invigilator.logArchiver.put(Level.LOGGED, msg);
   }
}
