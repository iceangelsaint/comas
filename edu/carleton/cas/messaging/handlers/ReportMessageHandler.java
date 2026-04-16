package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;

public class ReportMessageHandler extends BaseMessageHandler implements MessageHandler {
   public ReportMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      String reportRequired = message.getContentMessage();
      if (reportRequired != null) {
         reportRequired = reportRequired.trim();
      }

      if (reportRequired != null && reportRequired.length() != 0 && !reportRequired.equalsIgnoreCase("Activity")) {
         this.invigilator.getReportManager().generateReport(reportRequired, this.invigilator.highPriorityLogArchiver, "Requested by " + message.getFrom());
      } else {
         this.invigilator.getReportManager().instantReport((Object[])null, "Requested by " + message.getFrom());
      }
   }
}
