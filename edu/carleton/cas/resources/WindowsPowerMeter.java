package edu.carleton.cas.resources;

public class WindowsPowerMeter {
   public int getEstimatedChargeRemaining() {
      String[] cmd = new String[]{"WMIC", "PATH", "Win32_Battery", "Get", "EstimatedChargeRemaining"};
      LineContainingPatternProcessor lcpp = new LineContainingPatternProcessor("[0-9]{1,2}.*");
      CommandRunner cr = new CommandRunner(cmd, lcpp);
      cr.run();
      String[] values = lcpp.result().split("\n");
      if (values != null && values.length != 0) {
         int total = 0;
         int count = 0;

         for(String valueAsString : values) {
            try {
               total += Integer.parseInt(valueAsString.trim());
               ++count;
            } catch (NumberFormatException var12) {
            }
         }

         return count == 0 ? -1 : total / count;
      } else {
         return -1;
      }
   }

   public long getEstimatedRunTime() {
      String[] cmd = new String[]{"WMIC", "PATH", "Win32_Battery", "Get", "EstimatedRunTime"};
      LineContainingPatternProcessor lcpp = new LineContainingPatternProcessor("[0-9]{1,}.*");
      CommandRunner cr = new CommandRunner(cmd, lcpp);
      cr.run();
      String[] values = lcpp.result().split("\n");
      if (values != null && values.length != 0) {
         long value = 0L;
         long total = 0L;
         int count = 0;

         for(String valueAsString : values) {
            try {
               value = Long.parseLong(valueAsString.trim());
               if (value > 0L && value < 10000L) {
                  ++count;
                  total += value;
               }
            } catch (NumberFormatException var15) {
            }
         }

         return count == 0 ? -1L : total;
      } else {
         return -1L;
      }
   }
}
