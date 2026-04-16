package se.unlogic.eagledns;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.RejectedExecutionException;
import org.apache.log4j.Logger;
import se.unlogic.standardutils.net.SocketUtils;

public class TCPSocketMonitor extends Thread {
   private Logger log = Logger.getLogger(this.getClass());
   private final EagleDNS eagleDNS;
   private final InetAddress addr;
   private final int port;
   private final ServerSocket serverSocket;

   public TCPSocketMonitor(EagleDNS eagleDNS, InetAddress addr, int port) throws IOException {
      this.eagleDNS = eagleDNS;
      this.addr = addr;
      this.port = port;
      this.serverSocket = new ServerSocket(port, 128, addr);
      this.setDaemon(true);
      this.start();
   }

   public void run() {
      this.log.info("Starting TCP socket monitor on address " + this.getAddressAndPort());

      while(this.eagleDNS.getStatus() == Status.STARTING || this.eagleDNS.getStatus() == Status.STARTED) {
         Socket socket = null;

         try {
            socket = this.serverSocket.accept();
            this.log.debug("TCP connection from " + socket.getRemoteSocketAddress());
            if (this.eagleDNS.getStatus() == Status.STARTING || this.eagleDNS.getStatus() == Status.STARTED) {
               this.eagleDNS.getTcpThreadPool().execute(new TCPConnection(this.eagleDNS, socket));
            }
         } catch (RejectedExecutionException var3) {
            if (this.eagleDNS.getStatus() == Status.STARTING || this.eagleDNS.getStatus() == Status.STARTED) {
               this.log.warn("TCP thread pool exausted, rejecting connection from " + socket.getRemoteSocketAddress());
               this.eagleDNS.incrementRejectedTCPConnections();
            }

            SocketUtils.closeSocket(socket);
         } catch (SocketException e) {
            this.log.debug("SocketException thrown from TCP socket on address " + this.getAddressAndPort() + ", " + e);
            SocketUtils.closeSocket(socket);
         } catch (IOException e) {
            this.log.info("IOException thrown by TCP socket on address " + this.getAddressAndPort() + ", " + e);
            SocketUtils.closeSocket(socket);
         } catch (Throwable t) {
            this.log.info("Throwable thrown by TCP socket on address " + this.getAddressAndPort(), t);
            SocketUtils.closeSocket(socket);
         }
      }

      this.log.info("TCP socket monitor on address " + this.getAddressAndPort() + " shutdown");
   }

   public InetAddress getAddr() {
      return this.addr;
   }

   public int getPort() {
      return this.port;
   }

   public ServerSocket getServerSocket() {
      return this.serverSocket;
   }

   public void closeSocket() throws IOException {
      this.log.info("Closing TCP socket monitor on address " + this.getAddressAndPort() + "...");
      this.serverSocket.close();
   }

   public String getAddressAndPort() {
      return this.addr.getHostAddress() + ":" + this.port;
   }
}
