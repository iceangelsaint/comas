package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import java.io.PrintStream;

public class TestMessageHandler extends BaseMessageHandler implements MessageHandler {
   public TestMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      if (message != null) {
         PrintStream var10000 = System.out;
         String var10001 = message.getContentMessage();
         var10000.println("Test message received: " + var10001 + " from " + message.getFrom());
      } else {
         System.out.println("Null test message received");
      }

   }
}
