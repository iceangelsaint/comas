package edu.carleton.cas.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
   private static final String ALGORITHM = "AES";
   private static final String TRANSFORMATION = "AES";

   public static void encrypt(String key, File inputFile, File outputFile) throws CryptoException {
      doCrypto(1, key, (File)inputFile, (File)outputFile);
   }

   public static void decrypt(String key, File inputFile, File outputFile) throws CryptoException {
      doCrypto(2, key, (File)inputFile, (File)outputFile);
   }

   public static void encrypt(String key, InputStream inputFile, OutputStream outputFile) throws CryptoException {
      doCrypto(1, key, (InputStream)inputFile, (OutputStream)outputFile);
   }

   public static void decrypt(String key, InputStream inputFile, OutputStream outputFile) throws CryptoException {
      doCrypto(2, key, (InputStream)inputFile, (OutputStream)outputFile);
   }

   private static void doCrypto(int cipherMode, String key, File inputFile, File outputFile) throws CryptoException {
      try {
         doCrypto(cipherMode, key, (InputStream)(new FileInputStream(inputFile)), (OutputStream)(new FileOutputStream(outputFile)));
      } catch (CryptoException | FileNotFoundException ex) {
         throw new CryptoException("Error encrypting/decrypting file", ex);
      }
   }

   public static void doCrypto(int cipherMode, String key, InputStream inputStream, OutputStream outputStream) throws CryptoException {
      try {
         Key secretKey = new SecretKeySpec(key.getBytes(), "AES");
         Cipher cipher = Cipher.getInstance("AES");
         cipher.init(cipherMode, secretKey);
         byte[] inputBytes = new byte[inputStream.available()];
         inputStream.read(inputBytes);
         byte[] outputBytes = cipher.doFinal(inputBytes);
         outputStream.write(outputBytes);
         inputStream.close();
         outputStream.close();
      } catch (NoSuchAlgorithmException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException | IOException | NoSuchPaddingException ex) {
         throw new CryptoException("Error encrypting/decrypting file", ex);
      }
   }
}
