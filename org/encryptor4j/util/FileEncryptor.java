package org.encryptor4j.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import org.encryptor4j.Encryptor;
import org.encryptor4j.factory.EncryptorFactory;
import org.encryptor4j.factory.KeyFactory;

public class FileEncryptor {
   private Encryptor encryptor;
   private int bufferSize;

   public FileEncryptor() {
      this(KeyFactory.AES.randomKey());
   }

   public FileEncryptor(String password) {
      this(KeyFactory.AES.keyFromPassword(password.toCharArray()));
   }

   public FileEncryptor(Key key) {
      this(EncryptorFactory.AES.streamEncryptor(key));
   }

   public FileEncryptor(Encryptor encryptor) {
      this.encryptor = encryptor;
      this.bufferSize = 65536;
   }

   public void encrypt(File src, File dest) throws GeneralSecurityException, IOException {
      InputStream is = null;
      OutputStream os = null;

      try {
         is = new FileInputStream(src);
         os = this.encryptor.wrapOutputStream(new FileOutputStream(dest));
         this.copy(is, os);
      } finally {
         if (is != null) {
            is.close();
         }

         if (os != null) {
            os.close();
         }

      }

   }

   public void decrypt(File src, File dest) throws GeneralSecurityException, IOException {
      InputStream is = null;
      OutputStream os = null;

      try {
         is = this.encryptor.wrapInputStream(new FileInputStream(src));
         os = new FileOutputStream(dest);
         this.copy(is, os);
      } finally {
         if (is != null) {
            is.close();
         }

         if (os != null) {
            os.close();
         }

      }

   }

   private void copy(InputStream is, OutputStream os) throws IOException {
      byte[] buffer = new byte[this.bufferSize];

      int nRead;
      while((nRead = is.read(buffer)) != -1) {
         os.write(buffer, 0, nRead);
      }

      os.flush();
   }

   public Encryptor getEncryptor() {
      return this.encryptor;
   }

   public int getBufferSize() {
      return this.bufferSize;
   }

   public void setBufferSize(int bufferSize) {
      this.bufferSize = bufferSize;
   }
}
