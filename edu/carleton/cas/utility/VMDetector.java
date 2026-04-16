package edu.carleton.cas.utility;

import com.cogerent.utility.PropertyValue;
import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.resources.Resource;
import edu.carleton.cas.resources.ResourceListener;
import edu.carleton.cas.resources.VMCheck;
import edu.carleton.cas.ui.WebAlert;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.io.InputStream;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Scanner;

public abstract class VMDetector {
   private static byte[][] invalidMACs = new byte[][]{{0, 5, 105}, {0, 28, 20}, {0, 12, 41}, {0, 80, 86}, {8, 0, 39}, {10, 0, 39}, {0, 3, -1}, {0, 21, 93}, {0, 28, 66}, {0, 22, 62}, {0, 29, -40}, {0, 24, 81}, {88, -100, -4}, {80, 107, -115}, {84, 82, 0}, {-106, 0, 0}, {2, 66}};
   private static String[] vendors = new String[]{"VMWare", "VMWare", "VMWare", "VMWare", "VirtualBox", "VirtualBox", "Virtual-PC", "Hyper-V", "Parallels", "Xen", "Virtual-PC", "SWsoft", "bhyve", "Nutanix AHV", "KVM", "Hetzner vServer", "Docker"};
   private static Dimension[] knownResolutions = new Dimension[]{new Dimension(640, 360), new Dimension(800, 600), new Dimension(1024, 768), new Dimension(1280, 720), new Dimension(1280, 800), new Dimension(1280, 1024), new Dimension(1360, 768), new Dimension(1366, 768), new Dimension(1440, 900), new Dimension(1536, 864), new Dimension(1600, 900), new Dimension(1680, 1050), new Dimension(1920, 1080), new Dimension(1920, 1200), new Dimension(2048, 1152), new Dimension(2560, 1080), new Dimension(2560, 1440), new Dimension(3440, 1440), new Dimension(3840, 2160), new Dimension(4096, 2304), new Dimension(5120, 2880), new Dimension(3072, 1920), new Dimension(1680, 945), new Dimension(2048, 1152), new Dimension(2304, 1296), new Dimension(2560, 1440)};
   private static HashMap knownScreenResolutions = new HashMap(1024);
   private static VMCheck vmCheck;

   static {
      initializeResolutions();
   }

   public static int isVMMac() throws SocketException {
      Enumeration<NetworkInterface> net = NetworkInterface.getNetworkInterfaces();

      while(net.hasMoreElements()) {
         NetworkInterface element = (NetworkInterface)net.nextElement();
         int index = isVMMac(element.getHardwareAddress());
         if (index >= 0) {
            return index;
         }
      }

      return -1;
   }

   public static int isVMMac(byte[] mac) {
      if (mac == null) {
         return -1;
      } else {
         for(int j = 0; j < invalidMACs.length; ++j) {
            byte[] invalid = invalidMACs[j];
            boolean result = true;

            for(int i = 0; i < invalid.length; ++i) {
               if (invalid[i] != mac[i]) {
                  result = false;
                  break;
               }
            }

            if (result) {
               return j;
            }
         }

         return -1;
      }
   }

   public static NetworkInterface networkInterfaceFromIndex(int index) throws SocketException {
      if (index < 0) {
         return null;
      } else {
         Enumeration<NetworkInterface> net = NetworkInterface.getNetworkInterfaces();
         byte[] vmInterface = invalidMACs[index];

         while(net.hasMoreElements()) {
            NetworkInterface element = (NetworkInterface)net.nextElement();
            byte[] macAddress = element.getHardwareAddress();
            if (macAddress != null) {
               boolean result = true;

               for(int i = 0; i < vmInterface.length; ++i) {
                  if (vmInterface[i] != macAddress[i]) {
                     result = false;
                     break;
                  }
               }

               if (result) {
                  return element;
               }
            }
         }

         return null;
      }
   }

   public static boolean isInterfaceUp(int index) throws SocketException {
      NetworkInterface element = networkInterfaceFromIndex(index);
      if (element != null) {
         try {
            return element.isUp();
         } catch (SocketException var3) {
         }
      }

      return false;
   }

   public static boolean isInterfaceVirtual(int index) throws SocketException {
      NetworkInterface element = networkInterfaceFromIndex(index);
      return element != null ? element.isVirtual() : false;
   }

   public static String VMVendor(int index) {
      return index >= 0 && index < vendors.length ? vendors[index] : null;
   }

   public static boolean isStdResolution(Rectangle d) {
      Dimension[] var4;
      for(Dimension res : var4 = knownResolutions) {
         if (res.height == d.height && res.width == d.width) {
            return true;
         }
      }

      return false;
   }

   public static void initializeResolutions() {
      InputStream is = VMDetector.class.getClassLoader().getResourceAsStream("resolutions.txt");
      if (is != null) {
         Scanner scanner = null;

         try {
            scanner = new Scanner(is);

            while(scanner.hasNext()) {
               int width = scanner.nextInt();
               int height = scanner.nextInt();
               String percentage = scanner.next();

               try {
                  knownScreenResolutions.put(new Dimension(width, height), Float.parseFloat(percentage));
               } catch (NumberFormatException var10) {
               }
            }
         } catch (NoSuchElementException var11) {
         } finally {
            if (scanner != null) {
               scanner.close();
            }

         }

      }
   }

   public static float isKnownResolution(Rectangle d) {
      Float percentage = (Float)knownScreenResolutions.get(d.getSize());
      if (percentage == null) {
         return isStdResolution(d) ? 1.0F : 0.0F;
      } else {
         return percentage;
      }
   }

   public static boolean isRunnableInVM(Invigilator login) {
      String value = PropertyValue.getValue(login, "session", "virtual_machine");
      if (value == null) {
         value = login.getProperty("vm_enabled");
      }

      if (value == null) {
         value = login.getProperty("virtual_machine");
      }

      if (value == null) {
         value = "true";
      }

      return Utils.isTrueOrYes(value.trim(), true);
   }

   public static VMCheck runDynamicVMCheck(final Invigilator login) {
      if (vmCheck != null) {
         vmCheck.run();
         return vmCheck;
      } else {
         Logger.output("Running VM check");
         Properties properties = login.getProperties();
         vmCheck = new VMCheck(properties);
         vmCheck.addListener(new ResourceListener() {
            public void resourceEvent(Resource resource, String type, String description) {
               boolean canContinue = VMDetector.isRunnableInVM(login);
               String continueLogMsg;
               if (canContinue) {
                  continueLogMsg = "";
               } else {
                  continueLogMsg = "Session was terminated";
               }

               if (type.equals("exception")) {
                  String action = canContinue ? "" : "\n\nACTION: Add session." + login.getID() + ".virtual_machine=true to properties if this is a false positive.\n\n";
                  String msg = String.format("Unable to run a virtual machine detection test (%s). %s%s", description, action, continueLogMsg);
                  login.logArchiver.put(Level.LOGGED, msg, login.createProblemUnknownEvent("vm_test"));
                  if (!canContinue) {
                     String terminateMsg = String.format("Unable to run a virtual machine detection test.\nYour session will now terminate.\n%s", login.resolveVariablesInMessage(ClientShared.SUPPORT_MESSAGE));
                     VMDetector.terminateWithAlert(login, terminateMsg);
                  }
               } else {
                  String action = canContinue ? "" : "\n\nACTION: Add session." + login.getID() + ".virtual_machine=true to properties if this is a false positive.\n\n";
                  String msg = String.format("Virtual machine detected (%s). %s%s", description.trim(), action, continueLogMsg);
                  login.logArchiver.put(Level.LOGGED, msg, login.createProblemUnknownEvent("vm_running"));
                  if (!canContinue) {
                     VMDetector.terminateWithAlert(login, "Session can't run in a virtual machine.\nYour session will now terminate.");
                  }
               }

            }
         });
         vmCheck.open();
         return vmCheck;
      }
   }

   private static void terminateWithAlert(Invigilator login, String msg) {
      WebAlert.errorDialog(msg);
      login.setStateAndAuthenticate("Terminated");
      System.exit(-1);
   }

   public static void runStaticVMCheck(Invigilator login) {
      Rectangle[] bounds = Displays.getScreenBounds();

      for(Rectangle bound : bounds) {
         float percentage = isKnownResolution(bound);
         if (percentage < ClientShared.VM_SCREEN_RESOLUTION_PERCENTAGE) {
            login.logArchiver.put(Level.NOTED, String.format("Non-standard screen size detected (width=%.01f,height=%.01f). Percentage usage (%.03f%s) below threshold. Possible virtual machine?", bound.getWidth(), bound.getHeight(), percentage, "%"), login.createProblemUnknownEvent("vm_display"));
         }
      }

      try {
         int index = isVMMac();
         if (index != -1) {
            String up = isInterfaceUp(index) ? "is up" : "is not up";
            String virtual = isInterfaceVirtual(index) ? "virtual " : "";
            login.logArchiver.put(Level.WARNING, String.format("Virtual machine technology detected (%s), %snetwork interface %s", VMVendor(index), virtual, up), login.createProblemUnknownEvent("vm_interfaces"));
         }
      } catch (SocketException | HeadlessException var7) {
      }

   }

   public static VMCheck runAllVMChecks(Invigilator login) {
      runStaticVMCheck(login);
      return runDynamicVMCheck(login);
   }

   public static boolean vmChecksPerformed() {
      return vmCheck != null;
   }
}
