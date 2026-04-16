package se.unlogic.eagledns.plugins.zonereplicator;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ServerNotActiveException;
import java.util.List;

public interface ReplicationServer extends Remote {
   ReplicationResponse replicate(List var1) throws ReplicationException, RemoteException, ServerNotActiveException;
}
