package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;

public class RestartSessionMessageHandler extends BaseMessageHandler implements MessageHandler {
   public RestartSessionMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      String msg = message.getContentMessage();
      this.invigilator.runExam();
      if (msg != null && msg.length() > 0) {
         String var10000 = message.getFrom();
         msg = var10000 + " restarted session: " + msg.trim();
      } else {
         msg = message.getFrom() + " restarted session";
      }

      this.invigilator.logArchiver.put(Level.LOGGED, msg);
   }
}
