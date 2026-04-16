package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.utility.ClientHelper;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;

public class SessionStartTask implements Runnable {
   public static final String NAME = "session start";
   private static AtomicBoolean isRunning = new AtomicBoolean(false);
   private final Invigilator invigilator;
   private ProgressIndicator progressIndicator;

   SessionStartTask(Invigilator invigilator, ProgressIndicator progressIndicator) {
      this.invigilator = invigilator;
      this.progressIndicator = progressIndicator;
   }

   public void run() {
      if (isRunning.compareAndSet(false, true)) {
         Thread t = Thread.currentThread();
         t.setName("session start");
         t.setUncaughtExceptionHandler(this.invigilator);
         int times = 0;
         Logger.output("Preparing to run session");
         boolean timeCheck = false;

         while(!timeCheck) {
            if (this.hasEndedSession()) {
               return;
            }

            int rtnCode = this.invigilator.canStart();
            if (rtnCode < 0) {
               String msg = String.format("This session cannot continue, server return code: %d.\n%s", rtnCode, this.invigilator.resolveVariablesInMessage(ClientShared.SUPPORT_MESSAGE));
               Logger.output(msg);
               this.progressIndicator.setProgressMessage(msg);
            }

            timeCheck = rtnCode > 0;
            if (timeCheck) {
               try {
                  timeCheck = this.isActivityStartable();
               } catch (NumberFormatException var7) {
                  String msg = String.format("An incorrect time format has been detected.\n%s", this.invigilator.resolveVariablesInMessage(ClientShared.SUPPORT_MESSAGE));
                  Logger.output(msg);
                  this.progressIndicator.setProgressMessage(msg);
                  timeCheck = false;
               }
            }

            if (!timeCheck) {
               ++times;
               this.doWait(" for start time", times);
            }
         }

         Logger.output("Session can start");

         while(!this.isExamOk()) {
            if (this.hasEndedSession()) {
               return;
            }

            ++times;
            this.doWait(" for exam", times);
         }

         Logger.output("Activity is deployed");
         this.invigilator.runExam();
         isRunning.set(false);
      }
   }

   private boolean hasEndedSession() {
      return this.invigilator.isEndedSession() || this.invigilator.isInEndingState();
   }

   private boolean isActivityStartable() throws NumberFormatException {
      Properties config = this.invigilator.getProperties();
      boolean rtn = false;
      if (config.containsKey("START_MSECS") && config.containsKey("END_MSECS") && config.containsKey("CURRENT_TIME")) {
         String startAsString = config.getProperty("START_MSECS");
         String endAsString = config.getProperty("END_MSECS");
         String nowAsString = config.getProperty("CURRENT_TIME");
         long start = Long.parseLong(startAsString);
         long end = Long.parseLong(endAsString);
         long now = Long.parseLong(nowAsString);
         rtn = start <= now && now <= end;
      }

      return rtn;
   }

   private boolean isExamOk() {
      try {
         Client client = ClientHelper.createClient(ClientShared.PROTOCOL);
         WebTarget webTarget = client.target(ClientShared.BASE_EXAM).path("deployed").path("activity");
         Invocation.Builder invocationBuilder = webTarget.request(new String[]{"application/x-www-form-urlencoded"});
         invocationBuilder.accept(new String[]{"text/plain"});
         invocationBuilder.cookie("token", this.invigilator.getToken());
         Form form = new Form();
         form.param("course", this.invigilator.getCourse());
         form.param("activity", this.invigilator.getActivity());
         form.param("version", ClientShared.VERSION);
         form.param("passkey", ClientShared.PASSKEY_EXAM);
         Response response = invocationBuilder.post(Entity.entity(form, "application/x-www-form-urlencoded"));
         Logger.log(Level.INFO, String.format("[%s] %s", response.getStatus(), "Exam check response"), "");
         return response.getStatus() == 200;
      } catch (Exception e) {
         Logger.log(Level.WARNING, String.format("Exam check failure for %s: %s", ClientShared.BASE_EXAM, e.getMessage()), "");
         this.progressIndicator.setProgressMessage(String.format("Warning: %s", e.getMessage()));
         return false;
      }
   }

   public void doWait(String msg, int times, int sleep) {
      try {
         Thread.sleep((long)sleep);
         this.progressIndicator.setProgressMessage(String.format("Waiting%s (%d) ...", msg, times));
      } catch (InterruptedException var5) {
      }

   }

   private void doWait(String msg, int times) {
      this.doWait(msg, times, ClientShared.RETRY_TIME);
   }
}
