package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;

public class ScreenShotMessageHandler extends BaseMessageHandler implements MessageHandler {
   public ScreenShotMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      this.invigilator.takeScreenShot();
   }
}
