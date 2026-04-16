package edu.carleton.cas.jetty.embedded;

public interface ProgressIndicator {
   int getProgress();

   String getProgressMessage();

   void setProgress(int var1);

   void setProgressMessage(String var1);
}
