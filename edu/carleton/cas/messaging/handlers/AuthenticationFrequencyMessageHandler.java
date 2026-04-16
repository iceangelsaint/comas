package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import java.util.logging.Level;

public class AuthenticationFrequencyMessageHandler extends BaseMessageHandler implements MessageHandler {
   public AuthenticationFrequencyMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      String limitsMessage = message.getContentMessage();
      if (limitsMessage != null) {
         limitsMessage = limitsMessage.trim();
         if (limitsMessage.length() != 0) {
            int savedValue = ClientShared.MIN_AUTHENTICATION_INTERVAL;

            try {
               int noOfSeconds = Integer.parseInt(limitsMessage);
               if (noOfSeconds >= ClientShared.ABSOLUTE_MIN_INTERVAL) {
                  ClientShared.MIN_AUTHENTICATION_INTERVAL = noOfSeconds;
                  Logger.log(Level.CONFIG, message.getFrom() + " changed authentication frequency set to ", ClientShared.MIN_AUTHENTICATION_INTERVAL);
               } else {
                  Logger.log(Level.WARNING, message.getFrom() + " could not change authentication frequency to ", limitsMessage);
               }
            } catch (Exception e) {
               ClientShared.MIN_AUTHENTICATION_INTERVAL = savedValue;
               Logger.log(Level.WARNING, message.getFrom() + " tried to change authentication frequency. There was a parsing exception: ", e);
            }

         }
      }
   }
}
