package edu.carleton.cas.websocket;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.jetty.embedded.ServletProcessor;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import edu.carleton.cas.messaging.handlers.AlertMessageHandler;
import edu.carleton.cas.messaging.handlers.AuthenticationFrequencyMessageHandler;
import edu.carleton.cas.messaging.handlers.ChatMessageHandler;
import edu.carleton.cas.messaging.handlers.EndWaitTimeMessageHandler;
import edu.carleton.cas.messaging.handlers.EventMessageHandler;
import edu.carleton.cas.messaging.handlers.LoadMessageHandler;
import edu.carleton.cas.messaging.handlers.LogMessageHandler;
import edu.carleton.cas.messaging.handlers.NullMessageHandler;
import edu.carleton.cas.messaging.handlers.PingMessageHandler;
import edu.carleton.cas.messaging.handlers.ProcessMessageHandler;
import edu.carleton.cas.messaging.handlers.ReportAnnotationMessageHandler;
import edu.carleton.cas.messaging.handlers.ReportMessageHandler;
import edu.carleton.cas.messaging.handlers.ResetSessionMessageHandler;
import edu.carleton.cas.messaging.handlers.RestartSessionMessageHandler;
import edu.carleton.cas.messaging.handlers.ScreenShotFrequencyMessageHandler;
import edu.carleton.cas.messaging.handlers.ScreenShotMessageHandler;
import edu.carleton.cas.messaging.handlers.ServerMessageHandler;
import edu.carleton.cas.messaging.handlers.StopMessageHandler;
import edu.carleton.cas.messaging.handlers.TestMessageHandler;
import edu.carleton.cas.messaging.handlers.URLMessageHandler;
import edu.carleton.cas.messaging.handlers.UnloadMessageHandler;
import edu.carleton.cas.messaging.handlers.WebPageMessageHandler;
import edu.carleton.cas.messaging.handlers.WebcamMessageHandler;
import edu.carleton.cas.modules.foundation.ModuleClassLoader;
import edu.carleton.cas.resources.Resource;
import edu.carleton.cas.resources.ResourceListener;
import edu.carleton.cas.resources.SystemWebResources;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.EncodeException;

public class WebsocketEndpointManager implements ResourceListener, WebsocketInterface {
   private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss z");
   private final Invigilator invigilator;
   private final WebsocketClientEndpoint endpoint;
   private ModuleClassLoader classLoader;

   public WebsocketEndpointManager(Invigilator invigilator) {
      this.invigilator = invigilator;
      this.endpoint = this.initializeWebsocketClientEndpoint();
      this.classLoader = null;
   }

   public void addServletHandler() {
      this.invigilator.getServletProcessor().addServlet(new AlertServlet(this.endpoint.getMessageHandler("alert")), "/alerts");
   }

   public void createCommandAndControlChannel() {
      if (!this.endpoint.isOpen()) {
         try {
            this.endpoint.setURI(new URI(this.getEndpointUri()));
            this.endpoint.open();
         } catch (Exception e) {
            Logger.log(Level.WARNING, "CCI: ", e.getMessage());
         }
      }

   }

   public void configure() {
      this.configureExtendedProtocolHandlers(this.endpoint);
   }

   private String getEndpointUri() {
      StringBuffer sUri = new StringBuffer();
      sUri.append(ClientShared.service(ClientShared.WS_PROTOCOL, ClientShared.WEBSOCKET_HOST, ClientShared.PORT, "/WebSocket/channel/"));
      sUri.append(this.invigilator.getCourse());
      sUri.append("/");
      sUri.append(this.invigilator.getActivity());
      sUri.append("/");
      sUri.append(this.invigilator.getCanonicalStudentName());
      sUri.append('-');
      sUri.append(this.invigilator.getID());
      sUri.append("/");
      sUri.append(this.invigilator.getProperty("student.directory.PASSWORD"));
      return sUri.toString();
   }

   private WebsocketClientEndpoint initializeWebsocketClientEndpoint() {
      Logger.log(Level.FINE, "", "Creating CCI");
      WebsocketClientEndpoint ep = new WebsocketClientEndpoint();
      ep.addListener(this);
      ep.addMessageHandler("stop", new StopMessageHandler(this.invigilator));
      ep.addMessageHandler("unload", new UnloadMessageHandler(this.invigilator));
      ep.addMessageHandler("load", new LoadMessageHandler(this.invigilator));
      ep.addMessageHandler("url", new URLMessageHandler(this.invigilator));
      ep.addMessageHandler("alert", new AlertMessageHandler(this.invigilator));
      ep.addMessageHandler("chat", new ChatMessageHandler(this.invigilator));
      ep.addMessageHandler("display", new WebPageMessageHandler(this.invigilator));
      ep.addMessageHandler("ping", new PingMessageHandler(this.invigilator));
      ep.addMessageHandler("level", new LogMessageHandler(this.invigilator));
      ep.addMessageHandler("wait", new EndWaitTimeMessageHandler(this.invigilator));
      ep.addMessageHandler("screen", new ScreenShotMessageHandler(this.invigilator));
      ep.addMessageHandler("screenShot", new ScreenShotFrequencyMessageHandler(this.invigilator));
      ep.addMessageHandler("authenticate", new AuthenticationFrequencyMessageHandler(this.invigilator));
      ep.addMessageHandler("server", new ServerMessageHandler(this.invigilator));
      ep.addMessageHandler("restart", new RestartSessionMessageHandler(this.invigilator));
      ep.addMessageHandler("reset", new ResetSessionMessageHandler(this.invigilator));
      ep.addMessageHandler("report", new ReportMessageHandler(this.invigilator));
      ep.addMessageHandler("test", new TestMessageHandler(this.invigilator));
      ep.addMessageHandler("webcam", new WebcamMessageHandler(this.invigilator));
      ep.addMessageHandler("event", new EventMessageHandler(this.invigilator));
      ep.addMessageHandler("events", new NullMessageHandler(this.invigilator));
      ep.addMessageHandler("process", new ProcessMessageHandler(this.invigilator));
      ep.addMessageHandler("annotate", new ReportAnnotationMessageHandler(this.invigilator));
      this.configureExtendedProtocolHandlers(ep);
      return ep;
   }

   private void configureExtendedProtocolHandlers(WebsocketClientEndpoint ep) {
      int i = 1;
      String urlProp = this.invigilator.getProperty("protocol.handler.url");
      if (urlProp == null) {
         Logger.log(Level.FINE, "No CCI extension protocol handlers loaded", "");
      } else {
         if (urlProp.startsWith("/")) {
            urlProp = ClientShared.service(ClientShared.PROTOCOL, ClientShared.CMS_HOST, ClientShared.PORT, urlProp);
         }

         this.classLoader = new ModuleClassLoader(new URL[0], MessageHandler.class.getClassLoader());

         try {
            this.classLoader.addURL(new URL(urlProp));
            String base = "protocol.handler.";

            for(String handlerString = this.invigilator.getProperty(base + i); handlerString != null; handlerString = this.invigilator.getProperty(base + i)) {
               String[] tokens = handlerString.split("[:,]");
               if (tokens != null) {
                  if (tokens.length == 2) {
                     String type = tokens[0].toLowerCase().trim();
                     String messageHandlerClass = tokens[1].trim();

                     try {
                        Class<?> clazz = Class.forName(messageHandlerClass, true, this.classLoader);
                        ep.addMessageHandler(type, (MessageHandler)clazz.getDeclaredConstructor().newInstance());
                        Logger.log(Level.INFO, String.format("CCI protocol handler loaded for %s type from %s", type, messageHandlerClass), "");
                     } catch (Exception e) {
                        Logger.log(Level.WARNING, String.format("CCI protocol handler creation error for %s: ", messageHandlerClass), e);
                     }
                  } else {
                     Logger.log(Level.WARNING, "CCI protocol handler had too few (<2) tokens: ", handlerString);
                  }
               } else {
                  Logger.log(Level.WARNING, "No CCI protocol handler defined for: ", handlerString);
               }

               ++i;
            }
         } catch (MalformedURLException var11) {
            Logger.log(Level.WARNING, String.format("protocol.handler.url has an illegal URL format: %s", urlProp), "");
         }

      }
   }

   public MessageHandler getHandler(String type) {
      return this.endpoint.getMessageHandler(type);
   }

   public boolean isOpen() {
      return this.endpoint.isOpen();
   }

   public void close() {
      try {
         this.endpoint.close();
      } catch (Exception var2) {
      }

   }

   public void ping() {
      if (!this.invigilator.isDone() && !this.invigilator.isEndedSession() && this.endpoint.ping()) {
         try {
            this.sendMessage(new Message());
         } catch (Exception var2) {
            this.close();
         }
      }

   }

   public void sendMessage(Message msg) throws IOException, EncodeException {
      this.endpoint.sendMessage(msg);
   }

   public void send(Message message) throws IOException {
      try {
         this.sendMessage(message);
      } catch (EncodeException var3) {
      }

   }

   public boolean register(String type, MessageHandler handler) {
      if (this.endpoint != null && handler != null && this.getHandler(type) == null) {
         if (type.length() > 64) {
            return false;
         } else {
            this.endpoint.addMessageHandler(type, handler);
            return true;
         }
      } else {
         return false;
      }
   }

   public void resourceEvent(Resource resource, String type, String description) {
      if (resource instanceof WebsocketClientEndpoint) {
         String reasonCode = "";
         if (description.equals("open")) {
            reasonCode = "OPEN";
         } else if (description.startsWith("close:")) {
            reasonCode = description.substring("close:".length());
         } else if (description.startsWith("error:")) {
            reasonCode = description.substring("error:".length());
         }

         if (this.endpoint.log()) {
            this.invigilator.logArchiver.put(edu.carleton.cas.logging.Level.DIAGNOSTIC, String.format("[%s] Control Channel %s", sdf.format(new Date()), reasonCode));
         }
      }

   }

   public class AlertServlet extends HttpServlet {
      private final AlertMessageHandler amh;

      public AlertServlet(MessageHandler amh) {
         this.amh = (AlertMessageHandler)amh;
      }

      protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         WebsocketEndpointManager.this.invigilator.setLastServlet("alerts");
         String meta = String.format("<title>%sCoMaS Alerts: %s (%s)</title>%s%s", WebsocketEndpointManager.this.invigilator.getTitle(), WebsocketEndpointManager.this.invigilator.getName(), WebsocketEndpointManager.this.invigilator.getID(), SystemWebResources.getStylesheet(), SystemWebResources.getIcon());
         response.addHeader("Access-Control-Allow-Origin", "*");
         response.setContentType("text/html");
         PrintWriter pw = response.getWriter();
         pw.print("<html lang=\"en\" xml:lang=\"en\"><head>");
         pw.print(meta);
         pw.print("</head><body>");
         ServletProcessor sp = WebsocketEndpointManager.this.invigilator.getServletProcessor();
         pw.print(sp.checkForServletCode());
         if (this.amh != null) {
            pw.print("<div style=\"margin:16px;font-family: Arial, Helvetica, sans-serif;\">");
            pw.print("<h1 id=\"reportTitle\">Alerts: ");
            pw.print(WebsocketEndpointManager.this.invigilator.getCourse());
            pw.print("/");
            pw.print(WebsocketEndpointManager.this.invigilator.getActivity());
            pw.print(" for ");
            pw.print(WebsocketEndpointManager.this.invigilator.getName());
            pw.print(" (");
            pw.print(WebsocketEndpointManager.this.invigilator.getID());
            pw.print(")");
            pw.print("</h1>");
            pw.print(sp.refreshForServlet());
            pw.print("<table class=\"w3-table-all\">");
            pw.print("<thead><th>Alert</th><th>Received</th><th>Read</th></thead><tbody>");

            AlertMessageHandler.AlertRecord[] var9;
            for(AlertMessageHandler.AlertRecord ar : var9 = this.amh.alerts()) {
               pw.print("<tr><td>");
               if (ar.acknowledged == 0L) {
                  pw.print("<span class=\"w3-tag w3-red\">");
               }

               pw.print(ar.alert);
               if (ar.acknowledged == 0L) {
                  pw.print("</span >");
               }

               pw.print("</td><td>");
               pw.print(WebsocketEndpointManager.sdf.format(new Date(ar.timestamp)));
               pw.print("</td><td>");
               if (ar.acknowledged > 0L) {
                  pw.print(WebsocketEndpointManager.sdf.format(new Date(ar.acknowledged)));
               } else {
                  pw.print("-");
               }

               pw.print("</td></tr>");
            }

            pw.print("</tbody></table><br/>");
            pw.print(sp.footerForServlet(true, true, SystemWebResources.getHomeButton()));
            pw.print("</div>");
         } else {
            pw.print("<h1>Sorry, alerts are not available at this time</h1>");
         }

         pw.print("</body></html>");
         response.setStatus(200);
      }
   }
}
