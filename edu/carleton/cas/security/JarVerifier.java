package edu.carleton.cas.security;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class JarVerifier {
   File f;
   Certificate[] certs;
   boolean print;

   public JarVerifier(File f) {
      this(f, false);
   }

   public JarVerifier(File f, boolean print) {
      this.f = f;
      this.print = print;
   }

   public Certificate[] getCertificates() {
      return this.certs;
   }

   public boolean checkJarEntries() {
      return this.checkJarEntries(this.f);
   }

   public boolean checkJarEntries(File f) {
      boolean isSigned = true;
      ArrayList<JarEntry> entriesVec = new ArrayList();
      byte[] buffer = new byte[8192];
      JarFile jarFile = null;

      try {
         jarFile = new JarFile(f, true);
         Manifest man = jarFile.getManifest();
         if (man != null) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while(entries.hasMoreElements()) {
               JarEntry je = (JarEntry)entries.nextElement();
               if (!je.isDirectory()) {
                  entriesVec.add(je);
                  InputStream is = jarFile.getInputStream(je);

                  while(is.read(buffer, 0, buffer.length) != -1) {
                  }

                  is.close();
               }
            }

            entries = jarFile.entries();

            while(entries.hasMoreElements()) {
               JarEntry je = (JarEntry)entries.nextElement();
               if (!je.isDirectory() && !je.getName().startsWith("META-INF")) {
                  Certificate[] checkCerts = je.getCertificates();
                  if (checkCerts != null) {
                     this.certs = checkCerts;
                     if (this.print) {
                        System.out.println(je.getName() + " is signed");
                     }
                  } else {
                     isSigned = false;
                     if (this.print) {
                        System.out.println(je.getName() + " is not signed");
                     }
                  }
               }
            }

            return isSigned;
         }

         if (this.print) {
            System.err.println(String.valueOf(f) + " does not have a signature");
         }
      } catch (IOException e) {
         if (this.print) {
            System.out.println("JarFile: " + String.valueOf(e));
         }

         return isSigned;
      } finally {
         try {
            if (jarFile != null) {
               jarFile.close();
            }
         } catch (IOException var19) {
         }

      }

      return false;
   }
}
