package edu.carleton.cas.utility;

public abstract class Named {
   public static String canonical(String str) {
      if (str.length() == 0) {
         return str;
      } else {
         String canonicalStr;
         if (str.charAt(0) == '.') {
            canonicalStr = "_" + str.substring(1);
         } else {
            canonicalStr = str;
         }

         return canonicalStr.replace(' ', '-');
      }
   }

   public static String quoted(String str) {
      return str.charAt(0) != '"' ? String.format("\"%s\"", str) : str;
   }

   public static String unquoted(String str) {
      return str.charAt(0) == '"' ? str.substring(1, str.length() - 1) : str;
   }

   public static boolean isLegalVariableName(String name) {
      return name.matches("[A-Za-z_][\\dA-Za-z_]{0,31}");
   }
}
