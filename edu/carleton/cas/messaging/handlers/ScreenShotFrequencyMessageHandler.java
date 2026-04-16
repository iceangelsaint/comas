package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import java.util.logging.Level;

public class ScreenShotFrequencyMessageHandler extends BaseMessageHandler implements MessageHandler {
   public ScreenShotFrequencyMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      String limitsMessage = message.getContentMessage();
      if (limitsMessage != null) {
         limitsMessage = limitsMessage.trim();
         if (limitsMessage.length() != 0) {
            String[] limits = limitsMessage.split(",");
            if (limits != null && limits.length != 0) {
               int savedValue = ClientShared.MIN_INTERVAL;

               try {
                  int noOfSeconds = Integer.parseInt(limits[0].trim());
                  if (noOfSeconds >= ClientShared.ABSOLUTE_MIN_INTERVAL) {
                     ClientShared.MIN_INTERVAL = noOfSeconds;
                     Logger.log(Level.CONFIG, message.getFrom() + " changed lower screen shot frequency set to ", ClientShared.MIN_INTERVAL);
                  } else {
                     Logger.log(Level.WARNING, message.getFrom() + " could not change lower screen shot frequency to ", limitsMessage);
                  }
               } catch (Exception e) {
                  ClientShared.MIN_INTERVAL = savedValue;
                  Logger.log(Level.WARNING, message.getFrom() + " tried to change lower screen shot frequency. There was a parsing exception: ", e);
               }

               savedValue = ClientShared.MAX_INTERVAL;
               if (limits.length == 2) {
                  try {
                     int noOfSeconds = Integer.parseInt(limits[1].trim());
                     if (noOfSeconds >= ClientShared.MIN_INTERVAL) {
                        ClientShared.MAX_INTERVAL = noOfSeconds;
                        Logger.log(Level.CONFIG, message.getFrom() + " changed upper screen shot frequency set to ", ClientShared.MAX_INTERVAL);
                     } else {
                        Logger.log(Level.WARNING, message.getFrom() + " could not change upper screen shot frequency to ", limitsMessage);
                     }
                  } catch (Exception e) {
                     ClientShared.MAX_INTERVAL = savedValue;
                     Logger.log(Level.WARNING, message.getFrom() + " tried to change upper screen shot frequency. There was a parsing exception: ", e);
                  }
               }

               if (ClientShared.MAX_INTERVAL < ClientShared.MIN_INTERVAL) {
                  int temp = ClientShared.MAX_INTERVAL;
                  ClientShared.MAX_INTERVAL = ClientShared.MIN_INTERVAL;
                  ClientShared.MIN_INTERVAL = temp;
               }

            }
         }
      }
   }
}
