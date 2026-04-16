package edu.carleton.cas.utility;

import java.util.HashMap;
import java.util.Map;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

public class DetectVM {
   private static final Map vmVendor = new HashMap();
   private static final Map vmMacAddressProps;
   private static final String[] vmModelArray;

   static {
      vmVendor.put("bhyve bhyve", "bhyve");
      vmVendor.put("KVMKVMKVM", "KVM");
      vmVendor.put("TCGTCGTCGTCG", "QEMU");
      vmVendor.put("Microsoft Hv", "Microsoft Hyper-V or Windows Virtual PC");
      vmVendor.put("lrpepyh vr", "Parallels");
      vmVendor.put("VMwareVMware", "VMware");
      vmVendor.put("XenVMMXenVMM", "Xen HVM");
      vmVendor.put("ACRNACRNACRN", "Project ACRN");
      vmVendor.put("QNXQVMBSQG", "QNX Hypervisor");
      vmMacAddressProps = new HashMap();
      vmMacAddressProps.put("00:50:56", "VMware ESX 3");
      vmMacAddressProps.put("00:0C:29", "VMware ESX 3");
      vmMacAddressProps.put("00:05:69", "VMware ESX 3");
      vmMacAddressProps.put("00:03:FF", "Microsoft Hyper-V");
      vmMacAddressProps.put("00:1C:42", "Parallels Desktop");
      vmMacAddressProps.put("00:0F:4B", "Virtual Iron 4");
      vmMacAddressProps.put("08:00:27", "VirtualBox");
      vmMacAddressProps.put("02:42:AC", "Docker Container");
      vmModelArray = new String[]{"Linux KVM", "Linux lguest", "OpenVZ", "Qemu", "Microsoft Virtual PC", "VMWare", "linux-vserver", "Xen", "FreeBSD Jail", "VirtualBox", "Parallels", "Linux Containers", "LXC"};
   }

   public static void main(String[] args) {
      String vmString = identifyVM(true);
      if (vmString.isEmpty()) {
         System.out.println("You do not appear to be on a Virtual Machine.");
      } else {
         System.out.println("You appear to be on a VM: " + vmString);
      }

   }

   public static String identifyVM(boolean full) {
      SystemInfo si = new SystemInfo();
      HardwareAbstractionLayer hw = si.getHardware();
      String vendor = hw.getProcessor().getProcessorIdentifier().getVendor().trim();
      if (vmVendor.containsKey(vendor)) {
         return (String)vmVendor.get(vendor);
      } else {
         if (full) {
            for(NetworkIF nif : hw.getNetworkIFs()) {
               String mac = nif.getMacaddr().toUpperCase();
               String oui = mac.length() > 7 ? mac.substring(0, 8) : mac;
               if (vmMacAddressProps.containsKey(oui)) {
                  return (String)vmMacAddressProps.get(oui);
               }
            }
         }

         String model = hw.getComputerSystem().getModel();

         String[] var14;
         for(String vm : var14 = vmModelArray) {
            if (model.contains(vm)) {
               return vm;
            }
         }

         String manufacturer = hw.getComputerSystem().getManufacturer();
         return "Microsoft Corporation".equals(manufacturer) && "Virtual Machine".equals(model) ? "Microsoft Hyper-V" : "";
      }
   }
}
