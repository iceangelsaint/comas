package edu.carleton.cas.resources;

public interface OutputProcessorObserver {
   void resultAvailable(OutputProcessor var1);

   void exceptionOccurred(Exception var1);
}
