package edu.carleton.cas.messaging.handlers;

import com.cogerent.utility.PropertyValue;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import edu.carleton.cas.ui.DisappearingAlert;
import java.awt.Desktop;
import java.net.URI;

public class ChatMessageHandler extends BaseMessageHandler implements MessageHandler {
   public ChatMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(final Message message) {
      Thread thread = new Thread(new Runnable() {
         public void run() {
            try {
               if (PropertyValue.getValue(ChatMessageHandler.this.invigilator, "session", "tool_pages_required", true)) {
                  Desktop.getDesktop().browse(new URI(ChatMessageHandler.this.invigilator.getServletProcessor().getService("pages/Chat.html")));
               } else if (PropertyValue.getValue(ChatMessageHandler.this.invigilator, "session", "tools_required", false)) {
                  Desktop.getDesktop().browse(new URI(ChatMessageHandler.this.invigilator.getServletProcessor().getService("tools/Chat.html")));
               }

               DisappearingAlert da = new DisappearingAlert();
               String var10001 = message.getFrom();
               da.show("An administrator (" + var10001 + ") has requested a chat session.\nPlease go to the browser tab with the title:\n\"CoMaS Chat: " + ChatMessageHandler.this.invigilator.getName() + " (" + ChatMessageHandler.this.invigilator.getID() + ")\"");
            } catch (Exception var2) {
               ChatMessageHandler.this.invigilator.logArchiver.put(Level.WARNING, "Could not start a chat session");
            }

         }
      });
      thread.start();
   }
}
