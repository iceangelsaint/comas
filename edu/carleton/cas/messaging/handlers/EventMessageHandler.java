package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.background.LogArchiver;
import edu.carleton.cas.events.Event;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

public class EventMessageHandler extends BaseMessageHandler implements MessageHandler {
   public EventMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      String msg = message.getContentMessage();
      int index = msg.lastIndexOf(":");
      Invigilator login = this.invigilator;
      if (login != null && index != -1) {
         Event evt = new Event();
         evt.put("time", System.currentTimeMillis());
         String description = msg.substring(0, index);
         evt.put("description", description);
         String type = msg.substring(index + 1);
         evt.put("severity", type);
         evt.put("logged", 1);
         evt.put("args", new Object[]{"event"});
         login.getReportManager().notify(evt);
         SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss z");
         LogArchiver var10000 = login.logArchiver;
         Level var10001 = edu.carleton.cas.logging.Level.NOTED;
         String var10002 = type.toUpperCase();
         var10000.put(var10001, "[" + var10002 + "] " + description + " at " + sdf.format(new Date()));
      }

   }
}
