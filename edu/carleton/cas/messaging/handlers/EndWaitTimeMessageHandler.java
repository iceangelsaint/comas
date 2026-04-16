package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import java.util.logging.Level;

public class EndWaitTimeMessageHandler extends BaseMessageHandler implements MessageHandler {
   public EndWaitTimeMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      int savedValue = ClientShared.MAX_MSECS_TO_WAIT_TO_END;

      try {
         int noOfMilliSeconds = Integer.parseInt(message.getContentMessage().trim());
         if (noOfMilliSeconds > 0) {
            ClientShared.MAX_MSECS_TO_WAIT_TO_END = noOfMilliSeconds;
            Logger.log(Level.CONFIG, message.getFrom() + " set maximum time to wait to end to ", ClientShared.MAX_MSECS_TO_WAIT_TO_END + " msecs");
         } else {
            Logger.log(Level.WARNING, message.getFrom() + " tried to set maximum time to wait to end. It could not be changed to ", message.getContentMessage());
         }
      } catch (Exception e) {
         ClientShared.MAX_MSECS_TO_WAIT_TO_END = savedValue;
         Logger.log(Level.WARNING, message.getFrom() + " tried to set maximum time to wait to end. There was a parsing exception: ", e);
      }

   }
}
