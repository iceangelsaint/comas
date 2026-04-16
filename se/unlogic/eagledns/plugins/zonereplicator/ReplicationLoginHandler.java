package se.unlogic.eagledns.plugins.zonereplicator;

import java.rmi.RemoteException;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import org.apache.log4j.Logger;
import se.unlogic.standardutils.rmi.PasswordLogin;

public class ReplicationLoginHandler implements PasswordLogin {
   private Logger log = Logger.getLogger(this.getClass());
   private ReplicationServerPlugin server;
   private String password;

   public ReplicationLoginHandler(ReplicationServerPlugin server, String password) {
      this.server = server;
      this.password = password;
   }

   public ReplicationServerPlugin login(String password) throws RemoteException {
      if (password != null && password.equals(this.password)) {
         try {
            this.log.debug("Remote login from " + UnicastRemoteObject.getClientHost());
         } catch (ServerNotActiveException var3) {
         }

         return this.server;
      } else {
         try {
            this.log.warn("Failed login attempt from " + UnicastRemoteObject.getClientHost());
         } catch (ServerNotActiveException var4) {
         }

         return null;
      }
   }
}
