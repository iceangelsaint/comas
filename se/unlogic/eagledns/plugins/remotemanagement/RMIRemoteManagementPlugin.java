package se.unlogic.eagledns.plugins.remotemanagement;

import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import se.unlogic.eagledns.plugins.BasePlugin;
import se.unlogic.eagledns.plugins.SystemInterfaceWrapper;
import se.unlogic.standardutils.numbers.NumberUtils;

public class RMIRemoteManagementPlugin extends BasePlugin {
   private String password;
   private Integer port;
   private LoginHandler loginHandler;

   public void init(String name) throws Exception {
      super.init(name);
      if (this.password != null && this.port != null) {
         this.log.info("Plugin " + this.name + " starting RMI remote management interface on port " + this.port);
         EagleManager eagleManager = new SystemInterfaceWrapper(this.systemInterface);
         this.loginHandler = new LoginHandler(eagleManager, this.password);

         try {
            EagleLogin eagleLogin = (EagleLogin)UnicastRemoteObject.exportObject(this.loginHandler, this.port);
            UnicastRemoteObject.exportObject(eagleManager, this.port);
            Registry registry = LocateRegistry.createRegistry(this.port);
            registry.bind("eagleLogin", eagleLogin);
         } catch (AccessException e) {
            throw e;
         } catch (RemoteException e) {
            throw e;
         } catch (AlreadyBoundException e) {
            throw e;
         }
      } else {
         throw new RuntimeException("Remote managed port and/or password not set, unable to start RMI remote managent plugin.");
      }
   }

   public void setPassword(String remotePassword) {
      this.password = remotePassword;
   }

   public void setPort(String remotePort) {
      this.port = NumberUtils.toInt(remotePort);
   }

   public void setRmiServerHostname(String serverHost) {
      System.getProperties().put("java.rmi.server.hostname", serverHost);
   }
}
