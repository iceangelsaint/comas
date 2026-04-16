package edu.carleton.cas.utility;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.client.ClientConfig;

public class ClientHelper {
   public static Client createClient(String protocol) {
      if (protocol.equals("https")) {
         System.setProperty("jsse.enableSNIExtension", "false");
         TrustManager[] certs = new TrustManager[]{new InsecureTrustManager()};
         SSLContext ctx = null;

         try {
            ctx = SSLContext.getInstance("TLSv1.2");
            ctx.init((KeyManager[])null, certs, new SecureRandom());
         } catch (GeneralSecurityException var4) {
         }

         return ClientBuilder.newBuilder().withConfig(new ClientConfig()).hostnameVerifier(new InsecureHostnameVerifier()).sslContext(ctx).build();
      } else {
         return ClientBuilder.newClient();
      }
   }
}
