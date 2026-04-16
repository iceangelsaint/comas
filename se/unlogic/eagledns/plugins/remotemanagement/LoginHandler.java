package se.unlogic.eagledns.plugins.remotemanagement;

import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import org.apache.log4j.Logger;

public class LoginHandler implements EagleLogin {
   private Logger log = Logger.getLogger(this.getClass());
   private EagleManager eagleManager;
   private String password;

   public LoginHandler(EagleManager eagleManager, String password) {
      this.eagleManager = eagleManager;
      this.password = password;
   }

   public EagleManager login(String password) {
      if (password != null && password.equalsIgnoreCase(this.password)) {
         try {
            this.log.info("Remote login from " + UnicastRemoteObject.getClientHost());
         } catch (ServerNotActiveException var3) {
         }

         return this.eagleManager;
      } else {
         try {
            this.log.warn("Failed login attempt from " + UnicastRemoteObject.getClientHost());
         } catch (ServerNotActiveException var4) {
         }

         return null;
      }
   }
}
