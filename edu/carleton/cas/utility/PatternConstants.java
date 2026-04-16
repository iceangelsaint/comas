package edu.carleton.cas.utility;

import java.util.regex.Pattern;

public class PatternConstants {
   public static final String delimeter = "[:,]";
   public static final String space_delimeter = " ";
   public static final String comma_delimeter = ",";
   public static final String colon_delimeter = ":";
   public static final String semicolon_delimeter = ";";
   public static final Pattern emailPattern = Pattern.compile("^([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9_\\-\\.]+)\\.([a-zA-Z]{2,5})$");
   public static final Pattern variablePattern = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{1,31}$");
}
