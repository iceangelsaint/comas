package edu.carleton.cas.background;

public interface ArchiverInterface extends KeepAliveInterface {
   boolean doWork(Object var1);

   void put(Object var1);

   int backlog();

   void doWorkOnFailure(Object var1);
}
