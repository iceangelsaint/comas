package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.events.Event;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;

public class WebcamMessageHandler extends BaseMessageHandler implements MessageHandler {
   public WebcamMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      String msg = message.getContentMessage();
      if (msg != null && msg.length() != 0) {
         Event evt = new Event();
         evt.put("time", System.currentTimeMillis());
         evt.put("description", msg);
         evt.put("severity", "event");
         evt.put("logged", 1);
         if (msg.equals("started")) {
            evt.put("args", new Object[]{"session", "webcam:start", "problem", "webcam_error", "clear"});
         } else if (msg.equals("stopped")) {
            evt.put("args", new Object[]{"session", "webcam:stop"});
         } else if (msg.equals("error")) {
            evt.put("args", new Object[]{"session", "webcam:error", "problem", "webcam_error", "set"});
         } else if (msg.equals("close")) {
            evt.put("args", new Object[]{"session", "webcam:close"});
         } else if (msg.startsWith("pip")) {
            evt.put("args", new Object[]{"session", msg});
         } else if (msg.startsWith("faces")) {
            String number = msg.substring("faces:".length());
            String type;
            if (number.equals("0")) {
               type = "set";
            } else {
               type = "clear";
            }

            evt.put("args", new Object[]{"problem", "faces", type});
            this.invigilator.takeScreenShot(1000L * (long)ClientShared.MIN_INTERVAL);
         } else if (msg.startsWith("multiple_faces")) {
            String number = msg.substring("multiple_faces:".length());

            try {
               Integer numberOfFaces = Integer.parseInt(number);
               String type;
               if (numberOfFaces > 1) {
                  type = "set";
               } else {
                  type = "clear";
               }

               evt.put("args", new Object[]{"problem", "multiple_faces", type});
               this.invigilator.takeScreenShot(1000L * (long)ClientShared.MIN_INTERVAL);
            } catch (NumberFormatException var7) {
               return;
            }
         } else if (msg.startsWith("student-id-detected")) {
            this.invigilator.takeScreenShot(1000L * (long)ClientShared.MIN_INTERVAL);
            evt.put("args", new Object[]{"session", "student-id-detected"});
         } else if (msg.equals("snapshot")) {
            this.invigilator.takeScreenShot(1000L * (long)ClientShared.MIN_INTERVAL);
            evt = null;
         }

         if (evt != null) {
            this.invigilator.getReportManager().notify(evt);
         }

      }
   }
}
