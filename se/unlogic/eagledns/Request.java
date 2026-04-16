package se.unlogic.eagledns;

import java.net.Socket;
import java.net.SocketAddress;
import org.xbill.DNS.Message;

public interface Request {
   Message getQuery();

   byte[] getRawQuery();

   int getRawQueryLength();

   Socket getSocket();

   SocketAddress getSocketAddress();
}
