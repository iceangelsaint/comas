package se.unlogic.eagledns;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import org.apache.log4j.Logger;
import org.xbill.DNS.Message;

public class UDPConnection implements Runnable {
   private static final Logger log = Logger.getLogger(UDPConnection.class);
   private final EagleDNS eagleDNS;
   private final DatagramSocket socket;
   private final DatagramPacket inDataPacket;

   public UDPConnection(EagleDNS eagleDNS, DatagramSocket socket, DatagramPacket inDataPacket) {
      this.eagleDNS = eagleDNS;
      this.socket = socket;
      this.inDataPacket = inDataPacket;
   }

   public void run() {
      try {
         byte[] response = null;

         try {
            Message query = new Message(this.inDataPacket.getData());
            log.info("UDP query " + EagleDNS.toString(query.getQuestion()) + " from " + this.inDataPacket.getSocketAddress());
            response = this.eagleDNS.generateReply(query, this.inDataPacket.getData(), this.inDataPacket.getLength(), (Socket)null, this.inDataPacket.getSocketAddress());
            if (response == null) {
               return;
            }
         } catch (IOException var5) {
            response = this.eagleDNS.formerrMessage(this.inDataPacket.getData()).toWire();
         }

         DatagramPacket outdp = new DatagramPacket(response, response.length, this.inDataPacket.getAddress(), this.inDataPacket.getPort());
         outdp.setData(response);
         outdp.setLength(response.length);
         outdp.setAddress(this.inDataPacket.getAddress());
         outdp.setPort(this.inDataPacket.getPort());

         try {
            this.socket.send(outdp);
         } catch (IOException e) {
            log.debug("Error sending UDP response to " + this.inDataPacket.getAddress() + ", " + e);
         }
      } catch (Throwable e) {
         log.warn("Error processing UDP connection from " + this.inDataPacket.getSocketAddress() + ", " + e, e);
      }

   }
}
