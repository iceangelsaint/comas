package edu.carleton.cas.utility;

import edu.carleton.cas.background.timers.TimerService;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.TimerTask;

public abstract class ClipboardManager {
   public static TimerTask block;
   public static long frequencyInMsecs = 500L;
   public static String message = "Blocked by CoMaS";

   public static void main(String[] args) throws UnsupportedFlavorException, IOException {
      if (args.length > 0) {
         setContents(args[0]);
      }

      System.out.println("===CLIPBOARD===\n" + getContents() + "\n===END===");
      System.exit(0);
   }

   public static void setBlockMessage(String _message) {
      message = _message;
   }

   public static void setBlockFrequency(long _frequencyInMsecs) {
      frequencyInMsecs = _frequencyInMsecs;
   }

   public static void setContents(String contents) {
      Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
      if (contents != null) {
         StringSelection data = new StringSelection(contents);
         c.setContents(data, data);
      }

   }

   public static String getContents() throws UnsupportedFlavorException, IOException {
      Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
      Transferable t = c.getContents((Object)null);
      if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
         String data = (String)t.getTransferData(DataFlavor.stringFlavor);
         return data;
      } else {
         return "<empty>";
      }
   }

   public static void block() {
      block(frequencyInMsecs);
   }

   public static synchronized void block(long timeInMsecs) {
      if (block == null && timeInMsecs > 0L) {
         final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
         final ClipboardOwner owner = (c, t) -> {
         };
         final StringSelection data = new StringSelection(message);
         block = new TimerTask() {
            public void run() {
               try {
                  clipboard.setContents(data, owner);
               } catch (Exception var2) {
               }

            }
         };
         TimerService.getInstance().scheduleAtFixedRate(block, timeInMsecs, timeInMsecs);
      }
   }

   public static synchronized void unblock() {
      if (block != null) {
         block.cancel();
         block = null;
      }

   }
}
