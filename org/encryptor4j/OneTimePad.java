package org.encryptor4j;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.SecureRandom;

public class OneTimePad {
   private SecureRandom random;
   private int ioBufferSize;
   private int workBufferSize;
   private boolean zeroFill;

   public OneTimePad() {
      this(1048576, 262144);
   }

   public OneTimePad(int ioBufferSize, int workBufferSize) {
      this(new SecureRandom(), ioBufferSize, workBufferSize);
   }

   public OneTimePad(SecureRandom random, int ioBufferSize, int workBufferSize) {
      this.random = random;
      this.ioBufferSize = ioBufferSize;
      this.workBufferSize = workBufferSize;
   }

   public void createPadFile(File padFile, long size) {
      OutputStream os = null;

      try {
         os = new BufferedOutputStream(new FileOutputStream(padFile), this.ioBufferSize);
         long totalSize = 0L;
         byte[] randomBytes = new byte[this.workBufferSize];

         while(totalSize < size) {
            this.random.nextBytes(randomBytes);
            long bytesLeft = size - totalSize;
            if (bytesLeft < (long)this.workBufferSize) {
               os.write(randomBytes, 0, (int)bytesLeft);
               totalSize += bytesLeft;
            } else {
               os.write(randomBytes);
               totalSize += (long)this.workBufferSize;
            }
         }

         os.flush();
      } catch (IOException e) {
         throw new RuntimeException(e);
      } finally {
         if (os != null) {
            try {
               os.close();
            } catch (IOException e) {
               e.printStackTrace();
            }
         }

      }

   }

   public long padData(File inFile, File outFile, File padFile, long offset) {
      RandomAccessFile raf = null;
      InputStream is = null;
      OutputStream os = null;

      try {
         raf = new RandomAccessFile(padFile, this.zeroFill ? "rw" : "r");
         raf.seek(offset);
         is = new BufferedInputStream(new FileInputStream(inFile), this.ioBufferSize);
         os = new BufferedOutputStream(new FileOutputStream(outFile), this.ioBufferSize);
         byte[] padBytes = new byte[this.workBufferSize];
         byte[] bytes = new byte[this.workBufferSize];

         while(true) {
            int nBytes = is.read(bytes);
            int nPadBytes = raf.read(padBytes);
            if (nBytes <= 0) {
               os.flush();
               long var15 = offset;
               return var15;
            }

            if (nPadBytes < nBytes) {
               throw new IOException("Not enough pad bytes");
            }

            for(int i = 0; i < nBytes; ++i) {
               bytes[i] ^= padBytes[i];
            }

            os.write(bytes, 0, nBytes);
            if (this.zeroFill) {
               raf.seek(offset);

               for(int i = 0; i < nBytes; ++i) {
                  raf.write(0);
               }
            }

            offset += (long)nBytes;
         }
      } catch (IOException e) {
         throw new RuntimeException(e);
      } finally {
         if (raf != null) {
            try {
               raf.close();
            } catch (IOException e) {
               e.printStackTrace();
            }
         }

         if (is != null) {
            try {
               is.close();
            } catch (IOException e) {
               e.printStackTrace();
            }
         }

         if (os != null) {
            try {
               os.close();
            } catch (IOException e) {
               e.printStackTrace();
            }
         }

      }
   }

   public void setZeroFill(boolean zeroFill) {
      this.zeroFill = zeroFill;
   }
}
