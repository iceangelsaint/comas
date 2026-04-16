package se.unlogic.eagledns.plugins.remotemanagement;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface EagleLogin extends Remote {
   EagleManager login(String var1) throws RemoteException;
}
