package se.unlogic.eagledns;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.RejectedExecutionException;
import org.apache.log4j.Logger;

public class UDPSocketMonitor extends Thread {
   private Logger log = Logger.getLogger(this.getClass());
   private final EagleDNS eagleDNS;
   private final InetAddress addr;
   private final int port;
   private static final short UDP_LENGTH = 512;
   private final DatagramSocket socket;

   public UDPSocketMonitor(EagleDNS eagleDNS, InetAddress addr, int port) throws SocketException {
      this.eagleDNS = eagleDNS;
      this.addr = addr;
      this.port = port;
      this.socket = new DatagramSocket(port, addr);
      this.setDaemon(true);
      this.start();
   }

   public void run() {
      this.log.info("Starting UDP socket monitor on address " + this.getAddressAndPort());

      while(this.eagleDNS.getStatus() == Status.STARTING || this.eagleDNS.getStatus() == Status.STARTED) {
         DatagramPacket indp = null;

         try {
            byte[] in = new byte[512];
            indp = new DatagramPacket(in, in.length);
            indp.setLength(in.length);
            this.socket.receive(indp);
            this.log.debug("UDP connection from " + indp.getSocketAddress());
            if (this.eagleDNS.getStatus() == Status.STARTING || this.eagleDNS.getStatus() == Status.STARTED) {
               this.eagleDNS.getUdpThreadPool().execute(new UDPConnection(this.eagleDNS, this.socket, indp));
            }
         } catch (RejectedExecutionException var3) {
            if (this.eagleDNS.getStatus() == Status.STARTING || this.eagleDNS.getStatus() == Status.STARTED) {
               this.log.warn("UDP thread pool exausted, rejecting connection from " + indp.getSocketAddress());
               this.eagleDNS.incrementRejectedUDPConnections();
            }
         } catch (SocketException e) {
            this.log.debug("SocketException thrown from UDP socket on address " + this.getAddressAndPort() + ", " + e);
         } catch (IOException e) {
            this.log.info("IOException thrown by UDP socket on address " + this.getAddressAndPort() + ", " + e);
         } catch (Throwable t) {
            this.log.info("Throwable thrown by UDO socket on address " + this.getAddressAndPort(), t);
         }
      }

      this.log.info("UDP socket monitor on address " + this.getAddressAndPort() + " shutdown");
   }

   public void closeSocket() throws IOException {
      this.log.info("Closing TCP socket monitor on address " + this.getAddressAndPort() + "...");
      this.socket.close();
   }

   public String getAddressAndPort() {
      return this.addr.getHostAddress() + ":" + this.port;
   }
}
