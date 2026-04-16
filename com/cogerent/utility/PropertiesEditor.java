package com.cogerent.utility;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.Pattern;

public class PropertiesEditor extends Properties {
   private static final long serialVersionUID = -2248185898724523132L;
   private static final Pattern comment = Pattern.compile("#.*");
   private static final Pattern empty = Pattern.compile("^$");
   private static final Pattern prop = Pattern.compile("[^=]+[=].*");
   private static final Pattern extendedProp = Pattern.compile("\\+[^=]+[=].*");
   private static final Pattern delimiter = Pattern.compile("[=]");
   private static final Pattern multiValueKey = Pattern.compile(".*\\.[\\d]{1,}");

   public String getProperty(String key, String defaultValue) {
      String value = this.getProperty(key);
      return value != null ? value : defaultValue;
   }

   public String getProperty(String key) {
      String value;
      try {
         value = (String)super.get(key);
      } catch (ClassCastException var4) {
         value = String.join(",", this.getPropertyValue(key));
      }

      return value;
   }

   public String[] getPropertyValue(String key) {
      Object value = this.get(key);
      if (value instanceof ArrayList) {
         ArrayList<String> values = (ArrayList)value;
         return (String[])values.toArray(new String[values.size()]);
      } else {
         return value != null ? new String[]{value.toString()} : null;
      }
   }

   public String[] getPropertyValue(String key, String[] defaultValue) {
      String[] value = this.getPropertyValue(key);
      return value == null ? defaultValue : value;
   }

   public void setPropertyValue(String key, String value) {
      this.put(key, Arrays.asList(value));
   }

   public void setPropertyValue(String key, String[] value) {
      this.put(key, Arrays.asList(value));
   }

   public void loadAsLegacy(InputStream is) throws IOException {
      super.load(is);
   }

   public void loadAsLegacy(Reader rdr) throws IOException {
      super.load(rdr);
   }

   public void load(String fileName) throws IOException {
      this.load(new File(fileName));
   }

   public void load(File file) throws IOException {
      FileInputStream fis = new FileInputStream(file);

      try {
         this.load((InputStream)fis);
      } finally {
         if (fis != null) {
            fis.close();
         }

      }

   }

   public void load(InputStream inStream) throws IOException {
      this.load((Reader)(new InputStreamReader(inStream)));
   }

   public void load(Reader reader) throws IOException {
      BufferedReader in = new BufferedReader(reader);

      for(String line = in.readLine(); line != null; line = in.readLine()) {
         StringBuffer buf = new StringBuffer(line);
         if (!comment.matcher(buf).matches() && !empty.matcher(buf).matches()) {
            if (extendedProp.matcher(buf).matches()) {
               String[] tokens = delimiter.split(buf.substring(1));
               if (tokens.length > 0) {
                  String key = tokens[0].trim();
                  String value = tokens[1].trim();
                  Object existingValue = this.get(key);
                  if (existingValue != null) {
                     if (existingValue instanceof ArrayList) {
                        ArrayList<String> values = (ArrayList)existingValue;
                        values.add(value);
                     } else {
                        ArrayList<String> values = new ArrayList();
                        values.add(existingValue.toString());
                        values.add(value);
                        this.put(key, values);
                     }
                  } else {
                     this.put(key, value);
                  }
               }
            } else if (prop.matcher(buf).matches()) {
               String[] tokens = delimiter.split(buf);
               if (tokens.length > 0) {
                  String key = tokens[0].trim();
                  String value = tokens[1].trim();
                  this.put(key, value);
               }
            }
         }
      }

   }

   public void store(Writer wr, String comments) {
      PrintWriter pw = new PrintWriter(wr);
      pw.print("# ");
      pw.println(comments);
      pw.print("# Saved: ");
      pw.println((new Date()).toString());
      this.list(pw);
   }

   public void store(OutputStream os, String comments) {
      PrintStream ps = new PrintStream(os);
      ps.print("# ");
      ps.println(comments);
      ps.print("# Saved: ");
      ps.println((new Date()).toString());
      this.list(ps);
   }

   public void storeToXML(OutputStream os, String comments) {
      throw new ClassCastException("Unsupported for multi-value");
   }

   public void storeToXML(Writer wr, String comments) {
      throw new ClassCastException("Unsupported for multi-value");
   }

   public void list(PrintStream pw) {
      Enumeration<String> keys = this.propertyNames();

      while(keys.hasMoreElements()) {
         String key = (String)keys.nextElement();
         Object value = this.get(key);
         if (value instanceof ArrayList) {
            ArrayList<String> valuesForKey = (ArrayList)value;
            boolean first = true;

            for(String v4k : valuesForKey) {
               if (!first) {
                  pw.print("+");
               } else {
                  first = false;
               }

               pw.print(key);
               pw.print("=");
               pw.println(v4k);
            }
         } else {
            pw.print(key);
            pw.print("=");
            pw.println(value);
         }
      }

   }

   public void list(PrintWriter pw) {
      Enumeration<String> keys = this.propertyNames();

      while(keys.hasMoreElements()) {
         String key = (String)keys.nextElement();
         Object value = this.get(key);
         if (value instanceof ArrayList) {
            ArrayList<String> valuesForKey = (ArrayList)value;
            boolean first = true;

            for(String v4k : valuesForKey) {
               if (!first) {
                  pw.print("+");
               } else {
                  first = false;
               }

               pw.print(key);
               pw.print("=");
               pw.println(v4k);
            }
         } else {
            pw.print(key);
            pw.print("=");
            pw.println(value);
         }
      }

   }

   public void storeAsLegacy(OutputStream os, String comments) {
      PrintStream ps = new PrintStream(os);
      ps.print("# ");
      ps.println(comments);
      ps.print("# Saved: ");
      ps.println((new Date()).toString());
      this.listAsLegacy(ps);
   }

   public void listAsLegacy(PrintStream pw) {
      Enumeration<String> keys = this.propertyNames();

      while(keys.hasMoreElements()) {
         String key = (String)keys.nextElement();
         Object value = this.get(key);
         if (value instanceof ArrayList) {
            ArrayList<String> valuesForKey = (ArrayList)value;
            int i = 1;

            for(String v4k : valuesForKey) {
               pw.print(key);
               pw.print(".");
               pw.print(i);
               pw.print("=");
               pw.println(v4k);
               ++i;
            }
         } else {
            pw.print(key);
            pw.print("=");
            pw.println(value);
         }
      }

   }

   public void storeAsLegacy(Writer wr, String comments) {
      PrintWriter pw = new PrintWriter(wr);
      pw.print("# ");
      pw.println(comments);
      pw.print("# Saved: ");
      pw.println((new Date()).toString());
      this.listAsLegacy(pw);
   }

   public void listAsLegacy(PrintWriter pw) {
      Enumeration<String> keys = this.propertyNames();

      while(keys.hasMoreElements()) {
         String key = (String)keys.nextElement();
         Object value = this.get(key);
         if (value instanceof ArrayList) {
            ArrayList<String> valuesForKey = (ArrayList)value;
            int i = 1;

            for(String v4k : valuesForKey) {
               pw.print(key);
               pw.print(".");
               pw.print(i);
               pw.print("=");
               pw.println(v4k);
               ++i;
            }
         } else {
            pw.print(key);
            pw.print("=");
            pw.println(value);
         }
      }

   }

   public void convert() {
      for(String key : this.stringPropertyNames()) {
         String value = this.getProperty(key);
         if (multiValueKey.matcher(key).matches()) {
            String baseKey = removeExtension(key);
            Object existingValue = this.get(baseKey);
            if (existingValue != null) {
               if (existingValue instanceof ArrayList) {
                  ArrayList<String> values = (ArrayList)existingValue;
                  values.add(value);
               } else {
                  ArrayList<String> values = new ArrayList();
                  values.add(existingValue.toString());
                  values.add(value);
                  this.put(baseKey, values);
               }
            } else {
               this.put(baseKey, value);
            }

            this.remove(key);
         }
      }

   }

   public static String removeExtensionFull(File f) {
      return removeExtension(f == null ? null : f.getAbsolutePath());
   }

   public static String removeExtension(File f) {
      return removeExtension(f == null ? null : f.getName());
   }

   public static String removeExtension(String s) {
      return s != null && s.lastIndexOf(".") > 0 ? s.substring(0, s.lastIndexOf(".")) : s;
   }

   public static void main(String[] args) {
      PropertiesEditor pe = new PropertiesEditor();
      PropertiesEditor pe2 = new PropertiesEditor();
      PropertiesEditor pe3 = new PropertiesEditor();

      try {
         System.out.println("============LOAD==========");
         pe.load("/Users/tonywhite/Desktop/test.txt");
         pe.list(System.out);
         System.out.println("=======VALUE a,a,z,x======");
         System.out.println(Arrays.toString(pe.getPropertyValue("a")));
         System.out.println(pe.getProperty("a"));
         System.out.println(pe.getProperty("z"));
         System.out.println(pe.getPropertyValue("x"));
         System.out.println("=======LIST LEGACY========");
         pe.listAsLegacy(System.out);
         pe.store((OutputStream)(new FileOutputStream("/Users/tonywhite/Desktop/test1.txt")), "Test of PropertyEditor");
         pe.storeAsLegacy((OutputStream)(new FileOutputStream("/Users/tonywhite/Desktop/test2.txt")), "Legacy Test of PropertyEditor");
         System.out.println("============LOAD=CLIENT.INI==");
         pe.load("/Users/tonywhite/comas/cms/exam/client.ini");
         pe.list(System.out);
         System.out.println("============CONVERT==========");
         pe2.loadAsLegacy((InputStream)(new FileInputStream("/Users/tonywhite/Desktop/test2.txt")));
         pe2.convert();
         pe2.list(System.out);
         System.out.println("============CONVERT=2========");
         pe3.load("/Users/tonywhite/comas/cms/exam/client.ini");
         pe3.convert();
         pe3.list(System.out);
      } catch (IOException e) {
         e.printStackTrace();
      }

   }
}
