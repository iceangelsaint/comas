package org.encryptor4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

public class Encryptor {
   private static final String DEFAULT_ALGORITHM = "AES";
   private String algorithm;
   private String algorithmProvider;
   private int ivLength;
   private int tLen;
   private Key key;
   private KeySpec keySpec;
   private SecretKeyFactory secretKeyFactory;
   private ThreadLocal ivThreadLocal;
   private ThreadLocal cipherThreadLocal;
   private boolean prependIV;
   private boolean generateIV;

   public Encryptor(Key key) {
      this(key, "AES");
   }

   public Encryptor(Key key, String algorithm) {
      this(key, algorithm, 0);
   }

   public Encryptor(Key key, String algorithm, int ivLength) {
      this(key, algorithm, ivLength, 0);
   }

   public Encryptor(Key key, String algorithm, int ivLength, int tLen) {
      this.key = key;
      this.algorithm = algorithm;
      this.ivLength = ivLength;
      this.tLen = tLen;
      this.ivThreadLocal = new ThreadLocal();
      this.cipherThreadLocal = new ThreadLocal();
      this.prependIV = this.generateIV = true;
   }

   public Encryptor(KeySpec keySpec, SecretKeyFactory secretKeyFactory) {
      this(keySpec, secretKeyFactory, "AES", 0);
   }

   public Encryptor(KeySpec keySpec, SecretKeyFactory secretKeyFactory, String algorithm, int ivLength) {
      this(keySpec, secretKeyFactory, "AES", ivLength, 0);
   }

   public Encryptor(KeySpec keySpec, SecretKeyFactory secretKeyFactory, String algorithm, int ivLength, int tLen) {
      this.keySpec = keySpec;
      this.secretKeyFactory = secretKeyFactory;
      this.algorithm = algorithm;
      this.ivLength = ivLength;
      this.tLen = tLen;
      this.ivThreadLocal = new ThreadLocal();
      this.cipherThreadLocal = new ThreadLocal();
      this.prependIV = this.generateIV = true;
   }

   public byte[] encrypt(byte[] message) throws GeneralSecurityException {
      return this.encrypt(message, (byte[])null);
   }

   public byte[] encrypt(byte[] message, byte[] aad) throws GeneralSecurityException {
      return this.encrypt(message, aad, (byte[])null);
   }

   public byte[] encrypt(byte[] message, byte[] aad, byte[] iv) throws GeneralSecurityException {
      Cipher cipher = this.getCipher(true);
      if (iv == null && this.generateIV && this.ivLength > 0) {
         iv = this.generateIV();
      }

      if (iv != null) {
         cipher.init(1, this.getKey(), this.getAlgorithmParameterSpec(iv));
      } else {
         cipher.init(1, this.getKey());
         iv = cipher.getIV();
      }

      this.ivThreadLocal.set(iv);
      if (aad != null) {
         cipher.updateAAD(aad);
      }

      byte[] encrypted;
      if (this.prependIV && iv != null) {
         int outputSize = cipher.getOutputSize(message.length);
         encrypted = new byte[iv.length + outputSize];
         System.arraycopy(iv, 0, encrypted, 0, iv.length);

         try {
            int nBytes = cipher.doFinal(message, 0, message.length, encrypted, iv.length);
            if (nBytes < outputSize) {
               int excessBytes = outputSize - nBytes;
               byte[] resized = new byte[encrypted.length - excessBytes];
               System.arraycopy(encrypted, 0, resized, 0, resized.length);
               encrypted = resized;
            }
         } catch (ShortBufferException e) {
            throw new RuntimeException(e);
         }
      } else {
         encrypted = cipher.doFinal(message);
      }

      return encrypted;
   }

   public byte[] decrypt(byte[] message) throws GeneralSecurityException {
      return this.decrypt(message, (byte[])null);
   }

   public byte[] decrypt(byte[] message, byte[] aad) throws GeneralSecurityException {
      return this.decrypt(message, aad, (byte[])null);
   }

   public byte[] decrypt(byte[] message, byte[] aad, byte[] iv) throws GeneralSecurityException {
      Cipher cipher = this.getCipher(true);
      if (this.ivLength > 0) {
         if (this.prependIV) {
            cipher.init(2, this.getKey(), this.getAlgorithmParameterSpec(message));
            if (aad != null) {
               cipher.updateAAD(aad);
            }

            return cipher.doFinal(message, this.ivLength, message.length - this.ivLength);
         } else {
            throw new IllegalStateException("Could not obtain IV");
         }
      } else {
         if (iv != null) {
            cipher.init(2, this.getKey(), this.getAlgorithmParameterSpec(iv));
         } else {
            cipher.init(2, this.getKey());
         }

         if (aad != null) {
            cipher.updateAAD(aad);
         }

         return cipher.doFinal(message);
      }
   }

   public byte[] getIV() {
      return (byte[])this.ivThreadLocal.get();
   }

   public void setPrependIV(boolean prependIV) {
      this.prependIV = prependIV;
   }

   public void setGenerateIV(boolean generateIV) {
      this.generateIV = generateIV;
   }

   public String getAlgorithm() {
      return this.algorithm;
   }

   public void setAlgorithmProvider(String algorithmProvider) {
      this.algorithmProvider = algorithmProvider;
   }

   public Key getKey() {
      if (this.key != null) {
         return this.key;
      } else if (this.keySpec != null && this.secretKeyFactory != null) {
         try {
            return this.key = this.secretKeyFactory.generateSecret(this.keySpec);
         } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
         }
      } else {
         throw new IllegalStateException("Cannot produce key");
      }
   }

   public CipherInputStream wrapInputStream(InputStream is) throws GeneralSecurityException, IOException {
      return this.wrapInputStream(is, (byte[])null);
   }

   public CipherInputStream wrapInputStream(InputStream is, byte[] iv) throws GeneralSecurityException, IOException {
      Cipher cipher = this.getCipher(true);
      if (iv == null && this.ivLength > 0) {
         if (!this.prependIV) {
            throw new IllegalStateException("Could not obtain IV");
         }

         iv = new byte[this.ivLength];
         is.read(iv);
      }

      if (iv != null) {
         cipher.init(2, this.getKey(), this.getAlgorithmParameterSpec(iv));
      } else {
         cipher.init(2, this.getKey());
      }

      return new CipherInputStream(is, cipher);
   }

   public CipherOutputStream wrapOutputStream(OutputStream os) throws GeneralSecurityException, IOException {
      return this.wrapOutputStream(os, (byte[])null);
   }

   public CipherOutputStream wrapOutputStream(OutputStream os, byte[] iv) throws GeneralSecurityException, IOException {
      Cipher cipher = this.getCipher(true);
      if (iv == null && this.generateIV && this.ivLength > 0) {
         iv = this.generateIV();
      }

      if (iv != null) {
         cipher.init(1, this.getKey(), this.getAlgorithmParameterSpec(iv));
      } else {
         cipher.init(1, this.getKey());
         iv = cipher.getIV();
      }

      this.ivThreadLocal.set(iv);
      if (this.prependIV && iv != null) {
         os.write(iv);
      }

      return new CipherOutputStream(os, cipher);
   }

   public Cipher getCipher() throws GeneralSecurityException {
      return this.getCipher(false);
   }

   private Cipher getCipher(boolean create) throws GeneralSecurityException {
      Cipher cipher = (Cipher)this.cipherThreadLocal.get();
      if (cipher == null || create) {
         cipher = this.createCipher();
         this.cipherThreadLocal.set(cipher);
      }

      return cipher;
   }

   private Cipher createCipher() throws GeneralSecurityException {
      return this.algorithmProvider != null ? Cipher.getInstance(this.algorithm, this.algorithmProvider) : Cipher.getInstance(this.algorithm);
   }

   private AlgorithmParameterSpec getAlgorithmParameterSpec(byte[] ivBuffer) {
      int length = this.ivLength == 0 && ivBuffer != null ? ivBuffer.length : this.ivLength;
      String[] parts = this.algorithm.split("/");
      return (AlgorithmParameterSpec)(parts.length > 1 && parts[1].equalsIgnoreCase("GCM") ? new GCMParameterSpec(this.tLen > 0 ? this.tLen : 128, ivBuffer, 0, length) : new IvParameterSpec(ivBuffer, 0, length));
   }

   private byte[] generateIV() {
      byte[] iv = new byte[this.ivLength];
      SecureRandom random = new SecureRandom();
      random.nextBytes(iv);
      return iv;
   }
}
