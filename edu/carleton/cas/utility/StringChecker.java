package edu.carleton.cas.utility;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class StringChecker {
   public static String ALLOWED_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";
   public static int MAX_LENGTH = 32;

   public static boolean check(String thingToCheck) {
      return check(thingToCheck, MAX_LENGTH);
   }

   public static boolean check(String thingToCheck, int allowedLength) {
      return check(thingToCheck, ALLOWED_CHARACTERS, allowedLength);
   }

   public static boolean check(String thingToCheck, String allowedCharacters) {
      int i = 0;

      for(int n = thingToCheck.length(); i < n; ++i) {
         char c = thingToCheck.charAt(i);
         if (allowedCharacters.indexOf(c) < 0) {
            return false;
         }
      }

      return true;
   }

   public static boolean check(String thingToCheck, String allowedCharacters, int allowedLength) {
      if (thingToCheck.length() > allowedLength) {
         return false;
      } else {
         int i = 0;

         for(int n = thingToCheck.length(); i < n; ++i) {
            char c = thingToCheck.charAt(i);
            if (allowedCharacters.indexOf(c) < 0) {
               return false;
            }
         }

         return true;
      }
   }

   public static boolean check(String thingToCheck, Pattern allowedPattern) {
      Matcher m = allowedPattern.matcher(thingToCheck);
      return m.matches();
   }
}
