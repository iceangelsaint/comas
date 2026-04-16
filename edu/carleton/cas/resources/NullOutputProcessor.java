package edu.carleton.cas.resources;

public class NullOutputProcessor implements ExceptionProcessor {
   private final OutputProcessorObserver observer;

   public NullOutputProcessor() {
      this((OutputProcessorObserver)null);
   }

   public NullOutputProcessor(OutputProcessorObserver observer) {
      this.observer = observer;
   }

   public void process(String line) {
   }

   public String result() {
      return "";
   }

   public void asyncResult() {
      if (this.observer != null) {
         this.observer.resultAvailable(this);
      }

   }

   public void onException(Exception e) {
      if (this.observer != null) {
         this.observer.exceptionOccurred(e);
      }

   }
}
