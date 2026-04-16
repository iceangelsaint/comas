package com.cogerent.utility;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.file.Utils;
import java.util.Properties;

public abstract class PropertyValue {
   public static String getValue(Properties properties, String before, String after, String course, String activity, String id) {
      String format = before + ".%s." + after;
      String value = properties.getProperty(String.format(format, id));
      if (value == null) {
         value = properties.getProperty(String.format(format, course + "/" + activity));
         if (value == null) {
            value = properties.getProperty(String.format(format, course));
            if (value == null) {
               value = properties.getProperty(before + "." + after);
            }
         }
      }

      return value != null ? value.trim() : value;
   }

   public static String getValue(Invigilator invigilator, String before, String after, String[] values, String defaultValue) {
      String value = getValue(invigilator.getProperties(), before, after, invigilator.getCourse(), invigilator.getActivity(), invigilator.getID());
      if (value == null) {
         return defaultValue;
      } else {
         for(String allowedValue : values) {
            if (value.equals(allowedValue)) {
               return value;
            }
         }

         return defaultValue;
      }
   }

   public static String getValue(Invigilator invigilator, String before, String after) {
      return getValue(invigilator.getProperties(), before, after, invigilator.getCourse(), invigilator.getActivity(), invigilator.getID());
   }

   public static String getValue(Invigilator invigilator, String before, String after, String defaultValue) {
      String value = getValue(invigilator.getProperties(), before, after, invigilator.getCourse(), invigilator.getActivity(), invigilator.getID());
      return value == null ? defaultValue : value;
   }

   public static boolean getValue(Invigilator invigilator, String before, String after, boolean defaultValue) {
      String value = getValue(invigilator.getProperties(), before, after, invigilator.getCourse(), invigilator.getActivity(), invigilator.getID());
      return value == null ? defaultValue : Utils.isTrueOrYes(value, defaultValue);
   }

   public static int getValue(Invigilator invigilator, String before, String after, int defaultValue) {
      String value = getValue(invigilator.getProperties(), before, after, invigilator.getCourse(), invigilator.getActivity(), invigilator.getID());
      if (value == null) {
         return defaultValue;
      } else {
         try {
            return Integer.parseInt(value);
         } catch (NumberFormatException var6) {
            return defaultValue;
         }
      }
   }

   public static long getValue(Invigilator invigilator, String before, String after, long defaultValue) {
      String value = getValue(invigilator.getProperties(), before, after, invigilator.getCourse(), invigilator.getActivity(), invigilator.getID());
      if (value == null) {
         return defaultValue;
      } else {
         try {
            return (long)Integer.parseInt(value);
         } catch (NumberFormatException var7) {
            return defaultValue;
         }
      }
   }

   public static float getValue(Invigilator invigilator, String before, String after, float defaultValue) {
      String value = getValue(invigilator.getProperties(), before, after, invigilator.getCourse(), invigilator.getActivity(), invigilator.getID());
      if (value == null) {
         return defaultValue;
      } else {
         try {
            return Float.parseFloat(value);
         } catch (NumberFormatException var6) {
            return defaultValue;
         }
      }
   }

   public static String getValue(Invigilator invigilator, Properties properties, String before, String after) {
      return getValue(properties, before, after, invigilator.getCourse(), invigilator.getActivity(), invigilator.getID());
   }

   public static String[] getValues(PropertiesEditor properties, String before, String after, String course, String activity, String id) {
      String format = before + ".%s." + after;
      String[] value = properties.getPropertyValue(String.format(format, id));
      if (value == null) {
         value = properties.getPropertyValue(String.format(format, course + "/" + activity));
         if (value == null) {
            value = properties.getPropertyValue(String.format(format, course));
            if (value == null) {
               value = properties.getPropertyValue(before + "." + after);
            }
         }
      }

      return value;
   }

   public static String[] getValues(Invigilator invigilator, PropertiesEditor properties, String before, String after) {
      return getValues(properties, before, after, invigilator.getCourse(), invigilator.getActivity(), invigilator.getID());
   }
}
