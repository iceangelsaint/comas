package edu.carleton.cas.messaging.handlers;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.messaging.Message;
import edu.carleton.cas.messaging.MessageHandler;
import edu.carleton.cas.resources.HardwareAndSoftwareMonitor;
import edu.carleton.cas.resources.HardwareAndSoftwareMonitor.PatternAction;

public class ProcessMessageHandler extends BaseMessageHandler implements MessageHandler {
   public ProcessMessageHandler(Invigilator invigilator) {
      super(invigilator);
   }

   public void handleMessage(Message message) {
      String cmd = message.getContentMessage();
      if (cmd != null && cmd.length() > 0) {
         cmd = cmd.trim();
         cmd = this.mapNameForCoMaS(cmd);
         int numberOfAffectedProcesses = 0;
         HardwareAndSoftwareMonitor.PatternAction pa = null;

         try {
            pa = PatternAction.compile(cmd);
            if (ClientShared.isLinuxOS() && cmd.contains("CoMaS")) {
               pa.usePath();
            }

            numberOfAffectedProcesses = this.invigilator.getHardwareAndSoftwareMonitor().findAndActOnProcessesConformingToPattern(pa);
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, String.format("%s affected %d processes using %s", message.getFrom(), numberOfAffectedProcesses, pa.toString()));
         } catch (Exception var6) {
            this.invigilator.logArchiver.put(Level.DIAGNOSTIC, String.format("%s sent an illegal process message \"%s\"", message.getFrom(), cmd));
         }
      }

   }

   private String mapNameForCoMaS(String cmd) {
      if (cmd.contains("comas") && cmd.contains("CoMaS")) {
         cmd = cmd.toLowerCase();
         if (ClientShared.isMacOS()) {
            return cmd.replace("comas", "JavaApplicationStub");
         } else if (ClientShared.isWindowsOS()) {
            return cmd.replace("comas", "CoMaS");
         } else {
            return ClientShared.isLinuxOS() ? cmd.replace("comas", ".*CoMaS.*") : cmd;
         }
      } else {
         return cmd;
      }
   }
}
