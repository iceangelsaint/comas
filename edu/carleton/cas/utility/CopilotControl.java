package edu.carleton.cas.utility;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.logging.Level;

public class CopilotControl {
   private static final String key = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced";
   private static final String valueName = "TaskbarCopilot";
   private static final String keyCopilot = "Software\\Microsoft\\Windows\\Shell\\Copilot";
   private static final String valueNameCopilot = "IsCopilotAvailable";
   private static final String keyBingChat = "Software\\Microsoft\\Windows\\Shell\\Copilot\\BingChat";
   private static final String valueNameBingChat = "IsUserEligible";
   private static final String keyAllowCopilotRuntime = "Software\\Microsoft\\Windows\\CurrentVersion\\WindowsCopilot";
   private static final String valueNameCopilotRuntime = "AllowCopilotRuntime";
   private static final String keyAllowShowCopilotButton = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced";
   private static final String valueNameShowCopilotButton = "ShowCoPilotButton";
   private static final String keyAllowCopilot = "Software\\Policies\\Microsoft\\Windows\\WindowsCopilot";
   private static final String valueNameAllowCopilot = "TurnOffWindowsCopilot";
   private GenericWindowsRegistry genericWindowsRegistry = new GenericWindowsRegistry();
   private final Invigilator invigilator;

   public CopilotControl(Invigilator invigilator) {
      this.invigilator = invigilator;
   }

   public void enable() {
      this.doIt(1);
   }

   public void disable() {
      this.doIt(0);
   }

   private void doIt(int value) {
      try {
         this.genericWindowsRegistry.writeKeyValue("Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced", "TaskbarCopilot", value);
      } catch (Exception e) {
         this.invigilator.logArchiver.put(Level.WARNING, "Problem with Copilot control Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\\TaskbarCopilot: " + e.toString());
      }

      try {
         this.genericWindowsRegistry.writeKeyValue("Software\\Microsoft\\Windows\\Shell\\Copilot", "IsCopilotAvailable", value);
      } catch (Exception e) {
         this.invigilator.logArchiver.put(Level.WARNING, "Problem with Copilot control Software\\Microsoft\\Windows\\Shell\\Copilot\\IsCopilotAvailable: " + e.toString());
      }

      try {
         this.genericWindowsRegistry.writeKeyValue("Software\\Microsoft\\Windows\\Shell\\Copilot\\BingChat", "IsUserEligible", value);
      } catch (Exception e) {
         this.invigilator.logArchiver.put(Level.WARNING, "Problem with Copilot control Software\\Microsoft\\Windows\\Shell\\Copilot\\BingChat\\IsUserEligible: " + e.toString());
      }

      try {
         this.genericWindowsRegistry.writeKeyValue("Software\\Microsoft\\Windows\\CurrentVersion\\WindowsCopilot", "AllowCopilotRuntime", value);
      } catch (Exception e) {
         this.invigilator.logArchiver.put(Level.WARNING, "Problem with Copilot control Software\\Microsoft\\Windows\\CurrentVersion\\WindowsCopilot\\AllowCopilotRuntime: " + e.toString());
      }

      try {
         this.genericWindowsRegistry.writeKeyValue("Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced", "ShowCoPilotButton", value);
      } catch (Exception e) {
         this.invigilator.logArchiver.put(Level.WARNING, "Problem with Copilot control Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\\ShowCoPilotButton: " + e.toString());
      }

      try {
         this.genericWindowsRegistry.writeKeyValue("Software\\Policies\\Microsoft\\Windows\\WindowsCopilot", "TurnOffWindowsCopilot", value == 0 ? 1 : 0);
      } catch (Exception e) {
         this.invigilator.logArchiver.put(Level.WARNING, "Problem with Copilot control Software\\Policies\\Microsoft\\Windows\\WindowsCopilot\\TurnOffWindowsCopilot: " + e.toString());
         this.invigilator.logArchiver.put(Level.WARNING, "Problem with Copilot control: " + e.toString());
      }

   }
}
