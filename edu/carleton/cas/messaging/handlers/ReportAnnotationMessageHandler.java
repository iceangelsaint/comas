package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;

public class ReportAnnotationMessageHandler extends BaseMessageHandler implements MessageHandler {
   public ReportAnnotationMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      String msg = message.getContentMessage();
      if (this.invigilator != null && msg != null && msg.length() > 0) {
         this.invigilator.getReportManager().annotateReport(msg.trim());
      }

   }
}
