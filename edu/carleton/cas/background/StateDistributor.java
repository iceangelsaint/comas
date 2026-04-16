package edu.carleton.cas.background;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.utility.ClientHelper;
import java.util.logging.Level;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;

public class StateDistributor extends Archiver {
   private String nameWithDashes;

   public StateDistributor(Invigilator login, String target, String type, String name) {
      super(login, target, type, name);
   }

   public void put(String key, String value) {
      this.put(new KeyValue(key, value));
   }

   public boolean doWork(Object obj) {
      KeyValue item = (KeyValue)obj;
      return item.isValid() ? this.distributeMessage(item.key, item.value) : true;
   }

   public void doWorkOnFailure(Object obj) {
   }

   private boolean distributeMessage(String service, String path, String name, String password, String state, String value) {
      this.lock();

      try {
         Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
         WebTarget webTarget = client.target(service).path(path);
         Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
         invocationBuilder.accept(new String[]{"text/plain"});
         Form form = new Form();
         form.param("name", name);
         form.param("password", password);
         form.param("course", this.login.getCourse());
         form.param("activity", this.login.getActivity());
         form.param("state", state);
         form.param("value", value);
         String authToken = this.login.getToken();
         if (authToken != null) {
            invocationBuilder.cookie("token", authToken);
            form.param("token", authToken);
         }

         Logger.log(Level.FINE, service + " " + path + " " + name + " " + password + " " + state + " " + value, "");
         Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
         String rtn = (String)response.readEntity(String.class);
         Logger.log(Level.FINE, "Response from " + service + "/" + path + " ", rtn);
         if (response.getStatus() != 200) {
            return false;
         }
      } catch (Exception e) {
         this.statistics.setLastException(e);
         Logger.log(Level.WARNING, "Exception occurred during distribution of state for " + this.login.getNameAndID() + ":", e);
         return false;
      } finally {
         this.unlock();
      }

      return true;
   }

   private boolean distributeMessage(String state, String value) {
      if (this.nameWithDashes == null) {
         String var10001 = this.login.getName().replace(" ", "-");
         this.nameWithDashes = var10001 + "-" + this.login.getID();
      }

      return this.distributeMessage(ClientShared.BASE_LOGIN, "state", this.nameWithDashes, this.login.getStudentPassword(), state, value);
   }

   private class KeyValue {
      private final String key;
      private final String value;

      KeyValue(String key, String value) {
         this.key = key;
         this.value = value;
      }

      boolean isValid() {
         return this.key != null && this.value != null;
      }
   }
}
