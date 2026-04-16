package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;

public abstract class BaseMessageHandler implements MessageHandler {
   protected final Invigilator invigilator;

   BaseMessageHandler(Invigilator invigilator) {
      this.invigilator = invigilator;
   }

   public void handleMessage(Message arg0) {
   }
}
