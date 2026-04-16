package edu.carleton.cas.utility;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

public class GenericWindowsRegistry {
   WinReg.HKEY hkey;

   GenericWindowsRegistry() {
      this(WinReg.HKEY_CURRENT_USER);
   }

   GenericWindowsRegistry(WinReg.HKEY hkey) {
      this.hkey = hkey;
   }

   public int readKeyIntValue(String _key, String _valueName) {
      if (Advapi32Util.registryValueExists(this.hkey, _key, _valueName)) {
         int value = Advapi32Util.registryGetIntValue(this.hkey, _key, _valueName);
         return value;
      } else {
         return Integer.MIN_VALUE;
      }
   }

   public String readKeyStringValue(String _key, String _valueName) {
      if (Advapi32Util.registryValueExists(this.hkey, _key, _valueName)) {
         String value = Advapi32Util.registryGetStringValue(this.hkey, _key, _valueName);
         return value;
      } else {
         return null;
      }
   }

   public boolean writeKeyValue(String _key, String _valueName, String _valueData) {
      boolean rtn = this.createKey(_key);
      Advapi32Util.registrySetStringValue(this.hkey, _key, _valueName, _valueData);
      return rtn;
   }

   public boolean writeKeyValue(String _key, String _valueName, int _valueData) {
      boolean rtn = this.createKey(_key);
      Advapi32Util.registrySetIntValue(this.hkey, _key, _valueName, _valueData);
      return rtn;
   }

   public boolean deleteKeyValue(String _key, String _valueName) {
      if (Advapi32Util.registryValueExists(this.hkey, _key, _valueName)) {
         Advapi32Util.registryDeleteValue(this.hkey, _key, _valueName);
         return true;
      } else {
         return false;
      }
   }

   public boolean deleteKey(String _key) {
      if (Advapi32Util.registryKeyExists(this.hkey, _key)) {
         Advapi32Util.registryDeleteKey(this.hkey, _key);
         return true;
      } else {
         return false;
      }
   }

   public boolean createKey(String _key) {
      if (!Advapi32Util.registryKeyExists(this.hkey, _key)) {
         Advapi32Util.registryCreateKey(this.hkey, _key);
         return true;
      } else {
         return false;
      }
   }
}
