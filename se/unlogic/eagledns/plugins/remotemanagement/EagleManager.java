package se.unlogic.eagledns.plugins.remotemanagement;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface EagleManager extends Remote {
   void shutdown() throws RemoteException;

   void reloadZones() throws RemoteException;

   String getVersion() throws RemoteException;

   long getStartTime() throws RemoteException;

   int getZoneProviderCount() throws RemoteException;

   int getPluginCount() throws RemoteException;

   int getResolverCount() throws RemoteException;

   int secondaryZoneCount() throws RemoteException;

   int primaryZoneCount() throws RemoteException;

   int getMaxActiveUDPThreadCount() throws RemoteException;

   long getCompletedUDPQueryCount() throws RemoteException;

   int getActiveUDPThreadCount() throws RemoteException;

   int getMaxActiveTCPThreadCount() throws RemoteException;

   long getCompletedTCPQueryCount() throws RemoteException;

   int getActiveTCPThreadCount() throws RemoteException;

   int getUDPThreadPoolMaxSize() throws RemoteException;

   int getUDPThreadPoolMinSize() throws RemoteException;

   int getTCPThreadPoolMaxSize() throws RemoteException;

   int getTCPThreadPoolMinSize() throws RemoteException;

   long getRejectedUDPConnections() throws RemoteException;

   long getRejectedTCPConnections() throws RemoteException;
}
