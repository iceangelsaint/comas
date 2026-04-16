package edu.carleton.cas.security;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import javax.crypto.SecretKey;
import org.encryptor4j.Encryptor;
import org.encryptor4j.factory.KeyFactory;

public class StreamCryptoUtils {
   public static SecretKey streamEncrypt(String password, String unencryptedFileName, String encryptedFileName) throws GeneralSecurityException, IOException {
      SecretKey secretKey = (SecretKey)KeyFactory.AES.keyFromPassword(password.toCharArray());
      Encryptor encryptor = new Encryptor(secretKey, "AES/CTR/NoPadding", 16);
      InputStream is = null;
      OutputStream os = null;

      try {
         is = new FileInputStream(unencryptedFileName);
         os = encryptor.wrapOutputStream(new FileOutputStream(encryptedFileName));
         byte[] buffer = new byte[4096];

         int nRead;
         while((nRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, nRead);
         }

         os.flush();
      } finally {
         if (is != null) {
            is.close();
         }

         if (os != null) {
            os.close();
         }

      }

      return secretKey;
   }

   public static void streamDecrypt(String password, String encryptedFileName, String decryptedFileName) throws FileNotFoundException, GeneralSecurityException, IOException {
      SecretKey secretKey = (SecretKey)KeyFactory.AES.keyFromPassword(password.toCharArray());
      streamDecrypt(secretKey, encryptedFileName, decryptedFileName);
   }

   public static void streamDecrypt(SecretKey secretKey, String encryptedFileName, String decryptedFileName) throws FileNotFoundException, GeneralSecurityException, IOException {
      Encryptor encryptor = new Encryptor(secretKey, "AES/CTR/NoPadding", 16);
      InputStream is = null;
      OutputStream os = null;

      try {
         is = encryptor.wrapInputStream(new FileInputStream(encryptedFileName));
         os = new FileOutputStream(decryptedFileName);
         byte[] buffer = new byte[4096];

         int nRead;
         while((nRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, nRead);
         }

         os.flush();
      } finally {
         if (is != null) {
            is.close();
         }

         if (os != null) {
            os.close();
         }

      }

   }

   public static void main(String[] args) {
      if (args.length < 2) {
         System.err.println("Usage:");
         System.err.println("java -jar CoMaS-decrypt.jar password archive");
         System.exit(0);
      }

      String password = args[0];
      if (password.length() != 16) {
         System.err.println("Password must be 16 characters long");
         System.exit(0);
      }

      String archive = args[1];
      if (!archive.endsWith(".zip")) {
         System.err.println("Archive must be an encrypted .zip file");
         System.exit(0);
      }

      try {
         streamDecrypt(password, archive, "unencrypted-" + archive);
         System.out.println("The unencrypted archive is unencrypted-" + archive);
      } catch (IOException | GeneralSecurityException e) {
         System.err.println("Archive decryption failed: " + String.valueOf(e));
      }

   }
}
