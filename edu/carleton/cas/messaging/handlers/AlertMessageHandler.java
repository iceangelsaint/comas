package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import edu.carleton.cas.ui.WebAlert;
import java.util.ArrayList;
import java.util.logging.Level;
import javax.swing.SwingUtilities;

public class AlertMessageHandler extends BaseMessageHandler implements MessageHandler {
   private final ArrayList alerts = new ArrayList();

   public AlertMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(final Message message) {
      final AlertRecord alertRecord = new AlertRecord(message.getContentMessage(), System.currentTimeMillis());
      if (alertRecord.alert.length() > 0) {
         SwingUtilities.invokeLater(new Runnable() {
            public void run() {
               WebAlert.alertDialog(alertRecord.alert, "CoMaS Administrator Alert!");
               alertRecord.acknowledged = System.currentTimeMillis();
               Logger.log(Level.INFO, String.format("Administrator message \"%s\" from %s", alertRecord.alert, message.getFrom()), "");
            }
         });
         synchronized(this.alerts) {
            this.alerts.add(alertRecord);
         }
      }

   }

   public int numberOfUnacknowledgedAlerts() {
      int unacknowledged = 0;

      for(AlertRecord ar : this.alerts) {
         if (ar.isUnacknowledged()) {
            ++unacknowledged;
         }
      }

      return unacknowledged;
   }

   public AlertRecord[] alerts() {
      synchronized(this.alerts) {
         return (AlertRecord[])this.alerts.toArray(new AlertRecord[this.alerts.size()]);
      }
   }

   public class AlertRecord {
      public final String alert;
      public final long timestamp;
      public long acknowledged;

      public AlertRecord(String alert, long timestamp) {
         if (alert != null) {
            this.alert = alert.trim();
         } else {
            this.alert = "";
         }

         this.timestamp = timestamp;
         this.acknowledged = 0L;
      }

      public boolean isUnacknowledged() {
         return this.acknowledged == 0L;
      }
   }
}
