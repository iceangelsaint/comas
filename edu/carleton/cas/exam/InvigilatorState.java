package edu.carleton.cas.exam;

public enum InvigilatorState {
   unknown,
   loggingIn,
   choosing,
   authorizing,
   verifying,
   initializing,
   running,
   ending,
   ended;

   public static InvigilatorState parse(String value) {
      if (value == null) {
         return unknown;
      } else {
         value = value.trim();
         if (value.equalsIgnoreCase("loggingIn")) {
            return loggingIn;
         } else if (value.equalsIgnoreCase("choosing")) {
            return choosing;
         } else if (value.equalsIgnoreCase("authorizing")) {
            return authorizing;
         } else if (value.equalsIgnoreCase("verifying")) {
            return authorizing;
         } else if (value.equalsIgnoreCase("initializing")) {
            return initializing;
         } else if (value.equalsIgnoreCase("running")) {
            return running;
         } else if (value.equalsIgnoreCase("ending")) {
            return ending;
         } else {
            return value.equalsIgnoreCase("ended") ? ended : unknown;
         }
      }
   }

   public static boolean isUnknown(InvigilatorState state) {
      return state == unknown;
   }

   public static boolean isChoosing(InvigilatorState state) {
      return state == choosing;
   }

   public static boolean isAuthorizing(InvigilatorState state) {
      return state == authorizing;
   }

   public static boolean isVerifying(InvigilatorState state) {
      return state == verifying;
   }

   public static boolean isInitializing(InvigilatorState state) {
      return state == initializing;
   }

   public static boolean isRunning(InvigilatorState state) {
      return state == running;
   }

   public static boolean isEnding(InvigilatorState state) {
      return state == ending;
   }

   public static boolean isEnded(InvigilatorState state) {
      return state == ended;
   }

   public static boolean isLoggingIn(InvigilatorState state) {
      return state == loggingIn;
   }

   public static boolean isOneOf(InvigilatorState state, InvigilatorState... states) {
      for(InvigilatorState _state : states) {
         if (_state == state) {
            return true;
         }
      }

      return false;
   }
}
