package se.unlogic.eagledns;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import org.apache.log4j.Logger;
import org.xbill.DNS.Message;
import se.unlogic.standardutils.net.SocketUtils;

public class TCPConnection implements Runnable {
   private static Logger log = Logger.getLogger(TCPConnection.class);
   private EagleDNS eagleDNS;
   private Socket socket;

   public TCPConnection(EagleDNS eagleDNS, Socket socket) {
      this.eagleDNS = eagleDNS;
      this.socket = socket;
   }

   public void run() {
      try {
         try {
            InputStream is = this.socket.getInputStream();
            DataInputStream dataIn = new DataInputStream(is);
            int inLength = dataIn.readUnsignedShort();
            byte[] in = new byte[inLength];
            dataIn.readFully(in);
            byte[] response = null;

            try {
               Message query = new Message(in);
               log.info("TCP query " + EagleDNS.toString(query.getQuestion()) + " from " + this.socket.getRemoteSocketAddress());
               response = this.eagleDNS.generateReply(query, in, in.length, this.socket, this.socket.getRemoteSocketAddress());
               if (response == null) {
                  return;
               }
            } catch (IOException var14) {
               response = this.eagleDNS.formerrMessage(in).toWire();
            }

            DataOutputStream dataOut = new DataOutputStream(this.socket.getOutputStream());
            dataOut.writeShort(response.length);
            dataOut.write(response);
         } catch (IOException e) {
            log.debug("Error sending TCP response to " + this.socket.getRemoteSocketAddress() + ":" + this.socket.getPort() + ", " + e);
         } catch (Throwable e) {
            log.warn("Error processing TCP connection from " + this.socket.getRemoteSocketAddress() + ":" + this.socket.getPort() + ", " + e);
         }

      } finally {
         SocketUtils.closeSocket(this.socket);
      }
   }
}
