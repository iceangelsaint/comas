package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.background.LogArchiver;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import java.net.InetAddress;
import java.util.logging.Level;

public class ServerMessageHandler extends BaseMessageHandler implements MessageHandler {
   public ServerMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      String currentServer = ClientShared.DIRECTORY_HOST;
      String server = message.getContentMessage().trim();
      if (server != null && server.length() > 0) {
         try {
            String[] tokens = server.split(":");
            if (tokens.length > 0) {
               InetAddress.getByName(tokens[0]);
            }

            this.invigilator.changeServer(server, "manual by " + message.getFrom());
         } catch (Exception var5) {
            LogArchiver var10000 = this.invigilator.logArchiver;
            Level var10001 = edu.carleton.cas.logging.Level.LOGGED;
            String var10002 = message.getFrom();
            var10000.put(var10001, var10002 + " tried to access an unknown CoMaS server: " + server + ". Server unchanged");
            this.invigilator.changeServer(currentServer, "manual by " + message.getFrom());
         }
      } else {
         this.invigilator.logArchiver.put(edu.carleton.cas.logging.Level.LOGGED, message.getFrom() + " failed to provide a CoMaS server name. Server unchanged");
      }

   }
}
