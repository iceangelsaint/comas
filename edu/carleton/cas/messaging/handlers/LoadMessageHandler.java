package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import edu.carleton.cas.modules.foundation.JarClassLoader;
import edu.carleton.cas.modules.foundation.ModuleAction;
import edu.carleton.cas.modules.foundation.ModuleContainer;
import edu.carleton.cas.modules.foundation.ModuleManager;
import java.net.URL;
import java.util.logging.Level;

public class LoadMessageHandler extends BaseMessageHandler implements MessageHandler {
   public LoadMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      String msg = message.getContentMessage();

      try {
         String[] token = msg.split(" ");
         ModuleManager manager = this.invigilator.getModuleManager();
         if (token == null) {
            throw new NullPointerException("No tokens provided for load module message");
         }

         if (token.length < 3) {
            throw new RuntimeException("Insufficient number of tokens (< 3) provided for load module message");
         }

         String name = token[0].trim();
         String className = token[1].trim();
         String url = token[2].trim();
         JarClassLoader jcl = new JarClassLoader(new URL(url), manager.getLoader(), manager.getToken());
         ModuleContainer mc = manager.load(name, className, jcl);
         if (mc != null) {
            manager.execute(mc, ModuleAction.start);
            Logger.log(Level.INFO, String.format("%s started module %s using %s", message.getFrom(), mc.getName(), mc.getModule().getClass()), "");
         } else {
            Logger.log(Level.WARNING, String.format("%s tried to load a module called %s using %s. It could not be loaded", message.getFrom(), name, className), "");
         }
      } catch (Exception e) {
         Logger.log(Level.WARNING, message.getFrom() + " tried to load a module. A message exception occurred: ", e);
      }

   }
}
