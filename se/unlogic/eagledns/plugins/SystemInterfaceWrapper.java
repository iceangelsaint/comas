package se.unlogic.eagledns.plugins;

import java.rmi.RemoteException;
import se.unlogic.eagledns.Status;
import se.unlogic.eagledns.SystemInterface;
import se.unlogic.eagledns.plugins.remotemanagement.EagleManager;

public class SystemInterfaceWrapper implements EagleManager {
   private SystemInterface systemInterface;

   public SystemInterfaceWrapper(SystemInterface systemInterface) {
      this.systemInterface = systemInterface;
   }

   public int getActiveUDPThreadCount() throws RemoteException {
      return this.systemInterface.getActiveUDPThreadCount();
   }

   public long getCompletedTCPQueryCount() throws RemoteException {
      return this.systemInterface.getCompletedTCPQueryCount();
   }

   public long getCompletedUDPQueryCount() throws RemoteException {
      return this.systemInterface.getCompletedUDPQueryCount();
   }

   public int getMaxActiveTCPThreadCount() throws RemoteException {
      return this.systemInterface.getMaxActiveTCPThreadCount();
   }

   public int getMaxActiveUDPThreadCount() throws RemoteException {
      return this.systemInterface.getMaxActiveUDPThreadCount();
   }

   public int getResolverCount() throws RemoteException {
      return this.systemInterface.getResolverCount();
   }

   public long getStartTime() throws RemoteException {
      return this.systemInterface.getStartTime();
   }

   public String getVersion() throws RemoteException {
      return this.systemInterface.getVersion();
   }

   public int primaryZoneCount() throws RemoteException {
      return this.systemInterface.primaryZoneCount();
   }

   public void reloadZones() throws RemoteException {
      this.systemInterface.reloadZones();
   }

   public int secondaryZoneCount() throws RemoteException {
      return this.systemInterface.secondaryZoneCount();
   }

   public void shutdown() throws RemoteException {
      (new Thread() {
         public void run() {
            SystemInterfaceWrapper.this.systemInterface.shutdown();
         }
      }).start();
   }

   public int getActiveTCPThreadCount() throws RemoteException {
      return this.systemInterface.getActiveUDPThreadCount();
   }

   public int getUDPThreadPoolMaxSize() throws RemoteException {
      return this.systemInterface.getUDPThreadPoolMaxSize();
   }

   public int getUDPThreadPoolMinSize() throws RemoteException {
      return this.systemInterface.getUDPThreadPoolMinSize();
   }

   public int getTCPThreadPoolMaxSize() throws RemoteException {
      return this.systemInterface.getTCPThreadPoolMaxSize();
   }

   public int getTCPThreadPoolMinSize() throws RemoteException {
      return this.systemInterface.getTCPThreadPoolMinSize();
   }

   public int getZoneProviderCount() throws RemoteException {
      return this.systemInterface.getZoneProviders().size();
   }

   public int getPluginCount() throws RemoteException {
      return this.systemInterface.getPlugins().size();
   }

   public long getRejectedUDPConnections() {
      return this.systemInterface.getRejectedUDPConnections();
   }

   public long getRejectedTCPConnections() {
      return this.systemInterface.getRejectedTCPConnections();
   }

   public Status getStatus() {
      return this.systemInterface.getStatus();
   }
}
