package edu.carleton.cas.websocket;

import edu.carleton.cas.logging.Level;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageDecoder;
import edu.carleton.cas.messaging.MessageEncoder;
import edu.carleton.cas.messaging.MessageHandler;
import edu.carleton.cas.resources.AbstractResourceMonitor;
import java.io.IOException;
import java.net.URI;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

@ClientEndpoint(
   decoders = {MessageDecoder.class},
   encoders = {MessageEncoder.class}
)
public class WebsocketClientEndpoint extends AbstractResourceMonitor {
   static long timeout = 0L;
   static int maxSize = 65536;
   static boolean ping = true;
   static boolean log = false;
   private Session userSession;
   private URI endpointURI;
   private final ConcurrentHashMap messageHandlers;
   private CloseReason closeReason;
   private Throwable errorReason;
   private WebSocketContainer container;

   public WebsocketClientEndpoint() {
      this((URI)null);
   }

   public WebsocketClientEndpoint(URI endpointURI) {
      super("websocket");
      this.userSession = null;
      this.endpointURI = endpointURI;
      this.closeReason = null;
      this.errorReason = null;
      this.messageHandlers = new ConcurrentHashMap();
      this.container = ContainerProvider.getWebSocketContainer();
      this.container.setDefaultMaxSessionIdleTimeout(timeout);
      this.container.setDefaultMaxTextMessageBufferSize(maxSize);
   }

   public void setURI(URI endpointURI) {
      this.endpointURI = endpointURI;
   }

   public URI getURI() {
      return this.endpointURI;
   }

   @OnOpen
   public void onOpen(Session userSession) throws IOException, EncodeException {
      this.userSession = userSession;
      this.closeReason = null;
      this.errorReason = null;
      this.notifyListeners(this.getResourceType(), "open");
   }

   @OnClose
   public void onClose(Session userSession, CloseReason reason) {
      if (userSession == this.userSession) {
         this.userSession = null;
      }

      this.closeReason = reason;
      String var10001 = this.getResourceType();
      String var10002 = String.valueOf(reason.getCloseCode());
      this.notifyListeners(var10001, "close:" + var10002 + ". " + reason.getReasonPhrase());
   }

   @OnError
   public void onError(Session userSession, Throwable reason) {
      if (!userSession.isOpen() && userSession == this.userSession) {
         this.userSession = null;
      }

      this.errorReason = reason;
      this.notifyListeners(this.getResourceType(), "error:" + String.valueOf(reason));
   }

   public CloseReason getCloseReason() {
      return this.closeReason;
   }

   public Throwable getErrorReason() {
      return this.errorReason;
   }

   @OnMessage
   public void onMessage(Message message) {
      MessageHandler handler = (MessageHandler)this.messageHandlers.get(message.getType());
      if (handler != null) {
         handler.handleMessage(message);
      } else {
         Logger.log(Level.WARNING, "No handler for: ", message.getType());
      }

   }

   public MessageHandler getMessageHandler(String type) {
      return (MessageHandler)this.messageHandlers.get(type);
   }

   public void addMessageHandler(String type, MessageHandler msgHandler) {
      this.messageHandlers.put(type, msgHandler);
   }

   public boolean removeMessageHandler(String type, MessageHandler msgHandler) {
      return this.messageHandlers.remove(type, msgHandler);
   }

   public void sendMessage(Message message) throws IOException, EncodeException {
      this.sendMessageAsync(message);
   }

   public void sendMessageSync(Message message) throws IOException, EncodeException {
      if (message != null && this.userSession != null) {
         this.userSession.getBasicRemote().sendObject(message);
      }

   }

   public void sendMessageAsync(Message message) throws IOException, EncodeException {
      if (message != null && this.userSession != null) {
         this.userSession.getAsyncRemote().sendObject(message);
      }

   }

   public void open() {
      if (!this.isOpen()) {
         try {
            this.userSession = this.container.connectToServer(this, this.endpointURI);
         } catch (IOException | IllegalStateException | DeploymentException e) {
            throw new RuntimeException(e);
         }
      }
   }

   public void close() {
      try {
         if (this.userSession != null) {
            this.userSession.close();
         }

      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   public boolean isOpen() {
      return this.userSession == null ? false : this.userSession.isOpen();
   }

   public boolean log() {
      return log;
   }

   public boolean ping() {
      return ping;
   }

   public static void configure(Properties properties) {
      String logAsString = properties.getProperty("websocket.log", "false");

      try {
         log = Boolean.parseBoolean(logAsString.trim());
         Logger.log(Level.FINE, "Websocket logging set to " + log, "");
      } catch (Exception var8) {
         log = false;
         Logger.log(Level.WARNING, "Illegal websocket log value, false used", "");
      }

      String timeoutAsString = properties.getProperty("websocket.timeout", "0");

      try {
         timeout = Long.parseLong(timeoutAsString.trim());
         Logger.log(Level.FINE, "Websocket timeout set to " + timeout + " seconds", "");
         timeout *= 1000L;
         if (timeout < 0L) {
            throw new NumberFormatException();
         }
      } catch (NumberFormatException var7) {
         timeout = 0L;
         Logger.log(Level.DIAGNOSTIC, "Illegal websocket timeout value provided, 0 used", "");
      }

      String maxsizeAsString = properties.getProperty("websocket.max_size", "65536");

      try {
         maxSize = Integer.parseInt(maxsizeAsString.trim());
         if (maxSize < 0 || maxSize > 65536) {
            throw new NumberFormatException();
         }

         Logger.log(Level.FINE, "Websocket maxsize set to " + maxSize + " bytes", "");
      } catch (NumberFormatException var9) {
         maxSize = 65536;
         Logger.log(Level.DIAGNOSTIC, "Illegal websocket maxsize value provided, 65536 bytes used", "");
      }

      String pingAsString = properties.getProperty("websocket.ping", "true");

      try {
         ping = Boolean.parseBoolean(pingAsString.trim());
         Logger.log(Level.FINE, "Websocket ping set to " + ping, "");
      } catch (Exception var6) {
         ping = true;
         Logger.log(Level.WARNING, "Illegal websocket ping value, true used", "");
      }

   }
}
