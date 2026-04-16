package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.background.timers.TimerService;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.jetty.embedded.ProgressIndicator;
import edu.carleton.cas.jetty.embedded.SessionEndTask;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import edu.carleton.cas.ui.DisappearingAlert;
import java.util.Calendar;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StopMessageHandler extends BaseMessageHandler implements MessageHandler {
   String msg;
   TimerTask stopTask;

   public StopMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      this.msg = message.getFrom() + " stopped the session";
      Logger.log(Level.WARNING, this.msg, "");
      String stopMessage = message.getContentMessage();
      this.processMessage(stopMessage);
   }

   private void processMessage(String message) {
      if (message != null) {
         message = message.trim();
         if (!message.isEmpty() && !message.equalsIgnoreCase("now")) {
            String[] tokens = message.split(" ");
            if (tokens.length == 2) {
               if (tokens[0].trim().equalsIgnoreCase("in")) {
                  this.processStopInSetAmountOfTime(tokens[1].trim());
               } else if (tokens[0].trim().equalsIgnoreCase("at")) {
                  this.processStopAtTime(tokens[1].trim());
               }
            }

         } else {
            this.setupStopTask(0L);
         }
      }
   }

   private void processStopInSetAmountOfTime(String time) {
      Pattern pattern = Pattern.compile("^(\\d+)([smh])$");
      Matcher matcher = pattern.matcher(time);
      if (matcher.matches()) {
         try {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            if (unit.equals("s")) {
               value *= 1000L;
            } else if (unit.equals("m")) {
               value = value * 60L * 1000L;
            } else if (unit.equals("h")) {
               value = value * 3600L * 1000L;
            }

            this.setupStopTask(value);
         } catch (NumberFormatException var7) {
         }
      }

   }

   private void processStopAtTime(String time) {
      Pattern pattern = Pattern.compile("^(?:(\\d|[01]\\d|2[0-3]):([0-5]\\d)|([01]\\d|2[0-3]):([0-5]\\d):([0-5]\\d))$");
      Matcher matcher = pattern.matcher(time);
      if (matcher.matches()) {
         try {
            Calendar calendar = Calendar.getInstance();
            if (matcher.group(1) != null) {
               calendar.set(11, Integer.parseInt(matcher.group(1)));
               calendar.set(12, Integer.parseInt(matcher.group(2)));
               calendar.set(13, 0);
            } else {
               calendar.set(11, Integer.parseInt(matcher.group(3)));
               calendar.set(12, Integer.parseInt(matcher.group(4)));
               calendar.set(13, Integer.parseInt(matcher.group(5)));
            }

            long initialDelay = calendar.getTimeInMillis() - System.currentTimeMillis();
            if (initialDelay < 0L) {
               initialDelay += TimeUnit.MILLISECONDS.convert(12L, TimeUnit.HOURS);
            }

            this.setupStopTask(initialDelay);
         } catch (NumberFormatException var7) {
         }
      }

   }

   private void setupStopTask(long time) {
      if (this.stopTask != null) {
         this.stopTask.cancel();
      }

      this.stopTask = new TimerTask() {
         public void run() {
            DisappearingAlert da = new DisappearingAlert((long)ClientShared.DISAPPEARING_ALERT_TIMEOUT, 1);
            StopMessageHandler.this.invigilator.setInvigilatorState(InvigilatorState.ended);
            String var10005 = StopMessageHandler.this.msg;
            da.setRunOnCloseRegardless(new SessionEndTask(StopMessageHandler.this.invigilator, (ProgressIndicator)null, "Session ended because " + var10005 + " for " + StopMessageHandler.this.invigilator.getNameAndID()));
            da.show(StopMessageHandler.this.msg);
         }
      };
      TimerService.schedule(this.stopTask, time);
   }
}
