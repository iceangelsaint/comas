package se.unlogic.eagledns;

import java.net.Socket;
import java.net.SocketAddress;
import org.xbill.DNS.Message;

public class DefaultRequest implements Request {
   private SocketAddress socketAddress;
   private Message query;
   private byte[] rawQuery;
   private int rawQueryLength;
   private Socket socket;

   public DefaultRequest(SocketAddress socketAddress, Message query, byte[] rawQuery, int rawQueryLength, Socket socket) {
      this.socketAddress = socketAddress;
      this.query = query;
      this.rawQuery = rawQuery;
      this.rawQueryLength = rawQueryLength;
      this.socket = socket;
   }

   public SocketAddress getSocketAddress() {
      return this.socketAddress;
   }

   public Message getQuery() {
      return this.query;
   }

   public byte[] getRawQuery() {
      return this.rawQuery;
   }

   public int getRawQueryLength() {
      return this.rawQueryLength;
   }

   public Socket getSocket() {
      return this.socket;
   }
}
