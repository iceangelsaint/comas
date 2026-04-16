package edu.carleton.cas.background;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.events.Event;
import edu.carleton.cas.events.EventListener;
import edu.carleton.cas.events.EventPublisher;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.utility.ClientHelper;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;

public class LogArchiver extends Archiver implements EventPublisher {
   private HashMap logs = new HashMap();
   private CopyOnWriteArraySet listeners = new CopyOnWriteArraySet();

   public LogArchiver(Invigilator login, String target, String type, String name) {
      super(login, target, type, name);
   }

   public void put(Level level, String description, String type) {
      LogRecord lr = new LogRecord(level, description);
      lr.setLoggerName(type);
      this.put(lr);
   }

   public void put(Level level, String description) {
      LogRecord lr = new LogRecord(level, description);
      this.put(lr);
   }

   public void put(Level level, String description, Object[] args) {
      LogRecord lr = new LogRecord(level, description);
      lr.setParameters(args);
      this.put(lr);
   }

   public boolean doWork(Object obj) {
      LogRecord item = (LogRecord)obj;
      return this.log(item.getLevel(), item.getMessage(), item.getMillis(), item.getParameters(), item.getLoggerName());
   }

   public void doWorkOnFailure(Object obj) {
      LogRecord item = (LogRecord)obj;
      String description = item.getMessage();
      Long logged = (Long)this.logs.getOrDefault(description, 0L) + 1L;
      this.publish(logged, item.getLevel(), description, item.getMillis(), item.getParameters());
   }

   private boolean log(Level severity, String description, long time, Object[] args, String type) {
      if (!this.login.isAllowedToUpload()) {
         return true;
      } else {
         this.lock();

         boolean var17;
         try {
            Long logged = (Long)this.logs.getOrDefault(description, 0L) + 1L;
            if (logged % (long)ClientShared.LOG_GENERATION_FREQUENCY != 1L) {
               this.publish(logged, severity, description, time, args);
               return true;
            }

            String logMsg;
            if (logged > 1L) {
               logMsg = description + " (" + String.valueOf(logged) + ")";
            } else {
               logMsg = description;
            }

            Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
            WebTarget webTarget = client.target(ClientShared.BASE_LOG).path(ClientShared.LOG_PATH);
            Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
            invocationBuilder.accept(new String[]{"application/json"});
            String token = this.login.getToken();
            if (token != null) {
               invocationBuilder.cookie("token", token);
            }

            Form form = new Form();
            form.param("passkey", ClientShared.PASSKEY_LOG);
            form.param("name", this.login.getName());
            form.param("id", this.login.getID());
            form.param("course", this.login.getCourse());
            form.param("activity", this.login.getActivity());
            String var10002 = ClientShared.LOG_URL;
            form.param("url", var10002 + "s/" + this.login.getCourse() + "/" + this.login.getActivity() + "/" + this.login.getName());
            form.param("severity", severity.getName());
            if (type != null) {
               form.param("type", type);
            }

            form.param("description", logMsg);
            form.param("time", "" + time);
            Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
            if (this.isLoggedLocally(severity)) {
               int var22 = response.getStatus();
               Logger.log(severity, "", " [" + var22 + "] " + logMsg);
            }

            if (response.getStatus() == 401 && !Authenticator.isRunning()) {
               (new Thread(new Authenticator(this.login))).start();
            }

            boolean rtnValue = response.getStatus() < 204;
            if (rtnValue) {
               this.publish(logged, severity, description, time, args);
            }

            var17 = rtnValue;
         } catch (Exception e) {
            this.statistics.setLastException(e);
            Logger.log(edu.carleton.cas.logging.Level.WARNING, "Failed to log: ", description);
            return false;
         } finally {
            this.unlock();
         }

         return var17;
      }
   }

   private boolean isLoggedLocally(Level severity) {
      return severity != edu.carleton.cas.logging.Level.DIAGNOSTIC && severity != edu.carleton.cas.logging.Level.REPORT && severity != edu.carleton.cas.logging.Level.PROBLEM;
   }

   private void publish(long logged, Level severity, String description, long time, Object[] args) {
      this.logs.put(description, logged);
      Event evt = new Event();
      evt.put("description", description);
      evt.put("severity", severity.getName());
      evt.put("logged", logged);
      evt.put("time", time);
      if (args != null) {
         evt.put("args", args);
      }

      this.publish(evt);
   }

   public void deregister(EventListener arg0) {
      this.listeners.remove(arg0);
   }

   public void publish(Event arg0) {
      for(EventListener el : this.listeners) {
         el.notify(arg0);
      }

   }

   public void register(EventListener arg0) {
      this.listeners.add(arg0);
   }
}
